package peripheral

import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import chisel3.util.RegEnable
import chisel3.util.Mux1H
import chisel3.util.Irrevocable
import utils.bus.SRAMLike._
import chisel3.util.PriorityMux
import utils.csr.InterruptSimple

object XBarConfig {
  def RTC_L = "h2004_0000".U
  def RTC_H = "h2004_ffff".U
  def PLIC_L = "h4000_0000".U
  def PLIC_H = "h43ff_ffff".U
}

class XBar(config: RVConfig, nrIntr: Int) extends Module {
  val io = IO(new Bundle {
    // val in = AXILite(config, AXILite.MS.asSlave)
    // val ibus = AXI4(config, AXI4.MS.asSlave)
    // val dbus = AXI4(config, AXI4.MS.asSlave)
    val ibus = new Bundle {
      val req = Flipped(Irrevocable(new SRAMLikeReq(config)))
      val resp = Irrevocable(new SRAMLikeRResp(config))
    }
    val dbus = new Bundle {
      val req = Flipped(Irrevocable(new SRAMLikeReq(config)))
      val rResp = Irrevocable(new SRAMLikeRResp(config))
      val wResp = Irrevocable(new SRAMLikeWResp(config))
    }
    val toMem = AXI4(config)
    val intrVec = Input(UInt(nrIntr.W))
    val interrupt = Output(new InterruptSimple())
  })
  val arbiter = Module(new Arbiter(config, respCacheEnable = true))
  arbiter.io.ibus <> io.ibus
  arbiter.io.dbus <> io.dbus
  // arbiter.io.out <> io.toMem

  val clint = Module(new CLINT(config))
  val plic = Module(new PLIC(config, nrIntr))
  io.interrupt.tip <> clint.io.mtip
  io.interrupt.sip <> clint.io.msip
  io.interrupt.eip <> plic.io.meip
  io.intrVec <> plic.io.intrVec

  when(io.dbus.req.valid && io.dbus.req.bits.wr) {
    arbiter.io.dbus.req <> io.dbus.req
  }

  // TODO： 使用FIFO去记录事务
  val read_sel_clint = Wire(Bool())
  val read_sel_plic = Wire(Bool())
  val read_sel_mem = Wire(Bool())
  val write_sel_clint = Wire(Bool())
  val write_sel_plic = Wire(Bool())
  val write_sel_mem = Wire(Bool())
  read_sel_clint := arbiter.io.out.ar.bits.addr < XBarConfig.RTC_H && arbiter.io.out.ar.bits.addr >= XBarConfig.RTC_L
  read_sel_plic := arbiter.io.out.ar.bits.addr < XBarConfig.PLIC_H && arbiter.io.out.ar.bits.addr >= XBarConfig.PLIC_L
  read_sel_mem := !read_sel_clint && !read_sel_plic
  write_sel_clint := arbiter.io.out.aw.bits.addr < XBarConfig.RTC_H && arbiter.io.out.aw.bits.addr >= XBarConfig.RTC_L
  write_sel_plic := arbiter.io.out.aw.bits.addr < XBarConfig.PLIC_H && arbiter.io.out.aw.bits.addr >= XBarConfig.PLIC_L
  write_sel_mem := !write_sel_clint && !write_sel_plic

  // read transaction
  io.toMem.ar.valid := arbiter.io.out.ar.valid & read_sel_mem
  io.toMem.ar.bits := arbiter.io.out.ar.bits
  clint.io.bus.ar.valid := arbiter.io.out.ar.valid & read_sel_clint
  clint.io.bus.ar.bits := arbiter.io.out.ar.bits
  plic.io.bus.ar.valid := arbiter.io.out.ar.valid & read_sel_plic
  plic.io.bus.ar.bits := arbiter.io.out.ar.bits
  io.toMem.r.ready := arbiter.io.out.r.ready
  clint.io.bus.r.ready := arbiter.io.out.r.ready
  plic.io.bus.r.ready := arbiter.io.out.r.ready

  // write transaction
  io.toMem.aw.valid := arbiter.io.out.aw.valid & write_sel_mem
  io.toMem.aw.bits := arbiter.io.out.aw.bits
  clint.io.bus.aw.valid := arbiter.io.out.aw.valid & write_sel_clint
  clint.io.bus.aw.bits := arbiter.io.out.aw.bits
  plic.io.bus.aw.valid := arbiter.io.out.aw.valid & write_sel_plic
  plic.io.bus.aw.bits := arbiter.io.out.aw.bits
  io.toMem.w.valid := arbiter.io.out.w.valid & write_sel_mem
  io.toMem.w.bits := arbiter.io.out.w.bits
  clint.io.bus.w.valid := arbiter.io.out.w.valid & write_sel_clint
  clint.io.bus.w.bits := arbiter.io.out.w.bits
  plic.io.bus.w.valid := arbiter.io.out.w.valid & write_sel_plic
  plic.io.bus.w.bits := arbiter.io.out.w.bits
  io.toMem.b.ready := arbiter.io.out.b.ready
  clint.io.bus.b.ready := arbiter.io.out.b.ready
  plic.io.bus.b.ready := arbiter.io.out.b.ready

  // arbiter.io.out.ar.ready := Mux(
  //   read_sel_mem,
  //   io.toMem.ar.ready,
  //   clint.io.bus.ar.ready
  // )
  arbiter.io.out.ar.ready := PriorityMux(
    Seq(
      read_sel_mem -> io.toMem.ar.ready,
      read_sel_clint -> clint.io.bus.ar.ready,
      read_sel_plic -> plic.io.bus.ar.ready
    )
  )
  arbiter.io.out.r.valid := io.toMem.r.valid || clint.io.bus.r.valid || plic.io.bus.r.valid
  arbiter.io.out.r.bits := PriorityMux(
    Seq(
      io.toMem.r.valid -> io.toMem.r.bits,
      clint.io.bus.r.valid -> clint.io.bus.r.bits,
      plic.io.bus.r.valid -> plic.io.bus.r.bits
    )
  )

  // arbiter.io.out.aw.ready := Mux(
  //   write_sel_mem,
  //   io.toMem.aw.ready,
  //   clint.io.bus.aw.ready
  // )
  // arbiter.io.out.w.ready := Mux(
  //   write_sel_mem,
  //   io.toMem.w.ready,
  //   clint.io.bus.w.ready
  // )
  arbiter.io.out.aw.ready := PriorityMux(
    Seq(
      write_sel_mem -> io.toMem.aw.ready,
      write_sel_clint -> clint.io.bus.aw.ready,
      write_sel_plic -> plic.io.bus.aw.ready
    )
  )
  arbiter.io.out.w.ready := PriorityMux(
    Seq(
      write_sel_mem -> io.toMem.w.ready,
      write_sel_clint -> clint.io.bus.w.ready,
      write_sel_plic -> plic.io.bus.w.ready
    )
  )
  arbiter.io.out.b.valid := io.toMem.b.valid || clint.io.bus.b.valid || plic.io.bus.b.valid
  arbiter.io.out.b.bits := PriorityMux(
    Seq(
      io.toMem.b.valid -> io.toMem.b.bits,
      clint.io.bus.b.valid -> clint.io.bus.b.bits,
      plic.io.bus.b.valid -> plic.io.bus.b.bits
    )
  )

}
