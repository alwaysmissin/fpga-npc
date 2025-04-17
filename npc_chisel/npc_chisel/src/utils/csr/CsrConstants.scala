package utils.csr
import chisel3._
import chisel3.util.BitPat

trait CsrConsts {
    val MSTATUS   = 0x300
    val MTVEC     = 0x305
    val MEPC      = 0x341
    val MCAUSE    = 0x342
    val MVENDORID = 0xf11
    val MARCHID   = 0xf12
    def MRET  = "b001100000010".U
    def ECALL = "b000000000000".U
}