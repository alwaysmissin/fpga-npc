package core
import chisel3._
import chisel3.util.Irrevocable
import utils.RVConfig
import utils.bus.InterStage.IdExeBus
import utils.id.ControlSignals.OpASrc
import utils.id.ControlSignals.OpBSrc
import chisel3.util.Mux1H
import utils.exe.ALU
import utils.exe.LSU
import utils.bus.AXI4
import utils.exe.MemReqSignalsFromCPU
import utils.bus.InterStage.ExeMemSignalsBundle
import utils.bus.InterStage.ExeMemBus
import utils.bypass.BypassFrom
import chisel3.util.Cat
import utils.exe.CSRU
import utils.bus.{AW, W, AR}
import chisel3.util.RegEnable
import chisel3.util.PriorityMux
import chisel3.util.Fill
import chisel3.util.circt.dpi.DPIClockedVoidFunctionImport
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import chisel3.util.switch
import chisel3.util.is
import utils.ExceptionCodes
import utils.id.ControlSignals.FuType
import utils.bus.AXIBURST
import utils.id.ControlSignals.ALUOp
import utils.bus.InterStage.JumpBus
import utils.exe.BRU
import utils.id.ControlSignals.BRUOp
import utils.bus.SRAMLike.SRAMLikeReq
import chisel3.util.log2Up
import utils.exe.Multiplier
import utils.id.ControlSignals.MULOp
import utils.id.ControlSignals.DIVOp
import utils.exe.Divider
import utils.nutshellUtils.ZeroExt
import utils.csr._
import utils.cache.BTBConfig
import utils.bpu.BTBUpdateInfo
import utils.BranchPredictionSelect

class EXE(config: RVConfig, btbConfig: BTBConfig) extends Module {
  val io = IO(new Bundle {
    val fromID = Flipped(Irrevocable(new IdExeBus(config, btbConfig)))
    val toMEM = Irrevocable(new ExeMemBus(config))
    val bypass = Flipped(new BypassFrom(config))
    val regWdata = Output(UInt(config.xlen.W))
    val jumpBus = Irrevocable(new JumpBus(config))
    val btbUpdateInfo = if (config.branchPrediction == BranchPredictionSelect.Dynamic) Irrevocable(new BTBUpdateInfo(config, btbConfig)) else null
    val csrWritePort = Flipped(new CSRWritePort(config))
    val flush = Output(Bool())
    val interCMD = Irrevocable(new InterruptCMD(config))
    val flushFromMEM = Input(Bool())
  })
  val decodeBundle = io.fromID.bits.decodeBundle
  val interrupt = io.interCMD.ready

  // 确保当前指令只被发送一次
  val hasFired = RegEnable(false.B, false.B, io.toMEM.fire && io.fromID.fire)
  when(io.toMEM.fire && !io.fromID.fire) {
    hasFired := true.B
  }
  io.toMEM.bits.nop := io.fromID.bits.nop || hasFired || interrupt || io.flushFromMEM

  // ------------- ALU -------------
  val opASrc = decodeBundle.aluSrc1
  val opBSrc = decodeBundle.aluSrc2
  val opA = Mux1H(
    Seq(
      (opASrc === OpASrc.RS1) -> io.fromID.bits.rs1Data,
      (opASrc === OpASrc.PC) -> io.fromID.bits.pc,
      (opASrc === OpASrc.RS1ADDR) -> ZeroExt(io.fromID.bits.rs1, config.xlen),
      (opASrc === OpASrc.ZERO) -> 0.U
    )
  )
  val opB = Mux1H(
    Seq(
      (opBSrc === OpBSrc.RS2) -> io.fromID.bits.rs2Data,
      (opBSrc === OpBSrc.CSR) -> io.fromID.bits.csrData,
      (opBSrc === OpBSrc.IMM) -> io.fromID.bits.imm,
      (opBSrc === OpBSrc.ZERO) -> 0.U
    )
  )
  val alu = Module(new ALU(config))
  alu.io.op <> decodeBundle.aluOp
  alu.io.opA <> opA
  alu.io.opB <> opB
  val aluRes = alu.io.res
  // ------------- ALU -------------

