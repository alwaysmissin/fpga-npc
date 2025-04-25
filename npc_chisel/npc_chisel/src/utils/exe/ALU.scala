package utils.exe
import chisel3._
import chisel3.util.switch
import chisel3.util.is
import chisel3.util.Fill
import chisel3.util.Cat
import utils.id.ControlSignals.ALUOp
import chisel3.util.MuxCase
import chisel3.util.Mux1H
import utils.RVConfig
import chisel3.util.Reverse

class Adder(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val opA = Input(UInt(config.xlen.W))
    val opB = Input(UInt(config.xlen.W))
    val out = Output(UInt(config.xlen.W))
  })
  io.out := io.opA + io.opB
}

class ALU(config: RVConfig) extends Module {
  val io = IO(new Bundle {
    val opA = Input(UInt(config.xlen.W))
    val opB = Input(UInt(config.xlen.W))
    val op = Input(UInt(ALUOp.WIDTH.W))
    val res = Output(UInt(config.xlen.W))
    val sum = Output(UInt(config.xlen.W))
  })
  val aluOp = io.op
  val sum = io.opA + Mux(io.op(0), -io.opB, io.opB)
  val cmp =
    Mux(
      io.opA(config.xlen - 1) === io.opB(config.xlen - 1),
      sum(config.xlen - 1),
      Mux(io.op(1), io.opB(config.xlen - 1), io.opA(config.xlen - 1))
    )
  val shamt = io.opB(4, 0).asUInt
  val shin = Mux(io.op(3), io.opA, Reverse(io.opA))
  val shiftr = (Cat(io.op(0) && shin(config.xlen - 1), shin).asSInt >> shamt)(
    config.xlen - 1,
    0
  )
  val shiftl = Reverse(shiftr)
  io.res := Mux1H(
    Seq(
      (aluOp === ALUOp.ADD) -> (sum),
      (aluOp === ALUOp.SUB) -> (sum),
      (aluOp === ALUOp.XOR) -> (io.opA ^ io.opB),
      (aluOp === ALUOp.AND) -> (io.opA & io.opB),
      (aluOp === ALUOp.OR) -> (io.opA | io.opB),
      (aluOp === ALUOp.SLL) -> (shiftl),
      (aluOp === ALUOp.SRL) -> (shiftr),
      (aluOp === ALUOp.SRA) -> (shiftr),
      // (aluOp === ALUOp.MULL) -> ((io.opA * io.opB)(config.xlen - 1, 0)),
      // (aluOp === ALUOp.MULH) -> ((io.opA.asSInt * io.opB.asSInt)(config.xlen * 2 - 1, config.xlen)),
      // (aluOp === ALUOp.MULHU) -> (io.opA * io.opB)(config.xlen * 2 - 1, config.xlen),
      // (aluOp === ALUOp.DIVU) -> (io.opA / io.opB),
      // (aluOp === ALUOp.DIVS) -> (io.opA.asSInt / io.opB.asSInt).asUInt,
      (aluOp === ALUOp.LTU) -> (Cat(0.U((config.xlen - 1).W), cmp)),
      (aluOp === ALUOp.LTS) -> (Cat(0.U((config.xlen - 1).W), cmp))
    )
  )
  io.sum := sum
}
