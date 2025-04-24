package core

import chisel3._
import utils.RVConfig
import chisel3.util.Decoupled
import chisel3.util.Irrevocable
import utils.bus.InterStage.MemWbBus
import utils.RegWritePort
import chisel3.util.Mux1H
import utils.bypass.BypassFrom
import verification.diff.DiffSignals
import verification.trace.TraceSignals
import verification.DebugSignals
import utils.csr._
import chisel3.util.Cat
import utils.id.ControlSignals.FuType

class WB(config: RVConfig) extends Module with CsrConsts{
    val io = IO(new Bundle{
        val fromMEM = Flipped(Irrevocable(new MemWbBus(config)))
        val writePort = Flipped(new RegWritePort(config))
        // val csrWritePort = Flipped(new CSRWritePort(config))
        val csrCmd = Flipped(new ExcepCMD(config))
        val bypass = Flipped(new BypassFrom(config))
        val regWdata = Output(UInt(config.xlen.W))
        val debug: DebugSignals = if(config.debug_enable) new DebugSignals(config) else null
        val diff: DiffSignals = if(config.diff_enable) new DiffSignals(config) else null
        val trace: TraceSignals = if(config.trace_enable) new TraceSignals(config) else null
    })

    val wen = io.fromMEM.bits.regWrite
    val wdata = io.fromMEM.bits.regWriteData
    val waddr = io.fromMEM.bits.rd

    io.bypass.regWrite := io.fromMEM.bits.regWrite
    io.bypass.waddr := io.fromMEM.bits.rd 
    io.bypass.valid := true.B
    io.regWdata := wdata

    io.writePort.waddr := waddr
    io.writePort.wen := Mux(waddr === 0.U, false.B, wen)
    io.writePort.wdata := wdata

    // 在出现异常时候, 将pc通过写端口的方式传递给CSRRegFile
    // io.csrWritePort.wen := io.fromMEM.bits.csrWrite
    // io.csrWritePort.waddr := Mux(io.fromMEM.bits.hasException, Mcause.U, io.fromMEM.bits.funct12)
    // io.csrWritePort.wdata := Mux(io.fromMEM.bits.hasException, io.fromMEM.bits.pc, io.fromMEM.bits.csrWriteData)
    // io.csrCmd.hasExcep := io.fromMEM.bits.excepVec.asUInt.orR
    io.csrCmd.excepVec := io.fromMEM.bits.excepVec.map(_ && !io.fromMEM.bits.nop)
    io.csrCmd.mret := io.fromMEM.bits.mret
    // io.csrCmd.funct12 := io.fromMEM.bits.funct12
    io.csrCmd.pc := io.fromMEM.bits.pc

    io.fromMEM.ready := true.B

    // debug
    if(config.debug_enable){
        val done = RegInit(false.B)
        val pc_done = RegInit(0.U(config.xlen.W))
        when(io.fromMEM.valid){
            done := !io.fromMEM.bits.nop && io.fromMEM.bits.pc.orR
            pc_done := io.fromMEM.bits.pc
        } otherwise {
            done := false.B
        }
        io.debug.done := done
        io.debug.pc_done := pc_done
    }
    if(config.diff_enable){
        val jumped = RegInit(false.B)
        when(io.fromMEM.valid){
            jumped := io.fromMEM.bits.jumped
        }
        io.diff.jumped := jumped
    }
    if(config.trace_enable){
        val inst = RegInit(0.U(config.xlen.W))
        when(io.fromMEM.valid){
            inst := io.fromMEM.bits.inst
        }
        io.trace.inst := inst
    }
}
