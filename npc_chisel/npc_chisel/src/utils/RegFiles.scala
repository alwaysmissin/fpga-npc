package utils

import chisel3._
import utils.RVConfig
import chisel3.util.log2Up
class RegReadPort(config: RVConfig) extends Bundle{
    val raddr = Input(UInt(log2Up(config.nr_reg).W))
    val rdata = Output(UInt(config.xlen.W))
}

class RegWritePort(config: RVConfig) extends Bundle{
    val wen = Input(Bool())
    val waddr = Input(UInt(log2Up(config.nr_reg).W))
    val wdata = Input(UInt(config.xlen.W))
}

class RegFiles(config: RVConfig) extends Module{
    val io = IO(new Bundle{
        val readPort1 = new RegReadPort(config)
        val readPort2 = new RegReadPort(config)
        val writePort = new RegWritePort(config)
    })

    val R = Mem(config.nr_reg - 1, UInt(config.xlen.W))
    io.readPort1.rdata := Mux(io.readPort1.raddr === 0.U, 0.U, R(io.readPort1.raddr - 1.U))
    io.readPort2.rdata := Mux(io.readPort2.raddr === 0.U, 0.U, R(io.readPort2.raddr - 1.U))

    when(io.writePort.wen){
        R(io.writePort.waddr - 1.U) := io.writePort.wdata
    }
  
}
