package utils.bus

import chisel3._
import chisel3.util._
import utils.RVConfig
import utils.ExceptionCodes
import utils.id.DecodeBundle
import utils.id.ControlSignals
import utils.bpu.BranchPredReq
import utils.bpu.BTBLineInfo
import utils.cache.BTBConfig
import utils.BranchPredictionSelect

object InterStage {
  class JumpBus(config: RVConfig) extends Bundle {
    //   val jump = Bool()
    val jumpTarget = UInt(config.xlen.W)
  }

  class PreIFIFBus(config: RVConfig) extends Bundle {
    val pc = Output(UInt(config.xlen.W))
    val nop = Output(Bool())
    val hasException = Output(Bool())
    val exceptionCode = Output(UInt(4.W))
  }

  class IfIdBus(config: RVConfig, btbConfig: BTBConfig) extends Bundle {
    val pc = Output(UInt(config.xlen.W))
    val inst = Output(UInt(config.xlen.W))
    val nop = Output(Bool())
    val branchPred = if (config.branchPrediction == BranchPredictionSelect.Static || config.branchPrediction == BranchPredictionSelect.Dynamic) Output(Bool()) else null
    val predTarget = if (config.branchPrediction == BranchPredictionSelect.Static || config.branchPrediction == BranchPredictionSelect.Dynamic) Output(UInt(config.xlen.W)) else null
    val btbHitInfo = if (config.branchPrediction == BranchPredictionSelect.Dynamic) Output(UInt(btbConfig.ways.W)) else null
    val btbInfo    = if (config.branchPrediction == BranchPredictionSelect.Dynamic) Output(BTBLineInfo(config, btbConfig)) else null
    // val hasException = Output(Bool())
    val excepVec = Output(Vec(16, Bool()))
    // val exceptionCode = Output(UInt(4.W))
  }

  class FtraceBundle(config: RVConfig) extends Bundle {
    val doFtrace = Bool()
    val rd = UInt(log2Up(config.nr_reg).W)
    val rs1 = UInt(log2Up(config.nr_reg).W)
  }

  class IdExeBus(config: RVConfig, btbConfig: BTBConfig) extends Bundle {
    val decodeBundle = Output(new DecodeBundle)
    val pc = Output(UInt(config.xlen.W))
    val imm = Output(UInt(config.xlen.W))
    val rs1Data = Output(UInt(config.xlen.W))
    val rs2Data = Output(UInt(config.xlen.W))
    val csrData = Output(UInt(config.xlen.W))
    val funct12 = Output(UInt(12.W))
    val rd = Output(UInt(log2Up(config.nr_reg).W))
    val rs1 = Output(UInt(log2Up(config.nr_reg).W))
    val nop = Output(Bool())
    val mret = Output(Bool())
    val branchPred = if (config.branchPrediction == BranchPredictionSelect.Static || config.branchPrediction == BranchPredictionSelect.Dynamic) Output(Bool()) else null
    val predTarget = if (config.branchPrediction == BranchPredictionSelect.Static || config.branchPrediction == BranchPredictionSelect.Dynamic) Output(UInt(config.xlen.W)) else null
    val btbHitInfo = if (config.branchPrediction == BranchPredictionSelect.Dynamic) Output(UInt(btbConfig.ways.W)) else null
    val btbInfo    = if (config.branchPrediction == BranchPredictionSelect.Dynamic) Output(BTBLineInfo(config, btbConfig)) else null
    // val hasException = Output(Bool())
    val excepVec = Output(Vec(16, Bool()))
    // val jumped = if(config.diff_enable) Output(Bool()) else null
    val inst = if (config.trace_enable) Output(UInt(config.xlen.W)) else null
    val ftrace =
      if (config.trace_enable) Output(new FtraceBundle(config)) else null
    val isWFI = if (config.diff_enable) Output(Bool()) else null
  }

  class ExeMemSignalsBundle(config: RVConfig) extends Bundle {
    //   val fuType = UInt(ControlSignals.FuType.WIDTH.W)
    val memRawMask = UInt(ControlSignals.MemRead.WIDTH.W)
    val signExt = Bool()
    // val csrWriteData = UInt(config.xlen.W)
    val regWriteData = UInt(config.xlen.W)
    val memWriteData = UInt(config.xlen.W)
    val regWrite = Bool()
    // val csrWrite = Bool()
    val memWrite = Bool()
    val memRead = Bool()
    val fuTypeAMO = Bool()
    val amoOp = UInt(ControlSignals.AMOOp.WIDTH.W)
  }

  class ExeMemBus(config: RVConfig) extends Bundle {
    val pc = Output(UInt(config.xlen.W))
    val rd = Output(UInt(log2Up(config.nr_reg).W))
    val nop = Output(Bool())
    val jumped = if (config.diff_enable) Output(Bool()) else null
    val inst = if (config.trace_enable) Output(UInt(config.xlen.W)) else null
    val flushForIntr = if (config.diff_enable) Output(Bool()) else null
    val isWFI = if (config.diff_enable) Output(Bool()) else null
    val funct12 = Output(UInt(12.W))
    val mret = Output(Bool())
    val controlSignals = Output(new ExeMemSignalsBundle(config))
    // val hasException = Output(Bool())
    val excepVec = Output(Vec(16, Bool()))
  }

  // class MemWbSignalsBundle(config: RVConfig) extends Bundle{
  //     val fuType = UInt(ControlSignals.FuType.WIDTH.W)
  //     val aluRes = UInt(config.xlen.W)
  //     val csrRes = UInt(config.xlen.W)
  //     val regWrite = Bool()
  //     val regWriteSrc = UInt(ControlSignals.RegWriteSrc.WIDTH.W)
  // }

  class MemWbBus(config: RVConfig) extends Bundle {
    val pc = Output(UInt(config.xlen.W))
    val rd = Output(UInt(log2Up(config.nr_reg).W))
    // val aluRes = Output(UInt(config.xlen.W))
    // val csrData = Output(UInt(config.xlen.W))
    val funct12 = Output(UInt(12.W))
    // val memRes = Output(UInt(config.xlen.W))
    val regWrite = Output(Bool())
    // val csrWriteData = Output(UInt(config.xlen.W))
    val regWriteData = Output(UInt(config.xlen.W))
    // val csrWrite = Output(Bool())
    val nop = Output(Bool())
    val mret = Output(Bool())
    // val hasException = Output(Bool())
    val excepVec = Output(Vec(16, Bool()))
    val jumped = if (config.diff_enable) Output(Bool()) else null
    val inst = if (config.trace_enable) Output(UInt(config.xlen.W)) else null
    val flushForIntr = if (config.diff_enable) Output(Bool()) else null
    // val controlSignals = Output(new MemWbSignalsBundle(config))
  }
}
