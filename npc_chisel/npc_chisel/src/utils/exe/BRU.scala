package utils.exe

import chisel3._
import utils.RVConfig
import utils.id.ControlSignals.BRUOp
import chisel3.util.Mux1H
import chisel3.util.Fill
import chisel3.util.Cat

class BRU(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val bruOp = Input(UInt(BRUOp.WIDTH.W))
    val opA = Input(UInt(config.xlen.W))
    val opB = Input(UInt(config.xlen.W))
    val branch = Output(Bool())
  })

  // val diff = io.opA - io.opB
  // val neq = diff.orR
  // val eq  = !neq
  // val isSameSign = io.opA(config.xlen - 1) === io.opB(config.xlen - 1)
  // val lt = Mux(isSameSign, diff(config.xlen - 1), io.opA(config.xlen - 1))
  // val ltu = Mux(isSameSign, diff(config.xlen - 1), io.opB(config.xlen - 1))
  // val ge = !lt
  // val geu = !ltu
  // io.branch :=
  //     (io.bruOp === BRUOp.EQ && eq) ||
  //     (io.bruOp === BRUOp.NEQ && neq) ||
  //     (io.bruOp === BRUOp.LTU && ltu) ||
  //     (io.bruOp === BRUOp.GEU && geu) ||
  //     (io.bruOp === BRUOp.LTS && lt) ||
  //     (io.bruOp === BRUOp.GES && ge)

  io.branch := Mux1H(
    Seq(
      (io.bruOp === BRUOp.LTU) -> (io.opA < io.opB),
      (io.bruOp === BRUOp.GEU) -> (io.opA >= io.opB),
      (io.bruOp === BRUOp.LTS) -> (io.opA.asSInt < io.opB.asSInt),
      (io.bruOp === BRUOp.GES) -> (io.opA.asSInt >= io.opB.asSInt),
      (io.bruOp === BRUOp.EQ) -> (io.opA === io.opB),
      (io.bruOp === BRUOp.NEQ) -> (io.opA =/= io.opB)
    )
  )
}
