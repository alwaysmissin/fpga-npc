package utils.id
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{EspressoMinimizer, TruthTable, decoder}
import ControlSignals._
import Instructions._
import utils.RVConfig

object ControlLogic{
  val default: Seq[BitPat] = Seq(InstType.X, FuType.X, OpASrc.X, OpBSrc.X, ALUOp.X, MULOp.X, DIVOp.X, BRUOp.X, CSROp.X, AMOOp.X, MemWrite.X, MemRead.X , MemSignExt.X          , CSRWrite.X, RegWrite.X, Fence.X, Legal.N)
  val table = TruthTable(Map(
    LUI     -> Seq(InstType.U, FuType.ALU, OpASrc.ZERO, OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AUIPC   -> Seq(InstType.U, FuType.ALU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    JAL     -> Seq(InstType.J, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    JALR    -> Seq(InstType.I, FuType.BRU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    BEQ     -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.EQ , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    BNE     -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.NEQ, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    BLT     -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.LTS, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    BGE     -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.GES, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    BLTU    -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.LTU, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    BGEU    -> Seq(InstType.B, FuType.BRU, OpASrc.PC  , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.GEU, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    LB      -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.B , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    LH      -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.H , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    LW      -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    LBU     -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.B , MemSignExt.UnsignedExt, CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    LHU     -> Seq(InstType.I, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.H , MemSignExt.UnsignedExt, CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SB      -> Seq(InstType.S, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.B, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    SH      -> Seq(InstType.S, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.H, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    SW      -> Seq(InstType.S, FuType.LSU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.W, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    ADDI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SLTI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.LTS , MULOp.X     , DIVOp.X   , BRUOp.LTS, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SLTIU   -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.LTU , MULOp.X     , DIVOp.X   , BRUOp.LTU, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    XORI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.XOR , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    ORI     -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.OR  , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    ANDI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.AND , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SLLI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.SLL , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SRLI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.SRL , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SRAI    -> Seq(InstType.I, FuType.ALU, OpASrc.RS1 , OpBSrc.IMM , ALUOp.SRA , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    ADD     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SUB     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.SUB , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SLL     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.SLL , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SLT     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.LTS , MULOp.X     , DIVOp.X   , BRUOp.LTS, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SLTU    -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.LTU , MULOp.X     , DIVOp.X   , BRUOp.LTU, CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    XOR     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.XOR , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SRL     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.SRL , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SRA     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.SRA , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    OR      -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.OR  , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AND     -> Seq(InstType.R, FuType.ALU, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.AND , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    MUL     -> Seq(InstType.R, FuType.MUL, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.X   , MULOp.MUL   , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    MULH    -> Seq(InstType.R, FuType.MUL, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.X   , MULOp.MULH  , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    MULHSU  -> Seq(InstType.R, FuType.MUL, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.X   , MULOp.MULHSU, DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    MULHU   -> Seq(InstType.R, FuType.MUL, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.X   , MULOp.MULHU , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    DIV     -> Seq(InstType.R, FuType.DIV, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.ADD , MULOp.X     , DIVOp.DIV , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    DIVU    -> Seq(InstType.R, FuType.DIV, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.ADD , MULOp.X     , DIVOp.DIVU, BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    REM     -> Seq(InstType.R, FuType.DIV, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.ADD , MULOp.X     , DIVOp.REM , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    REMU    -> Seq(InstType.R, FuType.DIV, OpASrc.RS1 , OpBSrc.RS2 , ALUOp.ADD , MULOp.X     , DIVOp.REMU, BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    CSRRW   -> Seq(InstType.I, FuType.CSR, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.RW, AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, Fence.N, Legal.Y),
    CSRRC   -> Seq(InstType.I, FuType.CSR, OpASrc.RS1 , OpBSrc.CSR , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.RC, AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, Fence.N, Legal.Y),
    CSRRS   -> Seq(InstType.I, FuType.CSR, OpASrc.RS1 , OpBSrc.CSR , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.RS, AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, Fence.N, Legal.Y),
    CSRRWI  -> Seq(InstType.I, FuType.CSR, OpASrc.RS1ADDR, OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.RW, AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, Fence.N, Legal.Y),
    CSRRCI  -> Seq(InstType.I, FuType.CSR, OpASrc.RS1ADDR, OpBSrc.CSR , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.RC, AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, Fence.N, Legal.Y),
    CSRRSI  -> Seq(InstType.I, FuType.CSR, OpASrc.RS1ADDR, OpBSrc.CSR , ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.RS, AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.Y, RegWrite.Y, Fence.N, Legal.Y),
    EBREAK  -> Seq(InstType.N, FuType.ALU, OpASrc.X   , OpBSrc.X   , ALUOp.X   , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    ECALL   -> Seq(InstType.N, FuType.CSR, OpASrc.ZERO, OpBSrc.CSR , ALUOp.X   , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    MRET    -> Seq(InstType.N, FuType.CSR, OpASrc.ZERO, OpBSrc.CSR , ALUOp.X   , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y),
    FENCEI  -> Seq(InstType.N, FuType.ALU, OpASrc.X   , OpBSrc.X   , ALUOp.X   , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.Y, Legal.Y),
    FENCE   -> Seq(InstType.N, FuType.ALU, OpASrc.X   , OpBSrc.X   , ALUOp.X   , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.Y, Legal.Y),
    LRW     -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.LR  , MemWrite.N, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    SCW     -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.SC  , MemWrite.W, MemRead.N , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOSWAP -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.SWAP, MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOADD  -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.ADD , MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOXOR  -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.XOR , MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOAND  -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.AND , MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOOR   -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.OR  , MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOMIN  -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.MIN , MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOMAX  -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.MAX , MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOMINU -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.MINU, MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    AMOMAXU -> Seq(InstType.R, FuType.AMO, OpASrc.RS1 , OpBSrc.ZERO, ALUOp.ADD , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.MAXU, MemWrite.W, MemRead.W , MemSignExt.SignedExt  , CSRWrite.N, RegWrite.Y, Fence.N, Legal.Y),
    NOP     -> Seq(InstType.X, FuType.ALU, OpASrc.X   , OpBSrc.X   , ALUOp.X   , MULOp.X     , DIVOp.X   , BRUOp.N  , CSROp.N , AMOOp.X   , MemWrite.N, MemRead.N , MemSignExt.X          , CSRWrite.N, RegWrite.N, Fence.N, Legal.Y)
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
    val mulOp = Output(UInt(MULOp.WIDTH.W))
    val divOp = Output(UInt(DIVOp.WIDTH.W))
    val bruOp = Output(UInt(BRUOp.WIDTH.W))
    val csrOp = Output(UInt(CSROp.WIDTH.W))
    val amoOp = Output(UInt(AMOOp.WIDTH.W))
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
  val decodeBundle = Wire(new DecodeBundle)

  val instType = ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - InstType.WIDTH)
  ctrlOffset += InstType.WIDTH
  decodeBundle.fuType := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - FuType.WIDTH)
  ctrlOffset += FuType.WIDTH
  decodeBundle.aluSrc1 := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - OpASrc.WIDTH)
  ctrlOffset += OpASrc.WIDTH
  decodeBundle.aluSrc2 := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - OpBSrc.WIDTH)
  ctrlOffset += OpBSrc.WIDTH
  decodeBundle.aluOp := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - ALUOp.WIDTH)
  ctrlOffset += ALUOp.WIDTH
  decodeBundle.mulOp := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MULOp.WIDTH)
  ctrlOffset += MULOp.WIDTH
  decodeBundle.divOp := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - DIVOp.WIDTH)
  ctrlOffset += DIVOp.WIDTH
  decodeBundle.bruOp := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - BRUOp.WIDTH)
  ctrlOffset += BRUOp.WIDTH
  decodeBundle.csrOp := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - CSROp.WIDTH)
  ctrlOffset += CSROp.WIDTH
  decodeBundle.amoOp := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - AMOOp.WIDTH)
  ctrlOffset += AMOOp.WIDTH
  decodeBundle.memWrite := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MemWrite.WIDTH)
  ctrlOffset += MemWrite.WIDTH
  decodeBundle.memRead := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MemRead.WIDTH)
  ctrlOffset += MemRead.WIDTH
  decodeBundle.memSignExt := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - MemSignExt.WIDTH)
  ctrlOffset += MemSignExt.WIDTH
  decodeBundle.csrWrite := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - CSRWrite.WIDTH)
  ctrlOffset += CSRWrite.WIDTH
  decodeBundle.regWrite := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - RegWrite.WIDTH)
  ctrlOffset += RegWrite.WIDTH
  decodeBundle.fencei := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - Fence.WIDTH)
  ctrlOffset += Fence.WIDTH
  io.legal := ctrlWord(ctrlWordWidth - ctrlOffset - 1, ctrlWordWidth - ctrlOffset - Legal.WIDTH)(0)


  // get immediate value from immExt
  val immExt = Module(new ImmGen)
  immExt.io.inst <> io.inst
  immExt.io.instType <> instType
  immExt.io.imm <> io.imm

  io.decodeBundle <> decodeBundle
}