  // ------------- MUL & DIV -------------
  val multiplier = Module(new Multiplier(config, latency = 2))
  val divider = Module(new Divider(config, latency = 34))
  val mulReqFired = RegEnable(false.B, false.B, io.fromID.fire)
  when(multiplier.io.req.fire && !io.toMEM.fire) {
    mulReqFired := true.B
  }
  val divReqFired = RegEnable(false.B, false.B, io.fromID.fire)
  when(divider.io.req.fire && !io.toMEM.fire) {
    divReqFired := true.B
  }
  multiplier.io.req.valid := decodeBundle.fuType === FuType.MUL && !mulReqFired && !io.fromID.bits.nop
  multiplier.io.resp.ready := decodeBundle.fuType === FuType.MUL && io.toMEM.ready
  val mulSignedOpA = (decodeBundle.mulOp === MULOp.MUL) ||
    (decodeBundle.mulOp === MULOp.MULH) ||
    (decodeBundle.mulOp === MULOp.MULHSU)
  val mulSignedOpB = (decodeBundle.mulOp === MULOp.MUL) ||
    (decodeBundle.mulOp === MULOp.MULH)
  val mulSigned = mulSignedOpA || mulSignedOpB
  val mulAbsOpA = Mux(mulSignedOpA, opA.asSInt.abs.asUInt, opA)
  val mulAbsOpB = Mux(mulSignedOpB, opB.asSInt.abs.asUInt, opB)
  multiplier.io.req.bits.A := mulAbsOpA
  multiplier.io.req.bits.B := mulAbsOpB
  val isMultResultNeg = Mux(
    mulSigned && decodeBundle.mulOp === MULOp.MULHSU,
    opA(opA.getWidth - 1),
    opA(opA.getWidth - 1) ^ opB(opB.getWidth - 1)
  )
  val multTwoCompEnable = mulSigned && isMultResultNeg
  val mulResOrigin = multiplier.io.resp.bits.P
  val mulResFinal = (Cat(
    multTwoCompEnable,
    Mux(multTwoCompEnable, ~mulResOrigin, mulResOrigin)
  ) + multTwoCompEnable)
  val mulRes = Mux(
    decodeBundle.mulOp === MULOp.MUL,
    mulResFinal(31, 0),
    mulResFinal(63, 32)
  )

  val divResFinal = Wire(UInt(config.xlen.W))
  val remainderFinal = Wire(UInt(config.xlen.W))
  val divRes = Mux(
    decodeBundle.divOp === DIVOp.DIV || decodeBundle.divOp === DIVOp.DIVU,
    divResFinal,
    remainderFinal
  )
  val divSigned =
    decodeBundle.divOp === DIVOp.DIV || decodeBundle.divOp === DIVOp.REM
  val divAbsOpA = Mux(divSigned, opA.asSInt.abs.asUInt, opA)
  val divAbsOpB = Mux(divSigned, opB.asSInt.abs.asUInt, opB)
  divider.io.req.bits.dividend := divAbsOpA
  divider.io.req.bits.divisor := divAbsOpB
  divider.io.req.valid := false.B
  divider.io.resp.ready := false.B
  val divReqNoNeed = WireDefault(false.B)
  when(opB === 0.U) {
    divReqNoNeed := decodeBundle.fuType === FuType.DIV
    divResFinal := 0xffffffffL.U
    remainderFinal := opA
  }.otherwise {
    when(divSigned && opA === 0x80000000L.U && opB === 0xffffffffL.U) {
      divReqNoNeed := decodeBundle.fuType === FuType.DIV
      divResFinal := 0x80000000L.U
      remainderFinal := 0.U
    }.otherwise {
      divider.io.req.valid := decodeBundle.fuType === FuType.DIV && !divReqFired && !io.fromID.bits.nop
      divider.io.resp.ready := decodeBundle.fuType === FuType.DIV && io.toMEM.ready
      val divResOrigin = divider.io.resp.bits.quotient
      val divTowCompEnable =
        divSigned && (opA(opA.getWidth - 1) ^ opB(opB.getWidth - 1))
      divResFinal := (Cat(
        divTowCompEnable,
        Mux(divTowCompEnable, ~divResOrigin, divResOrigin)
      ) + divTowCompEnable)
      val remainderOrigin = divider.io.resp.bits.remainder
      val remainderTowCompEnable = divSigned && opA(opA.getWidth - 1)
      remainderFinal := (Cat(
        remainderTowCompEnable,
        Mux(remainderTowCompEnable, ~remainderOrigin, remainderOrigin)
      ) + remainderTowCompEnable)
    }
  }
  // ------------- MUL -------------

