package core
import chisel3._
import utils.RVConfig
import chisel3.util.Decoupled
import chisel3.util.Irrevocable
import utils.bus.InterStage.IfIdBus
import utils.RegReadPort
import utils.bus.InterStage.IdExeBus
import utils.bus.InterStage.JumpBus
import utils.id.IDU
import utils.bypass.BypassTo
import utils.bypass.DataFromOtherStage
import chisel3.util.Mux1H
import utils.bypass
import utils.id.ControlSignals.OpASrc
import utils.id.ControlSignals.OpBSrc
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import chisel3.util.Fill
import utils.csr.CSRReadPort
import utils.id.Instructions._
import chisel3.util.RegEnable
import utils.ExceptionCodes
import chisel3.util.Cat
import chisel3.util.PriorityMux
import utils.csr.CsrConsts
import chisel3.util.Irrevocable
import utils.id.ControlSignals.FuType
import utils.id.ControlSignals.BRUOp

class ID(config: RVConfig) extends Module with CsrConsts{
    val io = IO(new Bundle{
        val fromIF = Flipped(Irrevocable(new IfIdBus(config)))
        val toEXE = Irrevocable(new IdExeBus(config))
        // val r = Flipped(Irrevocable(new R(config)))
        val readPort1 = Flipped(new RegReadPort(config))
        val readPort2 = Flipped(new RegReadPort(config))
        val csrReadPort = Flipped(new CSRReadPort(config))
        // val jumpBus = Irrevocable(new JumpBus(config))
        val bypassPort = Flipped(new BypassTo(config))
        val bypassDataFromOtherStage = new DataFromOtherStage(config)
        val fencei = Output(Bool())
        val flush = Input(Bool())
    })
    // make inst 0 if it's flushed or ifStage didn't fire the inst to idStage
    val inst = io.fromIF.bits.inst
    val stall = !io.bypassPort.newestDataGetable

    // 确保对一条指令, 只向后端发送一次有效指令
    val hasFired = RegEnable(false.B, false.B, io.toEXE.fire && io.fromIF.fire)
    when (io.toEXE.fire && !io.fromIF.fire && !stall){
        hasFired := true.B
    }

    // 提取读写寄存器所需要的地址
    val rs1Addr = inst(19, 15)
    val rs2Addr = inst(24, 20)
    val rdAddr  = inst(11,  7)

    // ebreak
    if (config.simulation){
        RawClockedVoidFunctionCall("ebreak", Option(Seq("inst")))(clock, !io.toEXE.bits.nop, inst)
    }

    // 译码
    val idu = Module(new IDU(config))
    idu.io.inst <> inst
    io.toEXE.bits.imm <> idu.io.imm
    val decodeBundle = idu.io.decodeBundle
    io.toEXE.bits.decodeBundle := decodeBundle
    io.toEXE.bits.pc <> io.fromIF.bits.pc

    io.toEXE.bits.decodeBundle.regWrite := idu.io.decodeBundle.regWrite & !io.toEXE.bits.nop
    io.toEXE.bits.decodeBundle.memRead  := idu.io.decodeBundle.memRead & Fill(4, !io.toEXE.bits.nop)
    io.toEXE.bits.decodeBundle.memWrite := idu.io.decodeBundle.memWrite & Fill(4, !io.toEXE.bits.nop)

    io.fencei <> idu.io.decodeBundle.fencei

    // 读寄存器
    io.readPort1.raddr <> rs1Addr
    io.readPort2.raddr <> rs2Addr
    val rs1DataFromReg = io.readPort1.rdata
    val rs2DataFromReg = io.readPort2.rdata
    io.toEXE.bits.rd <> rdAddr
    io.toEXE.bits.rs1 <> rs1Addr

    // 读 csr 寄存器
    // io.csrReadPort.raddr := Mux(inst === MRET, MEPC,
    //                         Mux(inst === ECALL, MTVEC, inst(31, 20)))
    val funct12 = inst(31, 20)
    val isECALL = inst(6, 0) === 0x73.U && funct12 === ECALL
    val isMRET  = inst(6, 0) === 0x73.U && funct12 === MRET
    // io.csrReadPort.raddr := PriorityMux(Seq(
    //     (isMRET) -> MEPC,
    //     // (funct12 === csr.funct12.ECALL) -> MTVEC,
    //     (true.B) -> inst(31, 20)
    // ))
    io.csrReadPort.raddr := Mux(isMRET, Mepc.U, inst(31, 20))
    io.toEXE.bits.csrData := io.csrReadPort.rdata
    io.toEXE.bits.funct12 := funct12
    io.toEXE.bits.mret    := isMRET

