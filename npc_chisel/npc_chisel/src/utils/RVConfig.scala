package utils
import chisel3._
case class RVConfig(
    PC_INIT: UInt,
    xlen: Int,
    nr_reg: Int,
    csr_width: Int,
    memorySize: Int = 256,
    staticBranchPrediction: Boolean = false,
    var diff_enable: Boolean = false,
    var trace_enable: Boolean = false,
    var simulation: Boolean = true
){
    val debug_enable = diff_enable | trace_enable
}
