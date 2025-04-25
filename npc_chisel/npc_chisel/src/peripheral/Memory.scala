package peripheral

import utils.RVConfig
import chisel3._
import chisel3.util.HasBlackBoxResource
// import utils.AR
// import utils.R
// import utils.AW
import utils.bus.AXI4
import chisel3.util.HasBlackBoxInline
import chisel3.experimental.noPrefix
import chisel3.util.circt.dpi.RawClockedNonVoidFunctionCall
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import chisel3.util.Counter
import chisel3.util.switch
import chisel3.util.is
import chisel3.util.random.LFSR
import chisel3.util.SRAM
import chisel3.util.Fill
import chisel3.util.Cat

object FSMState {
  object State extends ChiselEnum {
    val IDLE, READ, WRITE, READRESP, WRITERESP = Value
  }
}

class Memory(config: RVConfig) extends Module {
  import FSMState.State._
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val dbus = AXI4(config, AXI4.MS.asSlave)
  })
  val rdata = RegInit(0.U(config.xlen.W))
  val bresp = RegInit(false.B)

  val readState = RegInit(IDLE)
  val writeState = RegInit(IDLE)
  val rand = Module(new tools.LFSR(8))
  val writeCounter = RegInit(0.U(8.W))
  io.dbus.aw.ready := writeState === IDLE || writeState === WRITERESP
  io.dbus.ar.ready := readState === IDLE || readState === READRESP
  io.dbus.w.ready := writeState === IDLE || writeState === WRITERESP
  io.dbus.b.valid := writeState === WRITERESP
  io.dbus.r.valid := readState === READRESP

  switch(writeState) {
    is(IDLE) {
      writeCounter := 2.U
      when(io.dbus.aw.valid && io.dbus.w.valid) {
        writeState := WRITE
      }
    }
    is(WRITE) {
      writeCounter := writeCounter - 1.U
      when(writeCounter === 1.U) {
        writeState := WRITERESP
      }
    }
    is(WRITERESP) {
      writeCounter := 2.U
      when(io.dbus.b.ready) {
        when(io.dbus.aw.valid && io.dbus.w.valid) {
          writeState := WRITE
        }.otherwise {
          writeState := IDLE
        }
      }
    }
  }

  val readCounter = RegInit(0.U(8.W))
  switch(readState) {
    is(IDLE) {
      readCounter := 2.U
      when(io.dbus.ar.valid) {
        readState := READ
      }
    }
    is(READ) {
      readCounter := readCounter - 1.U
      when(readCounter === 1.U) {
        readState := READRESP
      }
    }
    is(READRESP) {
      readCounter := 2.U
      when(io.dbus.r.ready) {
        when(io.dbus.ar.valid) {
          readState := READ
        }.otherwise {
          readState := IDLE
        }
      }
    }
  }

  if (config.simulation) {
    rdata := RawClockedNonVoidFunctionCall(
      "pmem_read",
      UInt(config.xlen.W),
      Option(Seq("raddr")),
      Option("rdata")
    )(
      io.clk,
      enable = io.dbus.ar.fire,
      io.dbus.ar.bits.addr
    )

    bresp := RawClockedNonVoidFunctionCall(
      "pmem_write",
      Bool(),
      Option(Seq("waddr", "wdata", "wmask")),
      Option("bresp")
    )(
      io.clk,
      enable = io.dbus.aw.fire,
      io.dbus.aw.bits.addr,
      io.dbus.w.bits.data,
      Cat("b0000".U(4.W), io.dbus.w.bits.strb)
    )
  } else {
    val mem = SyncReadMem(config.memorySize, Vec(4, UInt(8.W)))
    when(io.dbus.ar.fire) {
      rdata := mem.read(io.dbus.ar.bits.addr).asUInt
    }
    val wmask = Seq(
      io.dbus.w.bits.strb(0).asBool,
      io.dbus.w.bits.strb(1).asBool,
      io.dbus.w.bits.strb(2).asBool,
      io.dbus.w.bits.strb(3).asBool
    )
    val wdata = Wire(Vec(4, UInt(8.W)))
    wdata(0) := io.dbus.w.bits.data(7, 0)
    wdata(1) := io.dbus.w.bits.data(15, 8)
    wdata(2) := io.dbus.w.bits.data(23, 16)
    wdata(3) := io.dbus.w.bits.data(31, 24)
    when(io.dbus.aw.fire && io.dbus.w.fire) {
      mem.write(io.dbus.aw.bits.addr, wdata, wmask, io.clk)
    }
    io.dbus.b.bits.resp := true.B
  }

  io.dbus.r.bits.data := rdata
  io.dbus.b.bits.resp := bresp

  // AXIFULL
  io.dbus.b.bits.id := 0.U
  io.dbus.r.bits.resp := 0.U
  io.dbus.r.bits.id := 0.U
  io.dbus.r.bits.last := 0.U
}
