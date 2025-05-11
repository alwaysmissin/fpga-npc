package utils.exe

import chisel3._
import chisel3.util._
import utils.RVConfig
import utils.Config.FPGAPlatform

class DividerFPGA(dataWidth: Int = 32, latency: Int = 0) extends BlackBox {
  override val desiredName = "divider"
  val io = IO(new Bundle {
    val aclk = Input(Bool())
    val s_axis_divisor_tvalid = Input(Bool())
    val s_axis_divisor_tdata = Input(UInt(dataWidth.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tdata = Input(UInt(dataWidth.W))
    val m_axis_dout_tvalid = Output(Bool())
    val m_axis_dout_tdata = Output(UInt((2 * dataWidth).W))
  })
}

class DividerSim(dataWidth: Int = 32, latency: Int = 0) extends Module {
  val io = IO(new Bundle {
    val s_axis_divisor_tvalid = Input(Bool())
    val s_axis_divisor_tdata = Input(UInt(dataWidth.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tdata = Input(UInt(dataWidth.W))
    val m_axis_dout_tvalid = Output(Bool())
    val m_axis_dout_tdata = Output(UInt((2 * dataWidth).W))
  })
  io.m_axis_dout_tvalid := true.B
  io.m_axis_dout_tdata := Cat(
    io.s_axis_dividend_tdata / io.s_axis_divisor_tdata,
    io.s_axis_dividend_tdata % io.s_axis_divisor_tdata
  )
}

class Divider(config: RVConfig, latency: Int = 0) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Irrevocable(new Bundle {
      val divisor = UInt(config.xlen.W)
      val dividend = UInt(config.xlen.W)
    }))
    val resp = Irrevocable(new Bundle {
      val quotient = UInt(config.xlen.W)
      val remainder = UInt(config.xlen.W)
    })
    val busy = Output(Bool())
  })
  val busy = RegInit(false.B)
  io.busy := busy
  val count = RegInit(0.U(log2Ceil(latency + 1).W))
  if (FPGAPlatform) {
    val divider = Module(new DividerFPGA(latency = latency))
    divider.io.aclk := clock.asBool
    divider.io.s_axis_divisor_tvalid <> io.req.valid
    divider.io.s_axis_divisor_tdata <> io.req.bits.divisor
    divider.io.s_axis_dividend_tvalid <> io.req.valid
    divider.io.s_axis_dividend_tdata <> io.req.bits.dividend
    when(io.req.fire) {
      busy := true.B
      count := (latency - 1).U
    }
    when(busy && count =/= 0.U) {
      busy := false.B
    }
    io.req.ready := !busy
    io.resp.valid <> divider.io.m_axis_dout_tvalid
    io.resp.bits.quotient <> divider.io.m_axis_dout_tdata(
      2 * config.xlen - 1,
      config.xlen
    )
    io.resp.bits.remainder <> divider.io.m_axis_dout_tdata(config.xlen - 1, 0)
  } else {
    val quotient = Reg(UInt(config.xlen.W))
    val remainder = Reg(UInt(config.xlen.W))
    when(io.req.fire) {
      quotient := io.req.bits.dividend / io.req.bits.divisor
      remainder := io.req.bits.dividend % io.req.bits.divisor
      busy := true.B
      count := (latency - 1).U
    }

    when(busy && count =/= 0.U) {
      count := count - 1.U
    }

    val done = busy && (count === 0.U)
    io.resp.valid := done
    io.resp.bits.quotient := quotient
    io.resp.bits.remainder := remainder
    when(io.resp.fire) {
      busy := false.B
    }
    io.req.ready := !busy
  }

}
