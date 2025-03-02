package utils.bus
import chisel3._
import chisel3.util._
import utils.RVConfig

object SRAMLike{
  class SRAMLikeReq(config: RVConfig) extends Bundle{
    // val valid = Output(Bool())
    // val ready = Input(Bool())
    // val bits = new Bundle{
    val wr    = Bool()
    val size  = UInt(2.W)
    val addr  = UInt(config.xlen.W)
    val wdata = UInt(config.xlen.W)
    val wstrb = UInt((config.xlen / 8).W)
    val len   = UInt(4.W) // for burst
    // val id    = UInt(1.W)
    // }
    // val fire = valid && ready
  }
  

  class SRAMLikeRResp(config: RVConfig) extends Bundle{
    // val valid = Input(Bool())
    // val ready = Output(Bool())
    // val bits = new Bundle{
    val rdata = UInt(config.xlen.W)
    // val resp = Bool()
    // val wr   = Bool()
    // val id   = UInt(1.W)
    // }
    // val fire = valid && ready
  }

  class SRAMLikeWResp(config: RVConfig) extends Bundle{
    val resp = Bool()
  }
}
