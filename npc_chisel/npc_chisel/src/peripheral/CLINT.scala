package peripheral

import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import chisel3.util.circt.dpi.RawClockedNonVoidFunctionCall
import chisel3.util._
import chisel3.util.circt.dpi.RawUnclockedNonVoidFunctionCall

class CLINT(config: RVConfig) extends Module {
    import peripheral.FSMState.State._
    val io = IO(new Bundle{
        val bus = AXI4(config, AXI4.MS.asSlave)
    })
    val counter0 = RegInit(0.U(32.W))
    val counter1 = RegInit(0.U(32.W))
    counter0 := counter0 + 1.U
    when (counter0.andR){
        counter1 := counter1 + 1.U
    }
    val valid = RegNext(io.bus.ar.fire, false.B)
    
    io.bus.ar.ready := true.B
    io.bus.w  <> DontCare
    io.bus.aw <> DontCare
    if (config.simulation){
        // val rdata = RegInit(0.U(config.xlen.W))
        io.bus.r.bits.data := RawClockedNonVoidFunctionCall(
            "rtc_read",
            UInt(config.xlen.W),
            Option(Seq("raddr")),
            Option("rdata")
        )(
            clock, enable = io.bus.ar.fire, io.bus.ar.bits.addr
        )
        // rdata := RawUnclockedNonVoidFunctionCall(
        //     "rtc_read",
        //     UInt(config.xlen.W),
        //     Option(Seq("raddr")),
        //     Option("rdata")
        // )(
        //     enable = io.bus.ar.fire, io.bus.ar.bits.addr
        // )
        // io.bus.r.bits.data := rdata
    } else {
        val sel = RegNext(io.bus.ar.bits.addr(2))
        io.bus.r.bits.data := Mux(sel, counter1, counter0)
    }
    io.bus.r.valid := valid
    io.bus.b  <> DontCare

    // // AXIFULL
    io.bus.r.bits.resp := 0.U
    io.bus.r.bits.id := 1.U
    io.bus.r.bits.last := true.B
}