    // 检查异常
    io.toEXE.bits.hasException := (io.fromIF.bits.hasException || isECALL || !idu.io.legal) && !io.toEXE.bits.nop
    io.toEXE.bits.exceptionCode := PriorityMux(Seq(
        (!idu.io.legal) -> ExceptionCodes.IllegalInstruction,
        (isECALL)       -> ExceptionCodes.MachineEnvironmentCall,
        (true.B)          -> io.fromIF.bits.exceptionCode
    ))
    

    // bypass
    io.bypassPort.rs1 <> rs1Addr
    io.bypassPort.rs2 <> rs2Addr
    val newestDataGetable = Mux(decodeBundle.aluSrc1 === OpASrc.RS1 || decodeBundle.aluSrc2 === OpBSrc.RS2,
                                    io.bypassPort.newestDataGetable,
                                    true.B)
    val bypassTypeA = io.bypassPort.bypassTypeA
    val bypassTypeB = io.bypassPort.bypassTypeB
    val rs1Data = Mux1H(Seq(
        (bypassTypeA === bypass.BypassTypeEnum.FromEXE.asUInt) -> io.bypassDataFromOtherStage.fromEXE,
        (bypassTypeA === bypass.BypassTypeEnum.FromMEM.asUInt) -> io.bypassDataFromOtherStage.fromMEM,
        (bypassTypeA === bypass.BypassTypeEnum.FromWB .asUInt) -> io.bypassDataFromOtherStage.fromWB,
        (bypassTypeA === bypass.BypassTypeEnum.FromREG.asUInt) -> rs1DataFromReg
    ))
    val rs2Data = Mux1H(Seq(
        (bypassTypeB === bypass.BypassTypeEnum.FromEXE.asUInt) -> io.bypassDataFromOtherStage.fromEXE,
        (bypassTypeB === bypass.BypassTypeEnum.FromMEM.asUInt) -> io.bypassDataFromOtherStage.fromMEM,
        (bypassTypeB === bypass.BypassTypeEnum.FromWB .asUInt) -> io.bypassDataFromOtherStage.fromWB,
        (bypassTypeB === bypass.BypassTypeEnum.FromREG.asUInt) -> rs2DataFromReg
    ))
    io.toEXE.bits.rs1Data := rs1Data
    io.toEXE.bits.rs2Data := rs2Data

    if (config.staticBranchPrediction){
        io.toEXE.bits.branchPred := io.fromIF.bits.branchPred
    }

    // 在阻塞的情况下, 发送 nop
    // 在已经发送了指令的情况下, 发送nop, 确保每一条指令只从前端发射一次
    io.toEXE.bits.nop := io.fromIF.bits.nop || stall || hasFired || io.flush

    // 如果无法获得最新的寄存器数据, 则会向流水级下游发送 nop, 直到最新的寄存器数据被获取
    // 但在不能获取最新寄存器数据时候, 需要阻塞后方的指令
    io.toEXE.valid := true.B
    io.fromIF.ready := io.toEXE.ready && !stall

    // 性能计数器
    if (config.simulation){
        RawClockedVoidFunctionCall(
            "PerfCountIDU", Option(Seq("fuType"))
        )(clock, enable = io.toEXE.fire && !hasFired && idu.io.legal && !io.toEXE.bits.nop, Cat(0.U(4.W), idu.io.decodeBundle.fuType))
        // 若从preif发来的指令为nop, 即其就是被冲刷掉的指令
        RawClockedVoidFunctionCall(
            "PerfCountInstFlushed"
        )(clock, enable = io.toEXE.fire && !hasFired && io.toEXE.bits.nop)
    }
    // trace 与 difftest 相关
    // if (config.diff_enable) io.toEXE.bits.jumped := io.jumpBus.fire
    if (config.trace_enable) {
        io.toEXE.bits.inst := inst
        io.toEXE.bits.ftrace.doFtrace := decodeBundle.fuType === FuType.BRU && decodeBundle.bruOp === BRUOp.N
        io.toEXE.bits.ftrace.rd := rdAddr
        io.toEXE.bits.ftrace.rs1 := Mux(decodeBundle.aluSrc1 === OpASrc.PC, 0.U, rs1Addr)
    }
    // if (config.trace_enable) {
    //     RawClockedVoidFunctionCall(
    //         "ftrace", Option(Seq("pc", "target", "rd", "rs1"))
    //     )(clock, enable = decodeBundle.fuType === FuType.BRU && decodeBundle.bruOp === BRUOp.N,
    //     io.fromIF.bits.pc, )
    // }

}
