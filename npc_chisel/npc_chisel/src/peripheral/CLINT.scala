package peripheral

import chisel3._
import utils.RVConfig
import utils.bus.AXI4
import chisel3.util.circt.dpi.RawClockedNonVoidFunctionCall
import chisel3.util._
import chisel3.util.circt.dpi.RawUnclockedNonVoidFunctionCall
import utils.Config.FPGAPlatform

class CLINT(config: RVConfig, tickCount: Int = 50) extends Module {
  import peripheral.FSMState.State._
  val io = IO(new Bundle {
    val bus = AXI4(config, AXI4.MS.asSlave)
    val msip = Output(Bool())
    val mtip = Output(Bool())
  })
  val msip = RegInit(0.U(32.W))
  val mtimecmp = RegInit("hffff_ffff_ffff_ffff".U(64.W))
  val mtime = RegInit(0.U(64.W))
  val incFlag = WireDefault(true.B)
  // if (FPGAPlatform) {
  //   val tickCounter = RegInit(0.U(log2Up(tickCount).W))
  //   tickCounter := Mux(
  //     tickCounter === (tickCount - 1).U,
  //     0.U,
  //     tickCounter + 1.U
  //   )
  //   incFlag := tickCounter === (tickCount - 1).U
  // } else {
  //   incFlag := true.B
  // }

  mtime := mtime + incFlag

  io.bus.ar.ready := true.B
  io.bus.w.ready := true.B
  io.bus.aw.ready := true.B

  // do read
  val raddr = io.bus.ar.bits.addr(15, 0)
  val rdata = RegNext(
    Mux1H(
      Seq(
        (raddr === 0x0000.U) -> msip,
        (raddr === 0x4000.U) -> mtimecmp(31, 0),
        (raddr === 0x4004.U) -> mtimecmp(63, 32),
        (raddr === 0xbff8.U) -> mtime(31, 0),
        (raddr === 0xbffc.U) -> mtime(63, 32)
      )
    )
  )

  // do write
  val waddr = io.bus.aw.bits.addr(15, 0)
  val wdata = io.bus.w.bits.data
  when(io.bus.aw.fire && io.bus.w.fire) {
    switch(waddr) {
      is(0x0000.U) {
        msip := wdata
      }
      is(0x4000.U) {
        mtimecmp := Cat(mtimecmp(63, 32), wdata)
      }
      is(0x4004.U) {
        mtimecmp := Cat(wdata, mtimecmp(31, 0))
      }
      is(0xbff8.U) {
        mtime := Cat(mtime(63, 32), wdata)
      }
      is(0xbffc.U) {
        mtime := Cat(wdata, mtime(31, 0))
      }
    }
  }

  io.bus.r.bits.data := rdata
  io.bus.r.valid := RegNext(io.bus.ar.fire)
  io.bus.b.valid := RegNext(io.bus.aw.fire && io.bus.w.fire)

  // // AXIFULL
  io.bus.r.bits.resp := 0.U
  io.bus.r.bits.id := 1.U
  io.bus.r.bits.last := true.B
  io.bus.b.bits.resp := 0.U
  io.bus.b.bits.id := 1.U

  io.msip := RegNext(msip(0))
  io.mtip := RegNext(mtimecmp < mtime)
}
