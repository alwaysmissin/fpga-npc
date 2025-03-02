package tools

import chisel3._
import chisel3.util.Cat
import tools.LFSR.defaultFunc

/**
  * Linear Feedback Shift Register
  * generate a LFSR of `nr_bits` bits with the feedback function of `func`
  *
  * @param nr_bits
  * @param func
  */
object LFSR {
    def defaultFunc(lfsr: UInt): Bool = {
        lfsr(0) ^ lfsr(2) ^ lfsr(3) ^ lfsr(4)
    }
}

case class LFSR(nr_bits: Int = 8, func: UInt => Bool = defaultFunc) extends Module{
    val io = IO(new Bundle{
        val out = Output(UInt(nr_bits.W))
    })
    val lfsr = RegInit(1.U(nr_bits.W))
    io.out := lfsr
    val shiftIn = func(lfsr)
    lfsr := Cat(shiftIn, lfsr(nr_bits-1, 1))
}
