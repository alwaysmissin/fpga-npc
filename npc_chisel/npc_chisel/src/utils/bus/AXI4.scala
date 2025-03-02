package utils.bus

import chisel3._
import chisel3.util._
import utils.RVConfig
import utils.bus.AXI4.MS.{asMaster => asMaster}

object AXI4{
    object MS extends Enumeration{
        type MS = Value
        val asMaster, asSlave = Value
    }

    def apply(config: RVConfig, mode: MS.Value = asMaster): AXI4 = {
        if (mode == asMaster) { 
            val axi = new AXI4(config) 
            axi
        }
        else Flipped(new AXI4(config))
    }
}

class AXI4(config: RVConfig) extends Bundle{
    val aw = Irrevocable(new AW(config))
    val w = Irrevocable(new W(config))
    val b = Flipped(Irrevocable(new B(config)))
    val ar = Irrevocable(new AR(config))
    val r = Flipped(Irrevocable(new R(config)))
}

class AW(config: RVConfig) extends Bundle{
    val addr = Bits(config.xlen.W)
    val id   = Bits(4.W)
    val len  = Bits(8.W)
    val size = Bits(3.W)
    val burst = AXIBURST()
    val lock = Bool()
    val cache = Bits(4.W)
    val prot = Bits(3.W)
    val qos = Bits(4.W)
}

class W(config: RVConfig) extends Bundle{
    val data = Bits(config.xlen.W)
    val strb = Bits(4.W)
    val last = Bool()
}

class B(config: RVConfig) extends Bundle{
    val resp = Bits(2.W)
    val id = Bits(4.W)
}

class AR(config: RVConfig) extends Bundle{
    val addr = Bits(config.xlen.W)
    val id   = Bits(4.W)
    val len = Bits(8.W)
    val size = Bits(3.W)
    val burst = AXIBURST()
    val lock = Bool()
    val cache = Bits(4.W)
    val prot = Bits(3.W)
    val qos = Bits(4.W)
}

class R(config: RVConfig) extends Bundle{
    val resp = Bits(2.W)
    val data = Bits(config.xlen.W)
    val last = Bool()
    val id = Bits(4.W)
}

object AXIBURST extends ChiselEnum{
    val FIXED, INCR, WRAP, RESERVED = Value
}