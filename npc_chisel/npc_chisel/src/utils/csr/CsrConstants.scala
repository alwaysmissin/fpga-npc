package utils.csr
import chisel3._
import chisel3.util.BitPat

trait CsrConsts {
    val Satp      = 0x180
    val Mstatus   = 0x300
    val Mie       = 0x304
    val Mip       = 0x344
    val Mtvec     = 0x305
    val Menvcfg   = 0x30a
    val Mscratch  = 0x340
    val Mepc      = 0x341
    val Mcause    = 0x342
    val Mtval     = 0x343
    val Pmpcfg0   = 0x3a0
    val Pmpaddr0  = 0x3b0
    val Mvendorid = 0xf11
    val Marchid   = 0xf12
    val Mimpid    = 0xf13
    val Mhartid   = 0xf14

    def ECALL = 0x000.U
    def MRET  = 0x302.U

    def IRQ_SEIP = 1
    def IRQ_MEIP = 3
    def IRQ_STIP = 5
    def IRQ_MTIP = 7
    def IRQ_SSIP = 9
    def IRQ_MSIP = 11

    val IntPriority = Seq(
        IRQ_MEIP, IRQ_MSIP, IRQ_MTIP,
        IRQ_SEIP, IRQ_SSIP, IRQ_STIP
    )
}