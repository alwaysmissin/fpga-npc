package utils.id

import chisel3.util.BitPat

object Instructions {
  def LUI    = BitPat("b??????? ????? ????? ??? ????? 01101 11")
  def AUIPC  = BitPat("b??????? ????? ????? ??? ????? 00101 11")
  def JAL    = BitPat("b??????? ????? ????? ??? ????? 11011 11")
  def JALR   = BitPat("b??????? ????? ????? 000 ????? 11001 11")
  def BEQ    = BitPat("b??????? ????? ????? 000 ????? 11000 11")
  def BNE    = BitPat("b??????? ????? ????? 001 ????? 11000 11")
  def BLT    = BitPat("b??????? ????? ????? 100 ????? 11000 11")
  def BGE    = BitPat("b??????? ????? ????? 101 ????? 11000 11")
  def BLTU   = BitPat("b??????? ????? ????? 110 ????? 11000 11")
  def BGEU   = BitPat("b??????? ????? ????? 111 ????? 11000 11")
  def LB     = BitPat("b??????? ????? ????? 000 ????? 00000 11")
  def LH     = BitPat("b??????? ????? ????? 001 ????? 00000 11")
  def LW     = BitPat("b??????? ????? ????? 010 ????? 00000 11")
  def LBU    = BitPat("b??????? ????? ????? 100 ????? 00000 11")
  def LHU    = BitPat("b??????? ????? ????? 101 ????? 00000 11")
  def SB     = BitPat("b??????? ????? ????? 000 ????? 01000 11")
  def SH     = BitPat("b??????? ????? ????? 001 ????? 01000 11")
  def SW     = BitPat("b??????? ????? ????? 010 ????? 01000 11")
  def ADDI   = BitPat("b??????? ????? ????? 000 ????? 00100 11")
  def SLTI   = BitPat("b??????? ????? ????? 010 ????? 00100 11")
  def SLTIU  = BitPat("b??????? ????? ????? 011 ????? 00100 11")
  def XORI   = BitPat("b??????? ????? ????? 100 ????? 00100 11")
  def ORI    = BitPat("b??????? ????? ????? 110 ????? 00100 11")
  def ANDI   = BitPat("b??????? ????? ????? 111 ????? 00100 11")
  def SLLI   = BitPat("b0000000 ????? ????? 001 ????? 00100 11")
  def SRLI   = BitPat("b0000000 ????? ????? 101 ????? 00100 11")
  def SRAI   = BitPat("b0100000 ????? ????? 101 ????? 00100 11")
  def ADD    = BitPat("b0000000 ????? ????? 000 ????? 01100 11")
  def SUB    = BitPat("b0100000 ????? ????? 000 ????? 01100 11")
  def SLL    = BitPat("b0000000 ????? ????? 001 ????? 01100 11")
  def SLT    = BitPat("b0000000 ????? ????? 010 ????? 01100 11")
  def SLTU   = BitPat("b0000000 ????? ????? 011 ????? 01100 11")
  def XOR    = BitPat("b0000000 ????? ????? 100 ????? 01100 11")
  def SRL    = BitPat("b0000000 ????? ????? 101 ????? 01100 11")
  def SRA    = BitPat("b0100000 ????? ????? 101 ????? 01100 11")
  def OR     = BitPat("b0000000 ????? ????? 110 ????? 01100 11")
  def AND    = BitPat("b0000000 ????? ????? 111 ????? 01100 11")
  def MUL    = BitPat("b0000001 ????? ????? 000 ????? 01100 11")
  def MULH   = BitPat("b0000001 ????? ????? 001 ????? 01100 11")
  def MULHSU = BitPat("b0000001 ????? ????? 010 ????? 01100 11")
  def MULHU  = BitPat("b0000001 ????? ????? 011 ????? 01100 11")
  def DIV    = BitPat("b0000001 ????? ????? 100 ????? 01100 11")
  def DIVU   = BitPat("b0000001 ????? ????? 101 ????? 01100 11")
  def REM    = BitPat("b0000001 ????? ????? 110 ????? 01100 11")
  def REMU   = BitPat("b0000001 ????? ????? 111 ????? 01100 11")
  def EBREAK = BitPat("b0000000 00001 00000 000 00000 11100 11")
  def ECALL  = BitPat("b0000000 00000 00000 000 00000 11100 11")
  def MRET   = BitPat("b0011000 00010 00000 000 00000 11100 11")
  def CSRRW  = BitPat("b??????? ????? ????? 001 ????? 11100 11")
  def CSRRS  = BitPat("b??????? ????? ????? 010 ????? 11100 11")
  def CSRRC  = BitPat("b??????? ????? ????? 011 ????? 11100 11")
  def LRW    = BitPat("b00010?? 00000 ????? 010 ????? 01011 11")
  def SCW    = BitPat("b00011?? ????? ????? 010 ????? 01011 11")
  def AMOSWAP= BitPat("b00001?? ????? ????? 010 ????? 01011 11")
  def AMOADD = BitPat("b00000?? ????? ????? 010 ????? 01011 11")
  def AMOXOR = BitPat("b00100?? ????? ????? 010 ????? 01011 11")
  def AMOAND = BitPat("b01100?? ????? ????? 010 ????? 01011 11")
  def AMOOR  = BitPat("b01000?? ????? ????? 010 ????? 01011 11")
  def AMOMIN = BitPat("b10000?? ????? ????? 010 ????? 01011 11")
  def AMOMAX = BitPat("b10100?? ????? ????? 010 ????? 01011 11")
  def AMOMINU= BitPat("b11000?? ????? ????? 010 ????? 01011 11")
  def AMOMAXU= BitPat("b11100?? ????? ????? 010 ????? 01011 11")
  def FENCEI = BitPat("b0000000 00000 00000 001 00000 00011 11")
  def NOP    = BitPat("b0000000 00000 00000 000 00000 00000 00")

}
