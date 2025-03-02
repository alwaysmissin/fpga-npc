package peripheral

import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import chisel3.util.circt.dpi.RawClockedNonVoidFunctionCall

class UART(config: RVConfig) extends Module {
    val io = IO(new Bundle{
        val clk = Input(Clock())
        val bus = AXI4(config, AXI4.MS.asSlave)
    })

    val rdata = RegInit(0.U(config.xlen.W))
    io.bus.ar.ready := true.B
    io.bus.r.valid := true.B
    val bresp = RegInit(false.B)
    io.bus.aw.ready := true.B
    io.bus.w.ready := true.B
    io.bus.b.valid := true.B
    if (config.simulation){
        rdata := RawClockedNonVoidFunctionCall(
            "serial_read",
            UInt(config.xlen.W),
            Option(Seq("raddr")),
            Option("rdata")
        )(
            io.clk, enable = io.bus.ar.fire, io.bus.ar.bits.addr
        )

        bresp := RawClockedNonVoidFunctionCall(
            "serial_write",
            Bool(),
            Option(Seq("waddr", "wdata", "wmask")),
            Option("bresp")
        )(
            io.clk,
            enable = io.bus.aw.fire && io.bus.w.fire,
            io.bus.aw.bits.addr,
            io.bus.w.bits.data,
            io.bus.w.bits.strb
        )
    }
    io.bus.r.bits.data := rdata
    io.bus.b.bits.resp := bresp
}
