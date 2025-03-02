import utils.exe.LSU
import chisel3._
import chisel3.util.Irrevocable
import chisel3.util.RegEnable
import utils.RVConfig
import utils.bus.AXI4
import core._
import utils.RegFiles
import utils.exe.LSU
import utils.bypass.BypassNetwork
import verification.diff.DiffSignals
import verification.DebugSignals
import verification.trace.TraceSignals
import utils.csr.CSRRegFile
import utils.cache.ICacheConfig
import utils.cache.ICache
import chisel3.util.IrrevocableIO
import utils.bus.SRAMLike._

// case class RVConfig(
//     xlen: Int,
//     RVC: Boolean
// )

class Core(config: RVConfig) extends Module{
    val io = IO(new Bundle{
        // val ibus = AXI4(config)
        // val dbus = AXI4(config)
        val ibus = new Bundle{
            val req = Irrevocable(new SRAMLikeReq(config))
            val resp = Flipped(Irrevocable(new SRAMLikeRResp(config)))
        }
        val dbus = new Bundle{
            val req = Irrevocable(new SRAMLikeReq(config))
            val rResp = Flipped(Irrevocable(new SRAMLikeRResp(config)))
            val wResp = Flipped(Irrevocable(new SRAMLikeWResp(config)))
        }
        val debug: DebugSignals = if(config.debug_enable) new DebugSignals(config) else null
        val diff: DiffSignals = if(config.diff_enable) new DiffSignals(config) else null
        val trace: TraceSignals = if(config.trace_enable) new TraceSignals(config) else null
    })  
    private val iCacheConfig = ICacheConfig()

    // val preIfStage = Module(new PreIF(config))
    val ifStage = Module(new IF(config))
    val idStage = Module(new ID(config))
    val exeStage = Module(new EXE(config))
    val memStage = Module(new MEM(config))
    val wbStage = Module(new WB(config))
    
    val icache = Module(new ICache(config, iCacheConfig, skipSRAM = false, burstEnable = true))

    // StageConnect(preIfStage.io.toIF, ifStage.io.fromPreIF)
    StageConnect(ifStage.io.toID, idStage.io.fromIF)
    StageConnect(idStage.io.toEXE, exeStage.io.fromID)
    StageConnect(exeStage.io.toMEM, memStage.io.fromEXE)
    StageConnect(memStage.io.toWB, wbStage.io.fromMEM)
    icache.io.fencei <> idStage.io.fencei

    ifStage.io.ar <> icache.io.arFromIF
    icache.io.arToMem <> io.ibus.req
    ifStage.io.r  <> icache.io.rToIF
    icache.io.rFromMem <> io.ibus.resp
    ifStage.io.jumpBus <> exeStage.io.jumpBus
    // io.ibus.aw <> DontCare
    // io.ibus.w  <> DontCare
    // io.ibus.b  <> DontCare

    
    val regFiles = Module(new RegFiles(config))
    idStage.io.readPort1 <> regFiles.io.readPort1
    idStage.io.readPort2 <> regFiles.io.readPort2
    val csrRegs = Module(new CSRRegFile(config))
    idStage.io.csrReadPort <> csrRegs.io.readPort
    ifStage.io.excepCMD <> csrRegs.io.excpCMD

    // 当在执行级的 BRU 检测到分支错误, 执行级处理 CSR 指令, 写回级提交异常  --->  冲刷流水线
    val flush = exeStage.io.flush || csrRegs.io.excpCMD.valid
    ifStage.io.flush <> flush
    idStage.io.flush <> flush 

    val bypassNetwork = Module(new BypassNetwork(config))
    bypassNetwork.io.fromEXE <> exeStage.io.bypass
    bypassNetwork.io.fromMEM <> memStage.io.bypass
    bypassNetwork.io.fromWB  <> wbStage .io.bypass
    bypassNetwork.io.toID    <> idStage .io.bypassPort
    idStage.io.bypassDataFromOtherStage.fromEXE <> exeStage.io.regWdata
    idStage.io.bypassDataFromOtherStage.fromMEM <> memStage.io.regWdata
    idStage.io.bypassDataFromOtherStage.fromWB  <> wbStage.io.regWdata


    io.dbus.req <> exeStage.io.req
    // io.dbus.aw <> exeStage.io.aw
    // io.dbus.w  <> exeStage.io.w

    io.dbus.rResp <> memStage.io.rResp
    io.dbus.wResp <> memStage.io.wResp
    // io.dbus.b <> memStage.io.b

    wbStage.io.writePort <> regFiles.io.writePort
    wbStage.io.csrWritePort <> csrRegs.io.writePort
    wbStage.io.csrCmd <> csrRegs.io.cmd
    if (config.debug_enable){
        wbStage.io.debug <> io.debug
    }
    if (config.diff_enable){
        wbStage.io.diff <> io.diff
    }
    if (config.trace_enable){
        wbStage.io.trace <> io.trace
    }
}


object StageConnect{
    import chisel3.util.IrrevocableIO
    def apply[T <: Data](left: IrrevocableIO[T], right: IrrevocableIO[T]) = {
        left.ready := right.ready
        right.bits := RegEnable(left.bits, left.fire)
        right.valid := left.valid
    }
}