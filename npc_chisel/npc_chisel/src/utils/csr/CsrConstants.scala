package utils.csr
import chisel3._
import chisel3.util.BitPat

object CSRRegAddr {
    def MSTATUS   = 0x300.U
    def MTVEC     = 0x305.U
    def MEPC      = 0x341.U
    def MCAUSE    = 0x342.U
    def MVENDORID = 0xf11.U
    def MARCHID   = 0xf12.U
}

object funct12{
    def MRET  = "b001100000010".U
    def ECALL = "b000000000000".U
}