  // ------------- BRU -------------
  val bru = Module(new BRU(config))
  bru.io.bruOp <> decodeBundle.bruOp
  bru.io.opA <> io.fromID.bits.rs1Data
  bru.io.opB <> io.fromID.bits.rs2Data
  // general branch or jump or jumpr
  // val jump = ((bru.io.branch && (decodeBundle.nextPCSrc === NextPCSrc.Branch)) ||
  //                     (decodeBundle.nextPCSrc =/= NextPCSrc.PC4))
  // val jump = (decodeBundle.nextPCSrc === NextPCSrc.Branch && bru.io.branch) ||
  //            (decodeBundle.nextPCSrc =/= NextPCSrc.Branch && decodeBundle.nextPCSrc =/= NextPCSrc.PC4)
  val jump =
    decodeBundle.fuType === FuType.BRU && (bru.io.branch || decodeBundle.bruOp === BRUOp.N)
  val jumpTarget = alu.io.sum
  // csr jump(ecall and mret)
  // val csrJump = decodeBundle.nextPCSrc === NextPCSrc.CSR_J
  // val csrJumpTarget = io.fromID.bits.csrData

  if (config.branchPrediction == BranchPredictionSelect.Static || config.branchPrediction == BranchPredictionSelect.Dynamic) {
    val redirect = (jump ^ io.fromID.bits.branchPred) || (decodeBundle.fuType === FuType.BRU && jumpTarget =/= io.fromID.bits.predTarget)
    dontTouch(redirect)
    io.jumpBus.valid := (redirect || decodeBundle.fencei || decodeBundle.fuType === FuType.CSR) && !io.toMEM.bits.nop
    if (config.simulation){
      RawClockedVoidFunctionCall( 
        "PerfBranchPredictionAccuracy",
        Option(Seq("redirect"))
      )(
        clock,
        enable = decodeBundle.fuType === FuType.BRU && io.toMEM.fire && !io.toMEM.bits.nop && !hasFired,
        redirect
      )
    }
  } else {
    io.jumpBus.valid := (jump || decodeBundle.fencei || decodeBundle.fuType === FuType.CSR) && !io.toMEM.bits.nop
  }
  // io.jumpBus.bits.jumpTarget := Mux1H(Seq(
  //     (jump) -> (jumpTarget),
  //     // (csrJump) -> (csrJumpTarget),
  //     (decodeBundle.fuType === FuType.CSR) -> (io.from)
  //     (decodeBundle.fencei) -> (io.fromID.bits.pc + 4.U)
  // ))

  io.jumpBus.bits.jumpTarget := Mux(jump, jumpTarget, io.fromID.bits.pc + 4.U)

  val jumpBusFired = RegEnable(false.B, false.B, io.jumpBus.fire)
  when(io.jumpBus.fire && !io.toMEM.fire) {
    jumpBusFired := true.B
  }

  if (config.branchPrediction == BranchPredictionSelect.Dynamic) {
    val btbUpdateInfoFired = RegEnable(false.B, false.B, io.toMEM.fire)
    when (io.btbUpdateInfo.fire && !io.toMEM.fire) {
      btbUpdateInfoFired := true.B
    }
    io.btbUpdateInfo.valid := decodeBundle.fuType === FuType.BRU && !io.toMEM.bits.nop && !btbUpdateInfoFired
    io.btbUpdateInfo.bits.pc := io.fromID.bits.pc
    io.btbUpdateInfo.bits.actualTaken := jump
    io.btbUpdateInfo.bits.actualTarget := io.jumpBus.bits.jumpTarget
    io.btbUpdateInfo.bits.btbHitInfo := io.fromID.bits.btbHitInfo
    io.btbUpdateInfo.bits.btbInfo := io.fromID.bits.btbInfo
  }

  // ------------- BRU -------------

  // ------------- CSRU -------------
  val csru = Module(new CSRU(config))
  csru.io.op <> io.fromID.bits.decodeBundle.csrOp
  csru.io.opA <> opA
  csru.io.opB <> opB
  val csrRes = csru.io.res

  io.csrWritePort.wen := decodeBundle.csrWrite.asBool && !io.toMEM.bits.nop
  io.csrWritePort.waddr := io.fromID.bits.funct12
  io.csrWritePort.wdata := csrRes
  if (config.simulation){
    val csrUpdated = RegEnable(io.csrWritePort.wen, io.toMEM.fire)
    val csrUpdatedPC = RegEnable(io.fromID.bits.pc, io.toMEM.fire)
    RawClockedVoidFunctionCall(
      "update_csr",
      Option(Seq("pc"))
    )(clock, enable = csrUpdated && io.toMEM.fire, csrUpdatedPC)
  }
  // ------------- CSRU -------------

