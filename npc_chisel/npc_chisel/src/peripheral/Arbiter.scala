package peripheral

import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import chisel3.util.switch
import chisel3.util.is
import chisel3.util.Mux1H
import chisel3.util.RegEnable
import chisel3.util.Queue
import chisel3.util.Irrevocable
import utils.bus.R
import utils.bus.B
import utils.bus.SRAMLike._
import utils.bus.AXIBURST
import chisel3.util.PriorityMux
import os.write

object WriteState extends ChiselEnum {
  val WriteIDLE, WaitWChannel, WaitAWChannel = Value
}
class Arbiter(config: RVConfig, respCacheEnable: Boolean = true)
    extends Module {
  val io = IO(new Bundle {
    val ibus = new Bundle {
      val req = Flipped(Irrevocable(new SRAMLikeReq(config)))
      val resp = Irrevocable(new SRAMLikeRResp(config))
    }
    val dbus = new Bundle {
      val req = Flipped(Irrevocable(new SRAMLikeReq(config)))
      val rResp = Irrevocable(new SRAMLikeRResp(config))
      val wResp = Irrevocable(new SRAMLikeWResp(config))
    }
    val out = AXI4(config, AXI4.MS.asMaster)
  })

  val awReqBusy = RegInit(0.U.asTypeOf(io.out.aw.bits))
  when(io.out.aw.fire) {
    awReqBusy := io.out.aw.bits
  }.elsewhen(io.out.b.valid) {
    awReqBusy := 0.U.asTypeOf(io.out.aw.bits)
  }

  // // 若主机无法及时接收应答, 则进入到FIFO中
  if (respCacheEnable) {
    val rChannelIBus = Wire(Irrevocable(UInt(config.xlen.W)))
    val rChannelIBusQueue = Queue.irrevocable(rChannelIBus, 1, flow = true)
    rChannelIBus.bits <> io.out.r.bits.data
    rChannelIBus.valid := io.out.r.bits.id === 0.U && io.out.r.valid
    rChannelIBusQueue.ready := io.ibus.resp.ready
    io.ibus.resp.valid := rChannelIBusQueue.valid
    io.ibus.resp.bits.rdata := rChannelIBusQueue.bits

    val rChannelDBus = Wire(Irrevocable(UInt(config.xlen.W)))
    val rChannelDBusQueue = Queue.irrevocable(rChannelDBus, 1, flow = true)
    rChannelDBus.bits <> io.out.r.bits.data
    rChannelDBus.valid := io.out.r.bits.id === 1.U && io.out.r.valid
    rChannelDBusQueue.ready := io.dbus.rResp.ready
    io.dbus.rResp.valid := rChannelDBusQueue.valid
    io.dbus.rResp.bits.rdata := rChannelDBusQueue.bits

    val bChannel = Wire(Irrevocable(new B(config)))
    val bChannelQueue = Queue.irrevocable(bChannel, 1, flow = true)
    bChannel <> io.out.b
    bChannelQueue.ready := io.dbus.wResp.ready
    io.dbus.wResp.valid := bChannelQueue.valid
    io.dbus.wResp.bits.resp := bChannelQueue.bits.resp

    io.out.r.ready := Mux(
      io.out.r.bits.id === 0.U,
      rChannelIBus.ready,
      rChannelDBus.ready
    )
    io.out.b.ready := bChannel.ready
  } else {
    io.ibus.resp.valid := io.out.r.valid && io.out.r.bits.id === 0.U
    io.ibus.resp.bits.rdata := io.out.r.bits.data
    io.dbus.rResp.valid := io.out.r.valid && io.out.r.bits.id === 1.U
    io.dbus.rResp.bits.rdata := io.out.r.bits.data
    io.dbus.wResp.valid := io.out.b.valid
    io.dbus.wResp.bits.resp := io.out.b.bits.resp

    val bChannel = Wire(Irrevocable(new B(config)))
    val bChannelQueue = Queue.irrevocable(bChannel, 1, flow = true)
    bChannel <> io.out.b
    bChannelQueue.ready := io.dbus.wResp.ready
    io.dbus.wResp.valid := bChannelQueue.valid
    io.dbus.wResp.bits.resp := bChannelQueue.bits.resp
    io.out.r.ready := Mux(
      io.out.r.bits.id === 0.U,
      io.ibus.resp.ready,
      io.dbus.rResp.ready
    )
    io.out.b.ready := bChannel.ready
  }

  // 当out_arvalid 为高后, 禁止更改请求, 直到ready为高
  val reqLock = RegEnable(false.B, false.B, io.out.ar.ready)
  val selDBus = RegInit(false.B)
  when(io.out.ar.valid && !io.out.ar.ready) {
    reqLock := true.B
    selDBus := Mux(reqLock, selDBus, io.dbus.req.valid && !io.dbus.req.bits.wr)
  }

  // avoid w channel to be handshaked before aw channel, asure the w channel and aw channel is launching the same request
  // val wFired = RegEnable(false.B, false.B, io.dbus.req.fire)
  // val awFired = RegEnable(false.B, false.B, io.dbus.req.fire)
  // when (io.out.w.fire && !wFired && !io.out.aw.fire && !awFired){
  //     wFired := true.B
  // }

  // when (io.out.aw.fire && !awFired && !io.dbus.req.fire && !wFired){
  //     awFired := true.B
  // }

  val writeState = RegInit(WriteState.WriteIDLE)
  val dbusWReady = WireDefault(false.B)
  switch(writeState) {
    is(WriteState.WriteIDLE) {
      when(io.out.aw.fire && io.out.w.fire) {
        dbusWReady := true.B
        writeState := WriteState.WriteIDLE
      }.elsewhen(io.out.aw.fire && !io.out.w.fire) {
        writeState := WriteState.WaitWChannel
      }.elsewhen(!io.out.aw.fire && io.out.w.fire) {
        writeState := WriteState.WaitAWChannel
      }
    }
    is(WriteState.WaitWChannel) {
      when(io.out.w.fire) {
        dbusWReady := true.B
        writeState := WriteState.WriteIDLE
      }
    }
    is(WriteState.WaitAWChannel) {
      when(io.out.aw.fire) {
        dbusWReady := true.B
        writeState := WriteState.WriteIDLE
      }
    }
  }

  // 向外发起写请求
  io.out.w.valid := io.dbus.req.valid && io.dbus.req.bits.wr && writeState =/= WriteState.WaitAWChannel
  io.out.aw.valid := io.dbus.req.valid && io.dbus.req.bits.wr && writeState =/= WriteState.WaitWChannel
  io.out.aw.bits := DontCare
  io.out.aw.bits.addr := io.dbus.req.bits.addr
  io.out.w.bits.data := io.dbus.req.bits.wdata
  io.out.w.bits.strb := io.dbus.req.bits.wstrb
  io.out.w.bits.last := true.B
  io.out.aw.bits.burst := AXIBURST.INCR
  io.out.aw.bits.len := 0.U
  io.out.aw.bits.size := io.dbus.req.bits.size

  io.ibus.req.ready := io.out.ar.ready && ((reqLock && !selDBus) || (!reqLock && !(io.dbus.req.valid && !io.dbus.req.bits.wr)))
  io.dbus.req.ready := Mux(
    io.dbus.req.bits.wr,
    dbusWReady,
    io.out.ar.ready && ((reqLock && selDBus) || (!reqLock && io.out.ar.bits.addr =/= awReqBusy.addr))
  )
  io.out.ar.valid := ((io.dbus.req.valid && !io.dbus.req.bits.wr) || io.ibus.req.valid) && io.out.ar.bits.addr =/= awReqBusy.addr
  io.out.ar.bits.addr := Mux(
    reqLock,
    Mux(selDBus, io.dbus.req.bits.addr, io.ibus.req.bits.addr),
    Mux(
      io.dbus.req.valid && !io.dbus.req.bits.wr,
      io.dbus.req.bits.addr,
      io.ibus.req.bits.addr
    )
  )
  io.out.ar.bits.id := Mux(
    reqLock,
    Mux(selDBus, 1.U, 0.U),
    Mux(io.dbus.req.valid && !io.dbus.req.bits.wr, 1.U, 0.U)
  )
  io.out.ar.bits.burst := AXIBURST.INCR
  io.out.ar.bits.len := Mux(
    reqLock,
    Mux(selDBus, 0.U, io.ibus.req.bits.len),
    Mux(io.dbus.req.valid && !io.dbus.req.bits.wr, 0.U, io.ibus.req.bits.len)
  )
  io.out.ar.bits.cache := DontCare
  io.out.ar.bits.lock := DontCare
  io.out.ar.bits.prot := DontCare
  io.out.ar.bits.qos := DontCare
  io.out.ar.bits.size := Mux(
    reqLock,
    Mux(selDBus, io.dbus.req.bits.size, 2.U),
    Mux(io.dbus.req.valid && !io.dbus.req.bits.wr, io.dbus.req.bits.size, 2.U)
  )
}
