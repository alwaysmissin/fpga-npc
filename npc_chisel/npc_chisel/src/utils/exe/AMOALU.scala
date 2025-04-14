package utils.exe

import chisel3._
import utils.RVConfig
import utils.id.ControlSignals.AMOOp
import chisel3.util._

class AMOALU(config: RVConfig) extends Module {
  val io = IO(new Bundle{
    val mRead = Input(UInt(config.xlen.W))
    val src2  = Input(UInt(config.xlen.W))
    val amoOp = Input(UInt(AMOOp.WIDTH.W))
    val res   = Output(UInt(config.xlen.W))
  })
  io.res := Mux1H(Seq(
    (io.amoOp === AMOOp.SWAP) -> (io.src2),
    (io.amoOp === AMOOp.ADD ) -> (io.mRead + io.src2),
    (io.amoOp === AMOOp.XOR ) -> (io.src2 ^ io.mRead),
    (io.amoOp === AMOOp.AND ) -> (io.src2 & io.mRead),
    (io.amoOp === AMOOp.OR  ) -> (io.src2 | io.mRead),
    (io.amoOp === AMOOp.MAX ) -> (Mux(io.mRead.asSInt > io.src2.asSInt, io.mRead, io.src2)),
    (io.amoOp === AMOOp.MAXU) -> (Mux(io.mRead > io.src2, io.mRead, io.src2)),
    (io.amoOp === AMOOp.MIN ) -> (Mux(io.mRead.asSInt < io.src2.asSInt, io.mRead, io.src2)),
    (io.amoOp === AMOOp.MINU) -> (Mux(io.mRead < io.src2, io.mRead, io.src2)),
  ))
}
