package peripheral

import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import chisel3.util.RegEnable
import chisel3.util.Mux1H
import chisel3.util.Irrevocable
import utils.bus.SRAMLike._

object XBarConfig {
    def MASK = "he000_0000".U
    def MEM  = "h8000_0000".U
    def PERI = "ha000_0000".U
    def RTC_L = "h0200_0000".U
    def RTC_H = "h0200_ffff".U
}

class XBar(config: RVConfig) extends Module {
    val io = IO(new Bundle{
        // val in = AXILite(config, AXILite.MS.asSlave)
        // val ibus = AXI4(config, AXI4.MS.asSlave)
        // val dbus = AXI4(config, AXI4.MS.asSlave)
        val ibus = new Bundle{
            val req = Flipped(Irrevocable(new SRAMLikeReq(config)))
            val resp = Irrevocable(new SRAMLikeRResp(config))
        }
        val dbus = new Bundle{
            val req = Flipped(Irrevocable(new SRAMLikeReq(config)))
            val rResp = Irrevocable(new SRAMLikeRResp(config))
            val wResp = Irrevocable(new SRAMLikeWResp(config))
        }
        val toMem = AXI4(config)
    })
    val arbiter = Module(new Arbiter(config, respCacheEnable = false))
    arbiter.io.ibus <> io.ibus
    arbiter.io.dbus <> io.dbus
    // arbiter.io.out <> io.toMem

    val clint = Module(new CLINT(config))
    clint.io.bus.aw <> DontCare
    clint.io.bus.w  <> DontCare
    clint.io.bus.b  <> DontCare
    
    when (io.dbus.req.valid && io.dbus.req.bits.wr){
        arbiter.io.dbus.req <> io.dbus.req
    }
    io.toMem.aw <> arbiter.io.out.aw
    io.toMem.w  <> arbiter.io.out.w
    io.toMem.b  <> arbiter.io.out.b

    val read_sel_rtc  = arbiter.io.out.ar.bits.addr < XBarConfig.RTC_H && arbiter.io.out.ar.bits.addr >= XBarConfig.RTC_L
    val read_sel_mem  = !read_sel_rtc
    val read_sel_rtc_r  = RegEnable(read_sel_rtc , false.B, arbiter.io.out.ar.valid)
    val read_sel_mem_r  = RegEnable(read_sel_mem , false.B, arbiter.io.out.ar.valid)

    io.toMem.ar.valid := arbiter.io.out.ar.valid & read_sel_mem
    io.toMem.ar.bits  := arbiter.io.out.ar.bits
    clint.io.bus.ar.valid := arbiter.io.out.ar.valid & read_sel_rtc
    clint.io.bus.ar.bits := arbiter.io.out.ar.bits
    io.toMem.r.ready := arbiter.io.out.r.ready & read_sel_mem_r
    clint.io.bus.r.ready := arbiter.io.out.r.ready & read_sel_rtc_r

    // clint.io.bus.aw <> DontCare
    // clint.io.bus.w  <> DontCare
    // clint.io.bus.b  <> DontCare


    arbiter.io.out.ar.ready := Mux(read_sel_mem, io.toMem.ar.ready, clint.io.bus.ar.ready)
    arbiter.io.out.r.valid := Mux1H(Seq(
        read_sel_mem_r  -> io.toMem.r.valid,
        read_sel_rtc_r  -> clint.io.bus.r.valid
    ))
    arbiter.io.out.r.bits := Mux1H(Seq(
        read_sel_mem_r  -> io.toMem.r.bits,
        read_sel_rtc_r  -> clint.io.bus.r.bits
    ))

}
