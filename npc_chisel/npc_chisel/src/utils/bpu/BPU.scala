package utils.bpu

import chisel3._
import utils.RVConfig
import utils.cache.CacheBasicConfig
import utils.cache.BTBConfig
import chisel3.util.log2Up
import utils.memory.SDPRAM.SDPRAM_ASYNC
import utils.bus.B
import chisel3.util.Mux1H
import chisel3.util.Irrevocable
import chisel3.util.Cat

final case class BTBLineInfo(config: RVConfig, btbConfig: BTBConfig) extends Bundle{
  val lru = UInt(log2Up(btbConfig.ways).W)
  // branch instruction address, generate it by function
  val bia = Vec(btbConfig.ways, UInt(btbConfig.biaWidth.W))
  val tracker = Vec(btbConfig.ways, UInt(btbConfig.trackerWidth.W))
  val bta = Vec(btbConfig.ways, UInt(config.xlen.W))
}

class BranchPredReq(config: RVConfig, btbConfig: BTBConfig) extends Bundle{
  val pc = Input(UInt(config.xlen.W))
  val branchPred = Output(Bool())
  val branchPredTarget = Output(UInt(config.xlen.W))
  val btbInfo = Output(new BTBLineInfo(config, btbConfig))
  val btbHitInfo = Output(UInt(btbConfig.ways.W))
}

class BTBUpdateInfo(config: RVConfig, btbConfig: BTBConfig) extends Bundle {
  val pc = UInt(config.xlen.W)
  val actualTaken = Bool()
  val actualTarget = UInt(config.xlen.W)
  val btbHitInfo = UInt(btbConfig.ways.W)
  val btbInfo = new BTBLineInfo(config, btbConfig)
}

class BPU(config: RVConfig, btbConfig: BTBConfig) extends Module{
  val io = IO(new Bundle {
    val branchPredReq = new BranchPredReq(config, btbConfig)
    val btbUpdateInfo = Flipped(Irrevocable(new BTBUpdateInfo(config, btbConfig)))
  })

  val valids = RegInit(VecInit(Seq.fill(btbConfig.blocks)(VecInit(Seq.fill(btbConfig.ways)(false.B)))))
  val infoRam = Module(new SDPRAM_ASYNC(btbConfig.blocks, new BTBLineInfo(config, btbConfig)))

  def getBTBTag(addr: UInt): UInt = {
    addr(btbConfig.biaWidth + 1, 2)
  }

  val pc = io.branchPredReq.pc
  val idx = pc(btbConfig.indexRangeHi, btbConfig.indexRangeLo)
  val tag = getBTBTag(pc)

  val btbInfo = infoRam.read(idx).head
  val btbValids = valids(idx)

  val hits = btbValids.zip(btbInfo.bia).map {case(valid, t) =>
    valid && t === tag  
  }
  
  val hit = hits.reduce(_ || _)

  val branchPredTarget = Mux1H(hits, btbInfo.bta)
  val tracker = Mux1H(hits, btbInfo.tracker)

  io.branchPredReq.branchPred := tracker(tracker.getWidth - 1) && hit
  io.branchPredReq.branchPredTarget := branchPredTarget
  io.branchPredReq.btbInfo := btbInfo
  io.branchPredReq.btbHitInfo := Cat(hits.reverse)

  // ----------------------------------------
  // BTB update
  io.btbUpdateInfo.ready := true.B
  def BTBUpdate(pc: UInt, actualTaken: Bool, actualTarget: UInt, btbInfo: BTBLineInfo, hitInfo: UInt): Unit = {
    val idx = pc(btbConfig.indexRangeHi, btbConfig.indexRangeLo)
    val tag = getBTBTag(pc)

    val updateWay = Mux(hitInfo.orR, hitInfo(1), btbInfo.lru)
    val newInfo = Wire(btbInfo.copy())
    val oldTracker = Mux(hitInfo.orR, btbInfo.tracker(updateWay), 1.U)
    val newTracker = Mux(actualTaken,
                  Mux(oldTracker === 3.U, 3.U, oldTracker + 1.U),
                  Mux(oldTracker === 0.U, 0.U, oldTracker - 1.U)
                ) 
    newInfo := btbInfo
    newInfo.bia(updateWay) := tag
    newInfo.tracker(updateWay) := newTracker
    newInfo.bta(updateWay) := actualTarget
    newInfo.lru := ~updateWay(0)
    valids(idx)(updateWay) := true.B
    infoRam.write(io.btbUpdateInfo.fire, idx, newInfo.asTypeOf(infoRam.io.wdata), 1.U)
  }

  BTBUpdate(
    io.btbUpdateInfo.bits.pc, 
    io.btbUpdateInfo.bits.actualTaken, 
    io.btbUpdateInfo.bits.actualTarget, 
    io.btbUpdateInfo.bits.btbInfo,
    io.btbUpdateInfo.bits.btbHitInfo
  )
}
