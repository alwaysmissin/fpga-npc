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
import utils.id.ControlSignals.AMOOp
import utils.id.Instructions.AMOADD
import utils.exe.AMOALU
import utils.ExceptionCodes

object MEMState extends ChiselEnum{
    val IDLE, WAIT_RESP, AMO_WAIT_READ_RESP, AMO_LAUNCH_WRITE_REQ, AMO_WAIT_WRITE_RESP = Value
}

class MEM(config: RVConfig) extends Module with ExceptionCodes{
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
    val reservation = RegInit(0.U(config.xlen.W))

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

    val amoAlu = Module(new AMOALU(config))
    amoAlu.io.amoOp := controlSignals.amoOp
    amoAlu.io.mRead := io.rResp.bits.rdata
    amoAlu.io.src2  := controlSignals.memWriteData
    val amoAluRes = amoAlu.io.res
    val amoAluResReg = RegInit(0.U(config.xlen.W))
    val amoRegWriteDataReg = RegInit(0.U(config.xlen.W))
    val amoRegWriteData = WireDefault(0.U(config.xlen.W))
    amoRegWriteData := amoRegWriteDataReg

    switch(state){
        is (MEMState.IDLE){
            when (ren || wen){
                // when `ren` or `wen` is asserted, the inst should be load/store or load-reservation/store-condition/amo
                // avoid to launch when the store-condition is not satisfied
                io.req.valid := !(controlSignals.fuTypeAMO && controlSignals.amoOp === AMOOp.SC && reservation =/= vaddr)
                io.req.bits.addr := vaddr
                io.req.bits.len := 0.U
                // only launching write request when `wen` is true.B and `ren` is false.B
                // avoid launch write request first when the inst is amo
                io.req.bits.wr  := !ren && wen
                io.req.bits.wdata := memWdata
                io.req.bits.wstrb := controlSignals.memRawMask << vaddrLow2Bits
                io.req.bits.size := reqSize
                // `ren && wen` means `amox` instruction, should go to `AMO_WAIT_RESP` state
                state := Mux(io.req.fire, 
                            Mux(ren && wen, MEMState.AMO_WAIT_READ_RESP, MEMState.WAIT_RESP), 
                            MEMState.IDLE)
                // TODO: 考虑reservation是不是会在请求被阻塞时候，提前被清除？
                when (io.toWB.fire || io.req.fire){
                    reservation := Mux(controlSignals.fuTypeAMO && controlSignals.amoOp === AMOOp.SC, 0.U, reservation)
                }
                when (controlSignals.fuTypeAMO && controlSignals.amoOp === AMOOp.SC){
                    amoRegWriteDataReg := Cat(0.U(31.W), reservation =/= vaddr)
                    amoRegWriteData := Cat(0.U(31.W), reservation =/= vaddr)
                    io.toWB.valid := reservation =/= vaddr
                    io.fromEXE.ready := reservation =/= vaddr
                }
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
            state := Mux(io.rResp.fire || io.wResp.fire, MEMState.IDLE, MEMState.WAIT_RESP)
            when (controlSignals.fuTypeAMO) {
                when (controlSignals.amoOp === AMOOp.LR){
                    amoRegWriteData := io.rResp.bits.rdata
                    amoRegWriteDataReg := io.rResp.bits.rdata
                    reservation := vaddr
                }.elsewhen(controlSignals.amoOp === AMOOp.SC){
                    amoRegWriteData := 0.U
                    amoRegWriteDataReg := 0.U
                }
            }
        }
        is (MEMState.AMO_WAIT_READ_RESP){
            io.rResp.ready := io.rResp.valid
            when (io.rResp.fire){
                amoAluResReg := amoAluRes
                amoRegWriteDataReg := io.rResp.bits.rdata
                state := MEMState.AMO_LAUNCH_WRITE_REQ
            }
        }
        is (MEMState.AMO_LAUNCH_WRITE_REQ){
            io.req.valid := true.B
            io.req.bits.addr := vaddr
            io.req.bits.len := 0.U
            io.req.bits.wr  := true.B
            io.req.bits.wdata := amoAluResReg
            io.req.bits.wstrb := 0xF.U
            io.req.bits.size := 2.U
            state := Mux(io.req.fire, MEMState.AMO_WAIT_WRITE_RESP, MEMState.AMO_LAUNCH_WRITE_REQ)
        }
        is (MEMState.AMO_WAIT_WRITE_RESP){
            io.wResp.ready := io.wResp.valid && io.fromEXE.valid
            io.fromEXE.ready := io.wResp.valid
            io.toWB.valid := io.wResp.valid
            state := Mux(io.wResp.fire, MEMState.IDLE, MEMState.AMO_WAIT_WRITE_RESP)
        }
    }

    io.bypass.regWrite := controlSignals.regWrite
    io.bypass.waddr := io.fromEXE.bits.rd
    io.bypass.valid := io.toWB.valid
    io.regWdata := PriorityMux(Seq(
        controlSignals.fuTypeAMO -> amoRegWriteData,
        ren -> memRes,
        true.B -> controlSignals.regWriteData
    ))

    val hasFired = RegEnable(false.B, false.B, io.toWB.fire && io.fromEXE.fire)
    when (io.toWB.fire && !io.fromEXE.fire){
        hasFired := true.B
    }
    io.toWB.bits.nop := io.fromEXE.bits.nop || hasFired

    // io.toWB.bits.csrWriteData     := io.fromEXE.bits.controlSignals.csrWriteData
    io.toWB.bits.funct12     := io.fromEXE.bits.funct12
    // io.toWB.bits.memRes      := memRes
    io.toWB.bits.regWrite    := io.fromEXE.bits.controlSignals.regWrite && !io.toWB.bits.nop
    // io.toWB.bits.csrWrite    := io.fromEXE.bits.controlSignals.csrWrite && !io.toWB.bits.nop
    io.toWB.bits.rd          := io.fromEXE.bits.rd
    io.toWB.bits.pc          := io.fromEXE.bits.pc
    // TODO: 暂时忽略 Load Access Fault 异常
    io.toWB.bits.mret        := io.fromEXE.bits.mret
    io.toWB.bits.excepVec      := io.fromEXE.bits.excepVec
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
