package verification
import chisel3._
import utils.RVConfig

class DebugSignals(config: RVConfig) extends Bundle {
    val done = Output(Bool())
    val pc_done = Output(UInt(config.xlen.W))
}
