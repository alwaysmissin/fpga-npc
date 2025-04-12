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

object MEMState extends ChiselEnum{
    val IDLE, WAIT_RESP, AMO_WAIT_RESP, AMO_ALU, AMO_WRITE = Value
}

class MEM(config: RVConfig) extends Module {
    val io = IO(new Bundle{
        val fromEXE = Flipped(Irrevocable(new ExeMemBus(config)))
        val toWB = Irrevocable(new MemWbBus(config))
        // val r = Flipped(Irrevocable(new R(config)))
        // val b = Flipped(Irrevocable(new B(config)))
        val req = Irrevocable(new SRAMLikeReq(config))
        val rResp = Flipped(Irrevocable(new SRAMLikeRResp(config)))
        val wResp = Flipped(Irrevocable(new SRAMLikeWResp(config)))
        val bypass = Flipped(new BypassFrom(config))
        val regWdata = Output(UInt(config.xlen.W))
    })
    val controlSignals = io.fromEXE.bits.controlSignals
    val state = RegInit(MEMState.IDLE)
    val ren = controlSignals.memRead .orR && !io.fromEXE.bits.nop // MemRead.N
    val wen = controlSignals.memWrite.orR && !io.fromEXE.bits.nop // MemWrite.N

    // set pipeline control signal default
    io.toWB.valid := false.B
    io.fromEXE.ready := false.B

    // set lsu request signal default
    io.req.bits := 0.U.asTypeOf(io.req.bits)
    io.req.valid := false.B
    io.wResp.ready := false.B
    io.rResp.ready := false.B

    val byteWise = controlSignals.memRawMask
    val reqSize = PriorityMux(Seq(
        byteWise(3) -> 2.U,
        byteWise(1) -> 1.U,
        byteWise(0) -> 0.U,
    ))

    val vaddr = controlSignals.regWriteData
    val vaddrLow2Bits = vaddr(1, 0)
    val align = PriorityMux(Seq(
        byteWise(3) -> !vaddrLow2Bits.orR,
        byteWise(1) -> !vaddrLow2Bits(0),
        byteWise(0) -> true.B
    ))

    val memSignedExt = controlSignals.signExt
    val offset = (controlSignals.regWriteData(1) << 4.U).asUInt | 
                 (controlSignals.regWriteData(0) << 3.U).asUInt
    val rdataShift = io.rResp.bits.rdata >> offset
    val memRes = PriorityMux(Seq(
        byteWise(3) -> rdataShift,
        byteWise(1) -> Cat(Fill(16, rdataShift(15) & memSignedExt), rdataShift(15, 0)),
        byteWise(0) -> Cat(Fill(24, rdataShift( 7) & memSignedExt), rdataShift( 7, 0))
    ))

    val memWdata = PriorityMux(Seq(
        byteWise(3) -> controlSignals.memWriteData,
        byteWise(1) -> Fill(2, controlSignals.memWriteData(15, 0)),
        byteWise(0) -> Fill(4, controlSignals.memWriteData( 7, 0)),
    ))

    // align check when simulation is on
    if (config.simulation){
        switch(reqSize){
            is("b010".U){ 
                assert(vaddr(1, 0) === 0.U, "not align!!!!!, reqSize is %d, but target address is 0x%x, the pc is 0x%x\n", 1.U << reqSize, vaddr, io.fromEXE.bits.pc)
            }
            is("b001".U){
                assert(vaddr(0) === 0.U, "not align!!!!!, reqSize is %d, but target address is 0x%x, the pc is 0x%x\n", 1.U << reqSize, vaddr, io.fromEXE.bits.pc)
            }
            is("b000".U){}
        }
    }

    switch(state){
        is (MEMState.IDLE){
            when (ren || wen){
                io.req.valid := true.B
                io.req.bits.addr := vaddr
                io.req.bits.len := 0.U
                io.req.bits.wr := wen
                io.req.bits.wdata := memWdata
                io.req.bits.wstrb := controlSignals.memRawMask << vaddrLow2Bits
                io.req.bits.size := reqSize
                state := Mux(io.req.fire, MEMState.WAIT_RESP, MEMState.IDLE)
            }.otherwise{
                io.toWB.valid := true.B
                io.fromEXE.ready := true.B
            }
        }
        is (MEMState.WAIT_RESP){
            io.rResp.ready := io.rResp.valid && io.fromEXE.valid
            io.wResp.ready := io.wResp.valid && io.fromEXE.valid
            io.fromEXE.ready := io.rResp.valid || io.wResp.valid
            io.toWB.valid := io.rResp.valid || io.wResp.valid
            when (io.rResp.fire || io.wResp.fire){
                state := MEMState.IDLE
            }
        }
    }

    io.bypass.regWrite := controlSignals.regWrite
    io.bypass.waddr := io.fromEXE.bits.rd
    io.bypass.valid := io.toWB.valid
    io.regWdata := Mux(ren, memRes, controlSignals.regWriteData)

    val hasFired = RegEnable(false.B, false.B, io.toWB.fire && io.fromEXE.fire)
    when (io.toWB.fire && !io.fromEXE.fire){
        hasFired := true.B
    }
    io.toWB.bits.nop := io.fromEXE.bits.nop || hasFired

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
    io.toWB.bits.regWriteData := io.regWdata
    if (config.diff_enable) io.toWB.bits.jumped := io.fromEXE.bits.jumped
    if (config.trace_enable) io.toWB.bits.inst  := io.fromEXE.bits.inst


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
    if (config.diff_enable){
        val addrRangesToSkip = (vaddr >= 0x20000000L.U && vaddr < 0x21ffffffL.U) // skip device area and vga
        val skip_enable = addrRangesToSkip && ((ren || wen) && io.req.fire) && !io.toWB.bits.nop
        dontTouch(skip_enable)
        RawClockedVoidFunctionCall(
            "diff_skip", Option(Seq("skip_pc"))
        )(clock, enable = skip_enable, io.fromEXE.bits.pc)
    }

    if (config.simulation){
        RawClockedVoidFunctionCall(
            "LSULaunchARReq"
        )(clock, enable = io.req.fire && !io.req.bits.wr)
        RawClockedVoidFunctionCall(
            "LSULaunchAWReq"
        )(clock, enable = io.req.fire && io.req.bits.wr)
    }
}
