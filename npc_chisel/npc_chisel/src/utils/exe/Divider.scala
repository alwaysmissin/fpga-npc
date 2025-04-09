package utils.exe

import chisel3._
import chisel3.util._
import utils.RVConfig
import utils.Config.FPGAPlatform

class DividerFPGA(dataWidth: Int = 32) extends BlackBox{
  override val desiredName = "divider"
  val io = IO(new Bundle {
    val aclk = Clock()
    val s_axis_divisor_tvalid = Input(Bool())
    val s_axis_divisor_tdata = Input(UInt(dataWidth.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tdata = Input(UInt(dataWidth.W))
    val m_axis_dout_tvalid = Output(Bool())
    val m_axis_dout_tdata = Output(UInt((2 * dataWidth).W))
  })
}

class DividerSim(dataWidth: Int = 32) extends Module{
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
      io.s_axis_dividend_tdata % io.s_axis_divisor_tdata)
}

class Divider(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Irrevocable(new Bundle {
      val divisor = UInt(config.xlen.W)
      val dividend = UInt(config.xlen.W)
    }))
    val resp = Irrevocable(new Bundle {
      val quotient = UInt(config.xlen.W)
      val remainder = UInt(config.xlen.W)
    })
  })
  if (FPGAPlatform) {
    val divider = Module(new DividerFPGA())
    divider.io.aclk <> clock
    divider.io.s_axis_divisor_tvalid <> io.req.valid
    divider.io.s_axis_divisor_tdata <> io.req.bits.divisor
    divider.io.s_axis_dividend_tvalid <> io.req.valid
    divider.io.s_axis_dividend_tdata <> io.req.bits.dividend
    io.resp.bits.quotient <> divider.io.m_axis_dout_tdata(2 * config.xlen - 1, config.xlen)
    io.resp.bits.remainder <> divider.io.m_axis_dout_tdata(config.xlen - 1, 0)
  } else {
    val divider = Module(new DividerSim())
    divider.io.s_axis_divisor_tvalid <> io.req.valid
    divider.io.s_axis_divisor_tdata <> io.req.bits.divisor
    divider.io.s_axis_dividend_tvalid <> io.req.valid
    divider.io.s_axis_dividend_tdata <> io.req.bits.dividend
    io.req.ready <> true.B
    io.resp.valid <> true.B
    io.resp.bits.quotient <> divider.io.m_axis_dout_tdata(2 * config.xlen - 1, config.xlen)
    io.resp.bits.remainder <> divider.io.m_axis_dout_tdata(config.xlen - 1, 0)
  }
  
}
