package utils.exe

import chisel3._
import utils.bus.AXI4
import utils.RVConfig
import utils.id.ControlSignals.MemWrite
import utils.id.ControlSignals.MemRead
import chisel3.util.Mux1H
import chisel3.util.Cat

class MemReqSignalsFromCPU(config:RVConfig) extends Bundle{
    val memWrite = Input(UInt(MemWrite.WIDTH.W))
    val memRead = Input(UInt(MemRead.WIDTH.W))
    val rs1 = Input(UInt(config.xlen.W))
    val rs2 = Input(UInt(config.xlen.W))
    val imm = Input(UInt(config.xlen.W))
}

class MemRespForCPU(config: RVConfig) extends Bundle{
    val rdata = Output(UInt(config.xlen.W))
}

class LSU(config: RVConfig) extends Module{
    val io = IO(new Bundle{
        val fromExeStage = new MemReqSignalsFromCPU(config)
        val toMemStage = new MemRespForCPU(config)
        // val memWrite = Input(UInt(MemWrite.WIDTH.W))
        // val memRead = Input(UInt(MemRead.WIDTH.W))
        val dbus = new AXI4(config)
        // val rs1 = Input(UInt(config.xlen.W))
        // val rs2 = Input(UInt(config.xlen.W))
        // val imm = Input(UInt(config.xlen.W))
        // val rdata = Output(UInt(config.xlen.W))
    })

    val vaddr = io.fromExeStage.rs1 + io.fromExeStage.imm
    io.dbus.aw.bits.addr := vaddr
    io.dbus.ar.bits.addr := vaddr
    
    val wen = io.fromExeStage.memWrite.orR // MemWrite.N
    io.dbus.aw.valid := wen

    val en = io.fromExeStage.memRead.orR || wen // MemRead.N
    io.dbus.r.valid := en

    io.dbus.w.bits.data := io.fromExeStage.rs2
    io.dbus.w.bits.strb := Cat("b0000".U, io.fromExeStage.memWrite)
    io.toMemStage.rdata := io.dbus.r.bits.data
}
