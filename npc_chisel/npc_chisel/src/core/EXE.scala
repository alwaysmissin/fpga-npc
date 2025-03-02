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

class EXE(config: RVConfig) extends Module{
    val io = IO(new Bundle{
        val fromID = Flipped(Irrevocable(new IdExeBus(config)))
        val toMEM = Irrevocable(new ExeMemBus(config))
        val bypass = Flipped(new BypassFrom(config))
        val regWdata = Output(UInt(config.xlen.W))
        val req = Irrevocable(new SRAMLikeReq(config))
        val jumpBus = Irrevocable(new JumpBus(config))
        val flush = Output(Bool())
    })
    val decodeBundle = io.fromID.bits.decodeBundle

    // 确保当前指令只被发送一次
    val hasFired = RegEnable(false.B, false.B, io.toMEM.fire && io.fromID.fire)
    when (io.toMEM.fire && !io.fromID.fire){
        hasFired := true.B
    }
    io.toMEM.bits.nop := io.fromID.bits.nop || hasFired
    

    // ------------- ALU -------------
    val opASrc = decodeBundle.aluSrc1
    val opBSrc = decodeBundle.aluSrc2
    val opA = Mux1H(Seq(
        (opASrc === OpASrc.RS1 ) -> io.fromID.bits.rs1Data,
        (opASrc === OpASrc.PC  ) -> io.fromID.bits.pc,
        (opASrc === OpASrc.ZERO) -> 0.U
    ))
    val opB = Mux1H(Seq(
        (opBSrc === OpBSrc.RS2) -> io.fromID.bits.rs2Data,
        (opBSrc === OpBSrc.CSR) -> io.fromID.bits.csrData,
        (opBSrc === OpBSrc.IMM) -> io.fromID.bits.imm,
    ))
    val alu = Module(new ALU(config))
    alu.io.op <> decodeBundle.aluOp
    alu.io.opA <> opA
    alu.io.opB := opB
    val aluRes = alu.io.res
    // ------------- ALU -------------

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
    val jump = decodeBundle.fuType === FuType.BRU && (bru.io.branch || decodeBundle.bruOp === BRUOp.N)
    val jumpTarget = alu.io.sum
    // csr jump(ecall and mret)
    // val csrJump = decodeBundle.nextPCSrc === NextPCSrc.CSR_J
    // val csrJumpTarget = io.fromID.bits.csrData

