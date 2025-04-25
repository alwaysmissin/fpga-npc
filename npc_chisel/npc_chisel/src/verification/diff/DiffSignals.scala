package verification.diff

import chisel3._
import utils.RVConfig

class DiffSignals(config: RVConfig) extends Bundle {
  // val done = Output(Bool())
  // val pc_done = Output(UInt(config.xlen.W))
  val jumped = Output(Bool())
  // val npc_done = Output(UInt(config.xlen.W))

}
