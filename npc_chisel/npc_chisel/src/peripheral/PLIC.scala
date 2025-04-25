package peripheral

import utils.RVConfig
import chisel3._
import utils.bus.AXI4

class PLIC(config: RVConfig, nrIntr: Int) extends Module {
  val io = IO(new Bundle{
    val bus = AXI4(config, AXI4.MS.asSlave)
    val intrVec = Input(Vec(nrIntr, Bool()))
    val meip = Output(Bool())
  })
  require(nrIntr < 1024)
  
}
