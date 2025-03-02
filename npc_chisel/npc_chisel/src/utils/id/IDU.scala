package utils.id
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{EspressoMinimizer, TruthTable, decoder}
import ControlSignals._
import Instructions._
import utils.RVConfig

object ControlLogic{
  val default: Seq[BitPat] = Seq(InstType.X, FuType.X, OpASrc.X, OpBSrc.X, ALUOp.X, BRUOp.X, CSROp.X, MemWrite.X, MemRead.X , MemSignExt.X          , CSRWrite.X, RegWrite.X, FENCE.X, Legal.N)
  val table = TruthTable(Map(
    LUI   -> Seq(InstType.U, FuType.ALU, OpASrc.ZERO, OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    AUIPC -> Seq(InstType.U, FuType.ALU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    JAL   -> Seq(InstType.J, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    JALR  -> Seq(InstType.I, FuType.BRU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    BEQ   -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.EQ , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    BNE   -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.NEQ, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    BLT   -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.LTS, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    BGE   -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.GES, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    BLTU  -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.LTU, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    BGEU  -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM, ALUOp.ADD  , BRUOp.GEU, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    LB    -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.B , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    LH    -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.H , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    LW    -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    LBU   -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.B , MemSignExt.UnsignedExt, CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    LHU   -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.H , MemSignExt.UnsignedExt, CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SB    -> Seq(InstType.S, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.B, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    SH    -> Seq(InstType.S, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.H, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    SW    -> Seq(InstType.S, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.W, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    ADDI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SLTI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.LTS  , BRUOp.LTS, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SLTIU -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.LTU  , BRUOp.LTU, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    XORI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.XOR  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    ORI   -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.OR   , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    ANDI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.AND  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SLLI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.SLL  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SRLI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.SRL  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SRAI  -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM, ALUOp.SRA  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    ADD   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.ADD  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SUB   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.SUB  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SLL   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.SLL  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SLT   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.LTS  , BRUOp.LTS, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SLTU  -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.LTU  , BRUOp.LTU, CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    XOR   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.XOR  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SRL   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.SRL  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    SRA   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.SRA  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    OR    -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.OR   , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    AND   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.AND  , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    // MUL   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.ADD  , BRUOp.N  , CSROp.N , NextPCSrc.PC4   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    // MULH  -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.ADD  , BRUOp.N  , CSROp.N , NextPCSrc.PC4   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    // MULHU -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.ADD  , BRUOp.N  , CSROp.N , NextPCSrc.PC4   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    // DIV   -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.ADD  , BRUOp.N  , CSROp.N , NextPCSrc.PC4   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    // DIVU  -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2, ALUOp.ADD  , BRUOp.N  , CSROp.N , NextPCSrc.PC4   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, FENCE.N, Legal.Y),
    CSRRW -> Seq(InstType.I, FuType.CSR, OpASrc.RS1 , OpBSrc.N  , ALUOp.ADD  , BRUOp.N  , CSROp.RW, MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, FENCE.N, Legal.Y),
    CSRRC -> Seq(InstType.I, FuType.CSR, OpASrc.RS1 , OpBSrc.CSR, ALUOp.ADD  , BRUOp.N  , CSROp.RC, MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, FENCE.N, Legal.Y),
    CSRRS -> Seq(InstType.I, FuType.CSR, OpASrc.RS1 , OpBSrc.CSR, ALUOp.ADD  , BRUOp.N  , CSROp.RS, MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, FENCE.N, Legal.Y),
    EBREAK-> Seq(InstType.N, FuType.ALU, OpASrc.X   , OpBSrc.X  , ALUOp.X    , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    ECALL -> Seq(InstType.N, FuType.CSR, OpASrc.ZERO, OpBSrc.CSR, ALUOp.X    , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    MRET  -> Seq(InstType.N, FuType.CSR, OpASrc.ZERO, OpBSrc.CSR, ALUOp.X    , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y),
    FENCEI-> Seq(InstType.N, FuType.ALU, OpASrc.X   , OpBSrc.X  , ALUOp.X    , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.Y, Legal.Y),
    NOP   -> Seq(InstType.X, FuType.ALU, OpASrc.X   , OpBSrc.X  , ALUOp.X    , BRUOp.N  , CSROp.N , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, FENCE.N, Legal.Y)
  ).map({case(k, v) => k -> v.reduce(_ ## _)}), default.reduce(_ ## _))
}

class ImmGen extends Module{
  val io = IO(new Bundle{
    val inst = Input(UInt(32.W))
    val instType = Input(UInt(InstType.WIDTH.W))
    val imm  = Output(UInt(32.W))
  })
  io.imm := Mux1H(Seq(
    (io.instType === InstType.I) -> Cat(Fill(21, io.inst(31)), io.inst(30, 20)),
    (io.instType === InstType.S) -> Cat(Fill(21, io.inst(31)), io.inst(30, 25), io.inst(11, 7)),
    (io.instType === InstType.B) -> Cat(Fill(20, io.inst(31)), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W)),
    (io.instType === InstType.U) -> Cat(io.inst(31, 12), Fill(12, 0.U(1.W))),
    (io.instType === InstType.J) -> Cat(Fill(12, io.inst(31)), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))
  ))
}

class DecodeBundle extends Bundle{
    val aluSrc1 = Output(UInt(OpASrc.WIDTH.W))
    val aluSrc2 = Output(UInt(OpBSrc.WIDTH.W))
    val aluOp = Output(UInt(ALUOp.WIDTH.W))
    val bruOp = Output(UInt(BRUOp.WIDTH.W))
    val csrOp = Output(UInt(CSROp.WIDTH.W))
    val fuType = Output(UInt(FuType.WIDTH.W))
    val memWrite = Output(UInt(MemWrite.WIDTH.W))
    val memRead = Output(UInt(MemRead.WIDTH.W))
    val memSignExt = Output(Bool())
    val csrWrite = Output(UInt(CSRWrite.WIDTH.W))
    val regWrite = Output(Bool())
    val fencei = Output(Bool())
}

class IDU(config: RVConfig) extends Module{
  val io = IO(new Bundle{
    val inst = Input(UInt(config.xlen.W))
    val imm = Output(UInt(config.xlen.W))
    val decodeBundle = new DecodeBundle
    val legal = Output(Bool())
  })
  val ctrlWord: UInt = decoder(minimizer = EspressoMinimizer, input = io.inst, truthTable = ControlLogic.table)
  val ctrlWordWidth = ctrlWord.getWidth
  var ctrlOffset = 0

  val instType = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - InstType.WIDTH)
  ctrlOffset += InstType.WIDTH
  val fuType = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - FuType.WIDTH)
  ctrlOffset += FuType.WIDTH
  val aluSrc1 = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - OpASrc.WIDTH)
  ctrlOffset += OpASrc.WIDTH
  val aluSrc2 = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - OpBSrc.WIDTH)
  ctrlOffset += OpBSrc.WIDTH
  val aluOp = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - ALUOp.WIDTH)
  ctrlOffset += ALUOp.WIDTH
  val bruOp = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - BRUOp.WIDTH)
  ctrlOffset += BRUOp.WIDTH
  val csrOp = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - CSROp.WIDTH)
  ctrlOffset += CSROp.WIDTH
  val memWrite = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MemWrite.WIDTH)
  ctrlOffset += MemWrite.WIDTH
  val memRead = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MemRead.WIDTH)
  ctrlOffset += MemRead.WIDTH
  val memSignExt = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MemSignExt.WIDTH)
  ctrlOffset += MemSignExt.WIDTH
  val csrWrite = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - CSRWrite.WIDTH)
  ctrlOffset += CSRWrite.WIDTH
  val regWrite = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - RegWrite.WIDTH)
  ctrlOffset += RegWrite.WIDTH
  val fencei = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - FENCE.WIDTH)
  ctrlOffset += FENCE.WIDTH
  io.legal := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - Legal.WIDTH)(0)


  // get immediate value from immExt
  val immExt = Module(new ImmGen)
  immExt.io.inst <> io.inst
  immExt.io.instType <> instType
  immExt.io.imm <> io.imm

  io.decodeBundle.aluSrc1 <> aluSrc1
  io.decodeBundle.aluSrc2 <> aluSrc2
  io.decodeBundle.fuType <> fuType
  io.decodeBundle.aluOp <> aluOp
  io.decodeBundle.bruOp <> bruOp
  io.decodeBundle.csrOp <> csrOp
  io.decodeBundle.memWrite <> memWrite
  io.decodeBundle.memRead <> memRead
  io.decodeBundle.memSignExt <> memSignExt(0)
  io.decodeBundle.csrWrite <> csrWrite
  io.decodeBundle.regWrite <> regWrite(0)
  io.decodeBundle.fencei <> fencei
}
