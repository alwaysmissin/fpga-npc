package core
import chisel3._
import utils.RVConfig
import utils.bus.InterStage.{IfIdBus, JumpBus}
import chisel3.util.Irrevocable
import chisel3.util.RegEnable
import utils.bus.{AR, R, AXIBURST}
import utils.bus.AXIBURST
import utils.bus.InterStage.PreIFIFBus
import utils.ExceptionCodes
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import chisel3.util.Irrevocable
import utils.csr.RedirectCMD
import chisel3.util.PriorityMux
import utils.bus.SRAMLike.SRAMLikeReq

class PCBundle(config: RVConfig) extends Bundle{
    val npc = Output(UInt(config.xlen.W))
    val pc = Output(UInt(config.xlen.W))
}

// class PCManager(config: RVConfig) extends Module{
//     val io = IO(new Bundle{
//         val jumpBus = Irrevocable(new JumpBus(config))
//         val pcBundle = new PCBundle(config)
//     })
//     val PC = RegNext(io.pcBundle.npc, config.PC_INIT)
//     io.pcBundle.npc := Mux(io.jumpBus.fire, io.jumpBus.bits.jumpTarget, PC + 4.U)
//     io.pcBundle.pc := PC
// }

class PreIF(config: RVConfig) extends Module with ExceptionCodes{
    val io = IO(new Bundle{
        // val ibus = new IBus(config)
        // val ar = Irrevocable(new AR(config))
        val ar = Irrevocable(new SRAMLikeReq(config))
        // val r  = Flipped(Irrevocable(new R(config)))
        val jumpBus = Flipped(Irrevocable(new JumpBus(config)))
        val excepCMD = Flipped(Irrevocable(new RedirectCMD(config)))
        val toIF = Irrevocable(new PreIFIFBus(config))
        val flush = Input(Bool())
    })
    io.jumpBus.ready := io.toIF.valid
    io.excepCMD.ready := io.toIF.valid

    val npc = Wire(UInt(config.xlen.W))
    val PC = RegEnable(npc, config.PC_INIT, io.toIF.fire)
    npc := PriorityMux(Seq(
        (io.excepCMD.fire) -> io.excepCMD.bits.target,
        (io.jumpBus.fire) -> io.jumpBus.bits.jumpTarget,
        (true.B) -> (PC + 4.U)
    ))
    // npc := Mux(io.jumpBus.fire, io.jumpBus.bits.jumpTarget, PC + 4.U)
    io.toIF.bits.hasException := (PC(1, 0) =/= 0.U) && !io.toIF.bits.nop
    io.toIF.bits.exceptionCode := InstructionAddressMisaligned.U

    // 使用pc进行取指
    val reqFired = RegEnable(false.B, false.B, io.toIF.fire)
    io.ar.valid := !reset.asBool && (!reqFired)
    // io.r.ready := io.r.valid && !instGetted
    io.ar.bits.wr   := false.B // do read
    io.ar.bits.size := 2.U
    io.ar.bits.addr := PC
    io.ar.bits.len := 0.U
    io.ar.bits.wdata := DontCare
    io.ar.bits.wstrb := DontCare

    // 当前指令被冲刷
    io.toIF.bits.nop := io.jumpBus.fire || io.flush
    // io.toIF.bits.inst := Mux(instGetted, inst, io.r.bits.data)
    io.toIF.bits.pc := PC

    when(io.ar.fire && !reqFired && !io.toIF.fire){
        reqFired := true.B
    }
    val reqValid = reqFired || io.ar.fire

    // io.toIF.valid := !reset.asBool && (instValid || PC === config.PC_INIT) && reqValid
    io.toIF.valid := reqValid

    if (config.simulation){
        RawClockedVoidFunctionCall(
            "IFULaunchARReq"
        )(clock, enable = io.ar.fire)
    }
}