  // bypass
  io.bypass.regWrite := decodeBundle.regWrite && !io.toMEM.bits.nop
  io.bypass.waddr := io.fromID.bits.rd
  io.bypass.valid := decodeBundle.fuType =/= FuType.LSU && decodeBundle.fuType =/= FuType.AMO
  io.regWdata := Mux1H(
    Seq(
      (decodeBundle.fuType === FuType.ALU) -> aluRes,
      (decodeBundle.fuType === FuType.CSR) -> io.fromID.bits.csrData,
      (decodeBundle.fuType === FuType.BRU) -> (io.fromID.bits.pc + 4.U),
      (decodeBundle.fuType === FuType.LSU) -> aluRes,
      (decodeBundle.fuType === FuType.MUL) -> mulRes,
      (decodeBundle.fuType === FuType.DIV) -> divRes,
      (decodeBundle.fuType === FuType.AMO) -> aluRes
    )
  )

  // interrupt
  io.interCMD.bits.pc := io.fromID.bits.pc
  io.interCMD.valid := true.B

  // pass the pipeline signal to next stage
  io.toMEM.bits.pc := io.fromID.bits.pc
  val controlSignals = io.toMEM.bits.controlSignals
  controlSignals.signExt := decodeBundle.memSignExt
  controlSignals.regWrite := decodeBundle.regWrite
  // controlSignals.csrWrite     := decodeBundle.csrWrite
  controlSignals.regWriteData := io.regWdata
  controlSignals.memRawMask := decodeBundle.memRead | decodeBundle.memWrite
  controlSignals.memRead := decodeBundle.memRead.orR && !io.fromID.bits.nop
  controlSignals.memWrite := decodeBundle.memWrite.orR && !io.fromID.bits.nop
  controlSignals.memWriteData := io.fromID.bits.rs2Data
  // controlSignals.csrWriteData := csrRes
  controlSignals.fuTypeAMO := decodeBundle.fuType === FuType.AMO
  controlSignals.amoOp := decodeBundle.amoOp
  // pass the exception
  io.toMEM.bits.funct12 := io.fromID.bits.funct12
  io.toMEM.bits.mret := io.fromID.bits.mret
  io.toMEM.bits.rd := io.fromID.bits.rd
  io.toMEM.bits.excepVec := io.fromID.bits.excepVec
  // io.toMEM.bits.hasException  := (io.fromID.bits.hasException) && !io.toMEM.bits.nop
  // io.toMEM.bits.exceptionCode := io.fromID.bits.exceptionCode
  if (config.diff_enable) io.toMEM.bits.jumped := io.jumpBus.fire
  if (config.trace_enable) io.toMEM.bits.inst <> io.fromID.bits.inst

  // TODO: flush here
  io.flush := ((io.jumpBus.valid || io.toMEM.bits.excepVec.asUInt.orR || decodeBundle.fuType === FuType.CSR || interrupt) && !io.toMEM.bits.nop) || io.flushFromMEM

  // handshake signal
  val jumpValid =
    (io.jumpBus.valid && io.jumpBus.fire) || jumpBusFired || (!io.jumpBus.valid)
  val mulOrDivEnable =
    ((decodeBundle.fuType === FuType.MUL) || (decodeBundle.fuType === FuType.DIV)) && !io.toMEM.bits.nop
  val mulOrDivValid =
    !mulOrDivEnable || (mulOrDivEnable && (multiplier.io.resp.fire || divider.io.resp.fire || divReqNoNeed))

  io.toMEM.valid := jumpValid && mulOrDivValid
  io.fromID.ready := io.toMEM.ready && jumpValid && mulOrDivValid

  // DPI-C
  // skip: uart gpio keyboard
  if (config.trace_enable) {
    RawClockedVoidFunctionCall(
      "ftrace",
      Option(Seq("pc", "target", "rd", "rs1"))
    )(
      clock,
      enable =
        io.fromID.bits.ftrace.doFtrace && !io.toMEM.bits.nop && io.toMEM.fire,
      io.fromID.bits.pc,
      alu.io.sum,
      Cat(
        0.U((config.xlen - log2Up(config.nr_reg)).W),
        io.fromID.bits.ftrace.rd
      ),
      Cat(
        0.U((config.xlen - log2Up(config.nr_reg)).W),
        io.fromID.bits.ftrace.rs1
      )
    )
  }
  if (config.simulation) {
    RawClockedVoidFunctionCall(
      "PerfCountEXU"
    )(
      clock,
      enable =
        io.fromID.bits.decodeBundle.fuType === FuType.ALU && !io.toMEM.bits.nop
    )
  }
}
