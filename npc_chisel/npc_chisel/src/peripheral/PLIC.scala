package peripheral

import utils.RVConfig
import chisel3._
import chisel3.util._
import utils.bus.AXI4
import utils.nutshellUtils.RegMap
import utils.nutshellUtils.MaskExpand

class PLIC(config: RVConfig, nrIntr: Int) extends Module {
  val io = IO(new Bundle{
    val bus = AXI4(config, AXI4.MS.asSlave)
    val intrVec = Input(UInt(nrIntr.W))
    val meip = Output(Bool())
  })
  require(nrIntr < 1024)
  val addressSpaceSize = 0x4000000
  val addressBits = log2Up(addressSpaceSize)
  def getOffset(addr: UInt) = addr(addressBits - 1, 0)

  val arReg = RegNext(io.bus.ar.bits)
  val awReg = RegNext(io.bus.aw.bits)
  val wReg = RegNext(io.bus.w.bits)


  val priority = List.fill(nrIntr)(RegInit(5.U(32.W)))
  val priorityMap = priority.zipWithIndex.map{case (r, intr) => 
    RegMap((intr + 1) * 4, r)
  }.toMap

  val nrIntrWord = (nrIntr + 31) / 32

  val pending = List.fill(nrIntrWord)(RegInit(0.U.asTypeOf(Vec(32, Bool()))))
  val pendingMap = pending.zipWithIndex.map{case(r, intrWord) => 
    RegMap(0x1000 + intrWord * 4, Cat(r.reverse), RegMap.Unwritable)  
  }.toMap

  // TODO: enable interrupt for test
  val enable = List.fill(nrIntrWord)(RegInit(2.U(32.W)))
  val enableMap = enable.zipWithIndex.map{case(r, intrWord) => 
    RegMap(0x2000 + intrWord * 4, r)  
  }.toMap

  val threshold = RegInit(0.U(32.W))
  val thresholdMap = Map(RegMap(0x200000, threshold))

  val inHandle = RegInit(0.U.asTypeOf(Vec(nrIntr + 1, Bool())))
  def completionFn(wdata: UInt) = {
    inHandle(wdata(31, 0)) := false.B
    0.U
  }

  val claimCompletion = RegInit(0.U(32.W))
  val claimCompletionMap = {
    val addr = 0x200004
    when (io.bus.r.fire && arReg.addr === addr.U) { inHandle(claimCompletion) := false.B}
    Map(RegMap(addr, claimCompletion, completionFn))
  }

  io.intrVec.asBools.zipWithIndex.map {case(intr, i) => {
    val id = i + 1
    when (intr) {pending(id / 32)(id % 32) := true.B}
    when (inHandle(id)) {pending(id / 32)(id % 32) := false.B}
  }}

  val pendingVec = Cat(pending.map(x => Cat(x.reverse)))
  val takenVec = pendingVec & Cat(enable)
  dontTouch(takenVec)
  // TODO: invalid arbitration
  claimCompletion := Mux(takenVec === 0.U, 0.U, PriorityEncoder(takenVec))

  val mapping = priorityMap ++ pendingMap ++ enableMap ++ thresholdMap ++ claimCompletionMap

  val rdataWire = Wire(UInt(32.W))
  RegMap.generate(mapping, getOffset(arReg.addr), rdataWire, 
        getOffset(io.bus.aw.bits.addr), io.bus.w.fire, io.bus.w.bits.data,
        MaskExpand(io.bus.w.bits.strb))

  io.bus.ar.ready := true.B
  io.bus.w.ready := true.B
  io.bus.aw.ready := true.B

  io.bus.r.bits.data := RegNext(rdataWire)
  io.bus.r.valid := RegNext(io.bus.ar.fire)
  io.bus.b.valid := RegNext(io.bus.aw.fire && io.bus.w.fire)

  io.bus.r.bits.resp := 0.U
  io.bus.r.bits.id := 1.U
  io.bus.r.bits.last := true.B
  io.bus.b.bits.resp := 0.U
  io.bus.b.bits.id := 1.U

  io.meip := claimCompletion =/= 0.U

  
}
