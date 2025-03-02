package core
import chisel3._
import utils.RVConfig
import chisel3.util.Irrevocable
import utils.bus.InterStage.ExeMemBus
import utils.exe.MemRespForCPU
import chisel3.util.switch
import chisel3.util.is
import chisel3.util.Cat
import chisel3.util.Fill
import utils.id.ControlLogic.default
import utils.bus.InterStage.MemWbBus
import chisel3.util.MuxCase
import chisel3.util.Mux1H
import utils.bypass.BypassFrom
import utils.bus.{R, B}
import chisel3.util.RegEnable
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import utils.id.ControlSignals.FuType
import utils.bus.SRAMLike._
import chisel3.util.PriorityMux

class MEM(config: RVConfig) extends Module {
    val io = IO(new Bundle{
        val fromEXE = Flipped(Irrevocable(new ExeMemBus(config)))
        val toWB = Irrevocable(new MemWbBus(config))
        // val r = Flipped(Irrevocable(new R(config)))
        // val b = Flipped(Irrevocable(new B(config)))
        val rResp = Flipped(Irrevocable(new SRAMLikeRResp(config)))
        val wResp = Flipped(Irrevocable(new SRAMLikeWResp(config)))
        val bypass = Flipped(new BypassFrom(config))
        val regWdata = Output(UInt(config.xlen.W))
    })
    val ren = io.fromEXE.bits.controlSignals.memRead
    val wen = io.fromEXE.bits.controlSignals.memWrite
    io.rResp.ready := io.rResp.valid && ren
    io.wResp.ready := io.wResp.valid && wen
    // io.r.ready := io.r.valid && ren
    // io.b.ready := io.b.valid && wen
    val memMask = io.fromEXE.bits.controlSignals.memReadMask
    val memSignedExt = io.fromEXE.bits.controlSignals.signExt
    val offset = (io.fromEXE.bits.controlSignals.regWriteData(1) << 4.U).asUInt | (io.fromEXE.bits.controlSignals.regWriteData(0) << 3.U).asUInt
    val rdataShift = io.rResp.bits.rdata >> offset
    // val memRes = Mux1H(Seq(
    //     (memMask === "b0001".U) -> Cat(Fill(24, rdata( 7) & memSignedExt), rdata( 7,  0)),
    //     (memMask === "b0010".U) -> Cat(Fill(24, rdata(15) & memSignedExt), rdata(15,  8)),
    //     (memMask === "b0100".U) -> Cat(Fill(24, rdata(23) & memSignedExt), rdata(23, 16)),
    //     (memMask === "b1000".U) -> Cat(Fill(24, rdata(31) & memSignedExt), rdata(31, 24)),
    //     (memMask === "b0011".U) -> Cat(Fill(16, rdata(15) & memSignedExt), rdata(15,  0)),
    //     (memMask === "b1100".U) -> Cat(Fill(16, rdata(31) & memSignedExt), rdata(31, 16)),
    //     (memMask === "b1111".U) -> rdata
    // ))
    val memRes = PriorityMux(Seq(
        memMask(3) -> rdataShift,
        memMask(1) -> Cat(Fill(16, rdataShift(15) & memSignedExt), rdataShift(15, 0)),
        memMask(0) -> Cat(Fill(24, rdataShift( 7) & memSignedExt), rdataShift( 7, 0))
    ))

    // io.toWB.bits.aluRes      := io.fromEXE.bits.controlSignals.aluRes
    io.toWB.bits.csrWriteData     := io.fromEXE.bits.controlSignals.csrWriteData
    io.toWB.bits.funct12     := io.fromEXE.bits.funct12
    // io.toWB.bits.memRes      := memRes
    io.toWB.bits.regWrite    := io.fromEXE.bits.controlSignals.regWrite && !io.toWB.bits.nop
    io.toWB.bits.csrWrite    := io.fromEXE.bits.controlSignals.csrWrite && !io.toWB.bits.nop
    io.toWB.bits.rd          := io.fromEXE.bits.rd
    io.toWB.bits.pc          := io.fromEXE.bits.pc
    // TODO: 暂时忽略 Load Access Fault 异常
    io.toWB.bits.mret        := io.fromEXE.bits.mret
    io.toWB.bits.hasException:= io.fromEXE.bits.hasException
    io.toWB.bits.exceptionCode := io.fromEXE.bits.exceptionCode

    if (config.diff_enable) io.toWB.bits.jumped := io.fromEXE.bits.jumped
    if (config.trace_enable) io.toWB.bits.inst  := io.fromEXE.bits.inst

    val hasFired = RegEnable(false.B, false.B, io.toWB.fire && io.fromEXE.fire)
    when (io.toWB.fire && !io.fromEXE.fire){
        hasFired := true.B
    }
    io.toWB.bits.nop := io.fromEXE.bits.nop || hasFired

    io.bypass.regWrite := io.fromEXE.bits.controlSignals.regWrite && !io.toWB.bits.nop
    io.bypass.waddr := io.fromEXE.bits.rd
    io.bypass.valid := io.toWB.valid

    val controlSignals = io.fromEXE.bits.controlSignals
    io.regWdata := Mux(ren, memRes, io.fromEXE.bits.controlSignals.regWriteData)
    io.toWB.bits.regWriteData := io.regWdata

    // val respValid = (((ren && (io.r.fire)) || (wen && (io.b.fire)))) || (!ren && !wen)
    val respValid = ((ren && io.rResp.fire) || (wen && io.wResp.fire)) || (!ren && !wen) || hasFired
    if (config.simulation){
        // RawClockedVoidFunctionCall("ebreak", Option(Seq("inst")))(clock, respValid && wen && io.wResp.bits.resp , "h00100073".U(32.W))
        when (respValid && wen){
            assert(!io.wResp.bits.resp)
        }
    }
    io.toWB.valid := respValid
    io.fromEXE.ready := io.toWB.ready && respValid

    if (config.simulation){
        RawClockedVoidFunctionCall(
            "PerfCountLSU"
        )(clock, enable = (io.rResp.fire || io.wResp.fire) && !io.toWB.bits.nop)
        RawClockedVoidFunctionCall(
            "LSURecvRResp"
        )(clock, enable = io.rResp.fire && ren)
        RawClockedVoidFunctionCall(
            "LSURecvBResp"
        )(clock, enable = io.wResp.fire && wen)
    }
}
