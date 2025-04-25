package verification.trace

import chisel3._
import utils.RVConfig

class TraceSignals(config: RVConfig) extends Bundle {
  val inst = Output(UInt(config.xlen.W))

}
