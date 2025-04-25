import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import peripheral.XBar
class ysyx_23060051(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val interrupt = Input(Bool())
    val master = AXI4(config, AXI4.MS.asMaster)
    val slave = AXI4(config, AXI4.MS.asSlave)
  })
  val core = Module(new Core(config))
  val xbar = Module(new XBar(config))
  core.io.dbus <> xbar.io.dbus
  core.io.ibus <> xbar.io.ibus
  xbar.io.toMem <> io.master
  xbar.io.interrupt <> core.io.interrupt

  io.slave <> DontCare

  if (config.simulation) dontTouch(core.io.debug)
  if (config.trace_enable) dontTouch(core.io.trace)
  if (config.diff_enable) dontTouch(core.io.diff)
}
