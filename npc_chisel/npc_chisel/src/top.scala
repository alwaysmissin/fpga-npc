import chisel3._
import utils.RVConfig
import dataclass.data
import verification.diff.DiffSignals
import verification.DebugSignals
import verification.trace.TraceSignals
import peripheral._
class top(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val debug: DebugSignals =
      if (config.debug_enable) new DebugSignals(config) else null
    val diff: DiffSignals =
      if (config.diff_enable) new DiffSignals(config) else null
    val trace: TraceSignals =
      if (config.trace_enable) new TraceSignals(config) else null
  })
  val core = Module(new Core(config))
  val xbar = Module(new XBar(config, 32))
  val dataMemory = Module(new Memory(config))

  core.io.dbus <> xbar.io.dbus
  core.io.ibus <> xbar.io.ibus
  xbar.io.toMem <> dataMemory.io.dbus
  dataMemory.io.clk := clock
  if (config.debug_enable) {
    io.debug <> core.io.debug
  }
  if (config.diff_enable) {
    io.diff <> core.io.diff
  }
  if (config.trace_enable) {
    io.trace <> core.io.trace
  }
}
