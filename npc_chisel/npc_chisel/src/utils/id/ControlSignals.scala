package utils.id

import chisel3.util.BitPat

object ControlSignals {
  def Y = BitPat("b1")
  def N = BitPat("b0")
  def X = BitPat("b?")

  // instruction type
  object InstType{
    def R = BitPat("b000") // R-Type
    def I = BitPat("b001") // I-Type
    def S = BitPat("b010") // S-Type
    def B = BitPat("b011") // B-Type
    def U = BitPat("b100") // U-Type
    def J = BitPat("b101") // J-Type
    def N = BitPat("b110")
    def X = BitPat("b???")
    def WIDTH = X.getWidth
  }

  object FuType{
    def ALU = BitPat("b0000")
    def LSU = BitPat("b0001")
    def BRU = BitPat("b0010")
    def CSR = BitPat("b0011")
    def MUL = BitPat("b0100")
    def DIV = BitPat("b0101")
    def X = BitPat("b????")
    def WIDTH = X.getWidth
  }

  // the source of alu operator 1
  object OpASrc{
    // register rs1
    def RS1 = BitPat("b00")
    // zero
    def ZERO = BitPat("b01")
    // program counter(pc)
    def PC   = BitPat("b10")
    def ALUSrc1 = BitPat("b11")
    def X    = BitPat("b??")
    def WIDTH = X.getWidth
  }

  // the source of alu operator 2
  object OpBSrc{
    // register rs2
    def RS2 = BitPat("b00")
    // immediate number
    def IMM = BitPat("b01")
    def N = BitPat("b10")
    def CSR = BitPat("b11")
    def X = BitPat("b??")
    def WIDTH = X.getWidth
  }
  object ALUOp{
    def ADD = BitPat("b0000")
    def SUB = BitPat("b0001")
    def AND = BitPat("b0010")
    def OR  = BitPat("b0011")
    def XOR = BitPat("b0100")
    // shift left logical
    def LTS = BitPat("b0101")
    // shift right logical
    def SLL = BitPat("b0110")
    // shift right arithmetic
    def LTU = BitPat("b0111")
    // // multiply(sign)
    // // low 32 bits
    // def MULL = BitPat("b01000")
    // // high 32 bits
    // def MULH = BitPat("b01001")
    // def MULHU = BitPat("b01010")
    // // divide
    // // unsigned
    // def DIVU = BitPat("b01011")
    // // sign
    // def DIVS = BitPat("b01100")
    // // less than unsign
    def SRL = BitPat("b1000")
    def SRA = BitPat("b1001")
    def X = BitPat("b????")
    def WIDTH = X.getWidth
  }

  object MULOp{
    def MUL = BitPat("b00")
    def MULHU = BitPat("b01")
    def MULH = BitPat("b10")
    def MULHSU = BitPat("b11")
    def X = BitPat("b??")
    def WIDTH = X.getWidth
  }

  object DIVOp{
    def DIVU = BitPat("b00")
    def DIV = BitPat("b01")
    def REMU = BitPat("b10")
    def REM = BitPat("b11")
    def X = BitPat("b??")
    def WIDTH = X.getWidth
  }

  
  object BRUOp{
    def LTU = BitPat("b000")
    def GEU = BitPat("b001")
    def LTS = BitPat("b010")
    def GES = BitPat("b011")
    def EQ  = BitPat("b100")
    def NEQ = BitPat("b101")
    def N   = BitPat("b110")
    def X   = BitPat("b???")
    def WIDTH = X.getWidth
  }

  object CSROp{
    def RW = BitPat("b00")
    def RC = BitPat("b01")
    def RS = BitPat("b10")
    def N  = BitPat("b11")
    def X  = BitPat("b??")
    def WIDTH = X.getWidth
  }

  object MemWrite{
    // Write 1 Byte
    def N = BitPat("b0000")
    def B = BitPat("b0001")
    // Write 2 Byte (half word)
    def H = BitPat("b0011")
    // Write 4 Byte (word)
    def W = BitPat("b1111")
    def X = BitPat("b????")
    def WIDTH = X.getWidth
  }

  object MemRead{
    // Read 1 Byte
    def N = BitPat("b0000")
    def B = BitPat("b0001")
    // Read 2 Byte (half word)
    // unsigned
    def H = BitPat("b0011")
    // Read 4 Byte (word)
    def W = BitPat("b1111")
    def X = BitPat("b????")
    def WIDTH = X.getWidth
  }

  object MemSignExt{
    def UnsignedExt = BitPat("b0")
    def SignedExt = BitPat("b1")
    def X = BitPat("b?")
    def WIDTH = X.getWidth
  }

  // the source of next pc
  // object NextPCSrc{
  //   // PC + 4
  //   def PC4      = BitPat("b000")
  //   // branch
  //   def Branch   = BitPat("b001")
  //   // jump target
  //   def Jump     = BitPat("b010")
  //   def JumpR    = BitPat("b011")
  //   def CSR_J    = BitPat("b100")
  //   def X        = BitPat("b???")
  //   def WIDTH    = X.getWidth
  // }

  object RegWrite{
    def Y = BitPat("b1")
    def N = BitPat("b0")
    def X = BitPat("b?")
    def WIDTH = X.getWidth
  }

  object Legal{
    def Y = BitPat("b1")
    def N = BitPat("b0")
    def X = BitPat("b?")
    def WIDTH = X.getWidth
  }

  object CSRWrite{
    def Y = BitPat("b1")
    def N = BitPat("b0")
    def X = BitPat("b?")
    def WIDTH = X.getWidth
  }

  // register write source
  // object RegWriteSrc{
  //   // the result of alu
  //   def ALURes = BitPat("b000")
  //   // immediate number
  //   def IMM    = BitPat("b001")
  //   // PC + 4
  //   def PC4    = BitPat("b010")
  //   // from memory read
  //   def MEM    = BitPat("b011")
  //   def CSR    = BitPat("b100")
  //   def N      = BitPat("b101")
  //   def X      = BitPat("b???")
  //   def WIDTH  = X.getWidth
  // }


  // object CSR{
  //   def RW = BitPat("b00")
  //   def RC = BitPat("b01")
  //   def RS = BitPat("b10")
  //   def N  = BitPat("b11")
  //   def X = BitPat("b?")
  //   def WIDTH  = X.getWidth
  // }

  object MemToReg{
    def Y = BitPat("b1")
    def N = BitPat("b0")
    def X = BitPat("b?")
    def WIDTH = X.getWidth
  }

  object FENCE{
    def Y = BitPat("b1")
    def N = BitPat("b0")
    def X = BitPat("b?")
    def WIDTH = X.getWidth
  }
}
