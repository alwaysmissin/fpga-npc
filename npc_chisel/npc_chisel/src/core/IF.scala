package core

import chisel3._
import utils.RVConfig
import utils.bus.R
import utils.bus.InterStage.IfIdBus
import chisel3.util._
import utils.bus.InterStage.PreIFIFBus
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import utils.bus.SRAMLike._
import utils.bus.InterStage.JumpBus
import utils.csr.RedirectCMD
import utils.ExceptionCodes
import utils.bpu.BranchPredReq
import utils.cache.BTBConfig
import utils.BranchPredictionSelect

class IF(config: RVConfig, btbConfig: BTBConfig) extends Module with ExceptionCodes {
  val io = IO(new Bundle {
    // val r = Flipped(Irrevocable(new R(config)))
    val ar = Irrevocable(new SRAMLikeReq(config))
    val r = Flipped(Irrevocable(new SRAMLikeRResp(config)))
    // val fromPreIF = Flipped(Irrevocable(new PreIFIFBus(config)))
    val jumpBus = Flipped(Irrevocable(new JumpBus(config)))
    val excepCMD = Flipped(Irrevocable(new RedirectCMD(config)))
    val toID = Irrevocable(new IfIdBus(config, btbConfig))
    val flush = Input(Bool())
    val branchPredReq = if (config.branchPrediction == BranchPredictionSelect.Dynamic) {
      Flipped(new BranchPredReq(config, btbConfig))
    } else {
      null
    }
  })
  val reqFired = RegEnable(false.B, false.B, io.toID.fire)
  when(io.ar.fire && !reqFired && !io.toID.fire) {
    reqFired := true.B
  }
  io.jumpBus.ready := io.toID.valid
  io.excepCMD.ready := io.toID.fire && !reqFired

  val npc = Wire(UInt(config.xlen.W))
  val PC = RegEnable(npc, config.PC_INIT - 4.U, io.toID.fire)
  if (config.branchPrediction == BranchPredictionSelect.Static) {
    val inst = io.toID.bits.inst
    val branchPred = inst(31) && inst(6, 0) === 0x63.U
    val offset =
      Cat(Fill(20, inst(31)), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
    val predTarget = PC + offset
    npc := PriorityMux(
      Seq(
        (io.excepCMD.valid) -> io.excepCMD.bits.target,
        (io.jumpBus.valid) -> io.jumpBus.bits.jumpTarget,
        (branchPred) -> predTarget,
        (true.B) -> (PC + 4.U)
      )
    )
    io.toID.bits.branchPred := branchPred
    io.toID.bits.predTarget := predTarget
  } else if (config.branchPrediction == BranchPredictionSelect.Dynamic){
    io.branchPredReq.pc := PC
    val branchPred = io.branchPredReq.branchPred
    val predTarget = io.branchPredReq.branchPredTarget
    npc := PriorityMux(
      Seq(
        (io.excepCMD.valid) -> io.excepCMD.bits.target,
        (io.jumpBus.valid) -> io.jumpBus.bits.jumpTarget,
        (branchPred) -> predTarget,
        (true.B) -> (PC + 4.U)
      )
    )
    io.toID.bits.branchPred := branchPred
    io.toID.bits.predTarget := predTarget
    io.toID.bits.btbHitInfo := io.branchPredReq.btbHitInfo
    io.toID.bits.btbInfo := io.branchPredReq.btbInfo
  } else if (config.branchPrediction == BranchPredictionSelect.None){
    npc := PriorityMux(
      Seq(
        (io.excepCMD.valid) -> io.excepCMD.bits.target,
        (io.jumpBus.valid) -> io.jumpBus.bits.jumpTarget,
        (true.B) -> (PC + 4.U)
      )
    )
  } else {
    assert(false, "Branch Prediction not supported")
  }

  val instGetted = RegEnable(false.B, false.B, io.toID.fire)
  val instReg =
    RegEnable(io.r.bits.rdata, 0.U(config.xlen.W), io.r.fire && !instGetted)
  when(io.r.fire && !instGetted && !io.toID.fire) {
    instGetted := true.B
  }

  io.ar.valid := (!reset.asBool && (!reqFired))
  io.ar.bits.addr := npc
  io.ar.bits.wr := false.B
  io.ar.bits.len := 0.U
  io.ar.bits.size := 2.U
  io.ar.bits.wdata := DontCare
  io.ar.bits.wstrb := DontCare

  io.r.ready := io.r.valid && !instGetted
  val instValid = io.r.fire || instGetted
  val reqValid = io.ar.fire || reqFired

  io.toID.bits.inst := Mux(io.r.fire, io.r.bits.rdata, instReg)
  io.toID.bits.pc := PC
  io.toID.bits.nop := PC === (config.PC_INIT - 4.U) || io.flush
  // TODO: 暂时忽略Instruction Access Fault异常
  // io.toID.bits.hasException := PC(1, 0).orR && !io.toID.bits.nop
  // io.toID.bits.exceptionCode := InstructionAddressMisaligned.U
  io.toID.bits.excepVec.map(_ := false.B)
  io.toID.bits.excepVec(InstructionAddressMisaligned) := PC(1, 0).orR

  io.toID.valid := (instValid && reqValid) || PC === config.PC_INIT

  if (config.simulation) {
    RawClockedVoidFunctionCall(
      "IFULaunchARReq"
    )(clock, enable = io.ar.fire)
    RawClockedVoidFunctionCall(
      "PerfCountIFU"
    )(clock, enable = io.r.fire)
    RawClockedVoidFunctionCall(
      "IFURecvRResp"
    )(clock, enable = io.r.fire)
  }

}
