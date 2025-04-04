import utils.id.IDU
import utils.RVConfig
import chisel3._
import utils.exe.ALU
import peripheral.XBar
import peripheral.Arbiter
import utils.bus.AXI4
import utils.Config.FPGAPlatform


object Elaborate extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)) 
    
  val config = RVConfig(
    PC_INIT = 0x10000000L.U,
    xlen = 32,
    nr_reg = 32,
    csr_width = 12,
    diff_enable = true,
    trace_enable = true,
    simulation = true
  )
  if (args.length > 0 && args(0) == "sta") {
    print("Generate for Static Timing Analysis\n")
    config.diff_enable = false
    config.trace_enable = false
    config.simulation = false
  } else if (args.length > 0 && args(0) == "fpga") {
    print("Generate for FPGA\n")
    config.diff_enable = false
    config.trace_enable = false
    config.simulation = false
    
  } else {
    FPGAPlatform = false
    print("Generate for Simulation\n")
  }

  circt.stage.ChiselStage.emitSystemVerilogFile(new ysyx_23060051(config)
  , args, firtoolOptions)
  // circt.stage.ChiselStage.emitSystemVerilogFile(new utils.IDStage.IDU(RVConfig(
  //   PC_INIT = (0x80000000L - 4).U,
  //   xlen = 32,
  //   nr_reg = 32
  // )), args, firtoolOptions)
  // circt.stage.ChiselStage.emitSystemVerilogFile(new gcd.GCD(), args, firtoolOptions)
}