    if (config.staticBranchPrediction){
        io.jumpBus.valid := ((jump ^ io.fromID.bits.branchPred) || decodeBundle.fencei || decodeBundle.fuType === FuType.CSR) && !io.toMEM.bits.nop
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
    when(io.jumpBus.fire && !io.toMEM.fire){
        jumpBusFired := true.B
    }

    // ------------- BRU -------------

    // ------------- CSRU -------------
    val csru = Module(new CSRU(config))
    csru.io.op <> io.fromID.bits.decodeBundle.csrOp
    csru.io.opA <> opA
    csru.io.opB <> opB
    val csrRes = csru.io.res
    // ------------- CSRU -------------

    // for lsu
    // ------------- LSU -------------
    // ------------- READ -------------
    val reqFired = RegEnable(false.B, io.fromID.fire)
    val ren = io.fromID.bits.decodeBundle.memRead.orR && !io.toMEM.bits.nop // MemRead.N
    val wen = io.fromID.bits.decodeBundle.memWrite.orR && !io.toMEM.bits.nop // MemWrite.N
    io.req.valid := (ren || wen) && !reqFired
    val byteWise = io.fromID.bits.decodeBundle.memRead | io.fromID.bits.decodeBundle.memWrite
    val reqSize = PriorityMux(Seq(
        (byteWise(3)) -> "b010".U,
        (byteWise(1)) -> "b001".U,
        (byteWise(0)) -> "b000".U
    ))

    // launch read request
    val vaddr = alu.io.sum
    val vaddrLow2Bits = vaddr(1, 0)
    val align = PriorityMux(Seq(
        (byteWise(3)) -> (!vaddrLow2Bits.orR),
        (byteWise(1)) -> (!vaddrLow2Bits(0)),
        (byteWise(0)) -> true.B
    ))
    // align check when simulation is on
    if (config.simulation){
        switch(reqSize){
            is("b010".U){ 
                assert(vaddr(1, 0) === 0.U, "not align!!!!!, reqSize is %d, but target address is 0x%x, the pc is 0x%x\n", 1.U << reqSize, vaddr, io.fromID.bits.pc)
            }
            is("b001".U){
                assert(vaddr(0) === 0.U, "not align!!!!!, reqSize is %d, but target address is 0x%x, the pc is 0x%x\n", 1.U << reqSize, vaddr, io.fromID.bits.pc)
            }
            is("b000".U){}
        }
    }

    // ------------- READ -------------
    // launch the write request
    // ------------- WRITE -------------
    // handle the wdata to make it align
    val rawData = io.fromID.bits.rs2Data
    // launch request
    io.req.bits.addr := vaddr
    io.req.bits.len := 0.U
    io.req.bits.wr := wen
    io.req.bits.wdata := io.fromID.bits.rs2Data
    io.req.bits.wstrb := decodeBundle.memWrite << vaddrLow2Bits
    io.req.bits.size := reqSize
    io.req.bits.wdata := PriorityMux(Seq(
        decodeBundle.memWrite(3) -> rawData,
        decodeBundle.memWrite(1) -> Fill(2, rawData(15, 0)),
        decodeBundle.memWrite(0) -> Fill(4, rawData( 7, 0)))
    )
    io.toMEM.bits.controlSignals.memReadMask := decodeBundle.memRead
    // AXIFULL
    // io.w.bits.last := true.B
    // val wFired = RegInit(false.B)
    // when(io.w.fire && !io.aw.fire){
    //     wFired := true.B
    // }
    when(!io.toMEM.fire){
        when(io.req.fire){
            reqFired := true.B
            // wFired := false.B
        }
    }
    // ------------- WRITE -------------
    // ------------- LSU -------------

    // bypass 
    io.bypass.regWrite := decodeBundle.regWrite && !io.toMEM.bits.nop
    io.bypass.waddr := io.fromID.bits.rd
    io.bypass.valid := decodeBundle.fuType =/= FuType.LSU
    io.regWdata := Mux1H(Seq(
        (decodeBundle.fuType === FuType.ALU) -> aluRes,
        (decodeBundle.fuType === FuType.CSR) -> io.fromID.bits.csrData,
        (decodeBundle.fuType === FuType.BRU) -> (io.fromID.bits.pc + 4.U),
        (decodeBundle.fuType === FuType.LSU) -> aluRes
    ))

    // pass the pipeline signal to next stage
    io.toMEM.bits.pc := io.fromID.bits.pc
    io.toMEM.bits.controlSignals.signExt := decodeBundle.memSignExt
    io.toMEM.bits.controlSignals.regWrite := decodeBundle.regWrite
    io.toMEM.bits.controlSignals.csrWrite := decodeBundle.csrWrite
    io.toMEM.bits.controlSignals.regWriteData := io.regWdata
    if (config.diff_enable) io.toMEM.bits.jumped := io.jumpBus.fire
    if (config.trace_enable) io.toMEM.bits.inst <> io.fromID.bits.inst
    io.toMEM.bits.funct12 <> io.fromID.bits.funct12
    io.toMEM.bits.mret    <> io.fromID.bits.mret
    io.toMEM.bits.rd <> io.fromID.bits.rd
    io.toMEM.bits.controlSignals.memRead := ren
    io.toMEM.bits.controlSignals.memWrite := wen
    io.toMEM.bits.controlSignals.csrWriteData := csrRes
    // pass the exception
    io.toMEM.bits.hasException := (io.fromID.bits.hasException || (!align && (wen || ren))) && !io.toMEM.bits.nop
    io.toMEM.bits.exceptionCode := Mux(!align && (wen || ren), ExceptionCodes.LoadAddressMisaligned, io.fromID.bits.exceptionCode)

    io.flush := (io.jumpBus.valid || io.toMEM.bits.hasException || decodeBundle.fuType === FuType.CSR) && !io.toMEM.bits.nop

    // handshake signal
    val lsuValid = reqFired || 
                   (!(ren || wen)) || 
                   (((ren || wen) && (io.req.fire || reqFired)))
    val jumpValid = (io.jumpBus.valid && io.jumpBus.fire) || jumpBusFired || (!io.jumpBus.valid)

    io.toMEM.valid := lsuValid && jumpValid
    io.fromID.ready := io.toMEM.ready && lsuValid && jumpValid

    // DPI-C
    // skip: uart gpio keyboard
    if (config.diff_enable){
        val addrRangesToSkip = (vaddr >= 0x02000000L.U && vaddr < 0x02000008L.U) ||  // clint
                               (vaddr >= 0x10000000L.U && vaddr < 0x10002010L.U) ||  // uart & gpio
                               (vaddr >= 0x10011000L.U && vaddr < 0x10011008L.U) ||  // keyboard
                               (vaddr >= 0x21000000L.U && vaddr < 0x211fffffL.U)     // gpu(vga)
        val skip_enable = addrRangesToSkip && ((ren || wen) && io.req.fire) && !io.toMEM.bits.nop
        dontTouch(skip_enable)
        RawClockedVoidFunctionCall(
            "diff_skip"
        )(clock, enable = skip_enable)
    }
    if (config.simulation){
        RawClockedVoidFunctionCall(
            "PerfCountEXU",
        )(clock, enable = io.fromID.bits.decodeBundle.fuType === FuType.ALU && !io.toMEM.bits.nop)
        RawClockedVoidFunctionCall(
            "LSULaunchARReq"
        )(clock, enable = io.req.fire && !io.req.bits.wr)
        RawClockedVoidFunctionCall(
            "LSULaunchAWReq"
        )(clock, enable = io.req.fire && io.req.bits.wr)
    }
}
