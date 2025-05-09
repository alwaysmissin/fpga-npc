package utils
import chisel3._
object BranchPredictionSelect{
  def None: Int = 0
  def Static: Int = 1
  def Dynamic: Int = 2
}
case class RVConfig(
    PC_INIT: UInt,
    xlen: Int,
    nr_reg: Int,
    csr_width: Int,
    memorySize: Int = 256,
    // staticBranchPrediction: Boolean = true,
    branchPrediction: Int = BranchPredictionSelect.Dynamic,
    var diff_enable: Boolean = false,
    var trace_enable: Boolean = false,
    var simulation: Boolean = true
) {
  val debug_enable = diff_enable | trace_enable
}

object Config {
  var FPGAPlatform = true
}
