package utils.exe

import chisel3._
import utils.RVConfig
import utils.id.ControlSignals.CSROp
import chisel3.util.Mux1H

class CSRU(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val opA = Input(UInt(config.xlen.W))
    val opB = Input(UInt(config.xlen.W))
    val op = Input(UInt(CSROp.WIDTH.W))
    val res = Output(UInt(config.xlen.W))
  })

  val csrOp = io.op
  io.res := Mux1H(
    Seq(
      (csrOp === CSROp.RW) -> (io.opA),
      (csrOp === CSROp.RC) -> (~io.opA & io.opB),
      (csrOp === CSROp.RS) -> (io.opA | io.opB)
    )
  )
}
