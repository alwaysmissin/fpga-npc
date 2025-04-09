package utils.exe

import chisel3._
import utils.RVConfig
import chisel3.util.Irrevocable
import chisel3.util.Cat
import utils.Config.FPGAPlatform

class MultiplierFPGA(dataWidth: Int = 32) extends BlackBox{
  override val desiredName = "multiplier"
  val io = IO(new Bundle {
    val CLK = Clock()
    val A = Input(UInt(dataWidth.W))
    val B = Input(UInt(dataWidth.W))
    val P = Output(UInt((dataWidth * 2).W))
  })
}

class MultiplierSim(dataWidth: Int = 32) extends Module{
  val io = IO(new Bundle{
    val A = Input(UInt(dataWidth.W))
    val B = Input(UInt(dataWidth.W))
    val P = Output(UInt((dataWidth * 2).W))
  })
  io.P := io.A * io.B
}

// class MultiplierGeneral(dataWidth: Int = 32) extends Module{

// }

class Multiplier(config: RVConfig) extends Module{
  val io = IO(new Bundle {
    val req = Flipped(Irrevocable(new Bundle{
      val A = UInt(config.xlen.W)
      val B = UInt(config.xlen.W)
    }))
    val resp = Irrevocable(new Bundle{
      val P = UInt((config.xlen * 2).W)
    })
  })
  if (FPGAPlatform){
    val multiplier = Module(new MultiplierFPGA())
    multiplier.io.CLK <> clock
    multiplier.io.A <> io.req.bits.A
    multiplier.io.B <> io.req.bits.B
    io.resp.bits.P  <> multiplier.io.P
  } else {
    val multiplier = Module(new MultiplierSim())
    multiplier.io.A <> io.req.bits.A
    multiplier.io.B <> io.req.bits.B
    io.resp.bits.P  <> multiplier.io.P

    io.req.ready := true.B
    io.resp.valid := true.B
  }
}
