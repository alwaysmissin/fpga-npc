package utils.bypass

import chisel3._
import utils.RVConfig
import utils.id.ControlSignals.FuType
import chisel3.util.log2Up
import utils.id.ControlSignals.{OpASrc, OpBSrc}
import chisel3.util.PriorityMux

class BypassFrom(config: RVConfig) extends Bundle{
    // val fuType = Input(UInt(FuType.WIDTH.W))
    val valid = Input(Bool())
    val regWrite = Input(Bool())
    val waddr = Input(UInt(log2Up(config.nr_reg).W))
}

class BypassTo(config: RVConfig) extends Bundle{
    val rs1 = Input(UInt(log2Up(config.nr_reg).W))
    val rs2 = Input(UInt(log2Up(config.nr_reg).W))
    val newestDataGetable = Output(Bool())
    val bypassTypeA = Output(UInt(BypassTypeEnum.getWidth.W))
    val bypassTypeB = Output(UInt(BypassTypeEnum.getWidth.W))
}

class DataFromOtherStage(config: RVConfig) extends Bundle{
    val fromEXE = Input(UInt(config.xlen.W))
    val fromMEM = Input(UInt(config.xlen.W))
    val fromWB  = Input(UInt(config.xlen.W))
}

class BypassNetwork(config: RVConfig) extends Module {
    val io = IO(new Bundle{
        val fromEXE = new BypassFrom(config)
        val fromMEM = new BypassFrom(config)
        val fromWB  = new BypassFrom(config)
        val toID    = new BypassTo(config)
    })

    io.toID.bypassTypeA := PriorityMux(Seq(
        (io.toID.rs1 =/= 0.U && io.fromEXE.waddr === io.toID.rs1 && io.fromEXE.regWrite) -> BypassTypeEnum.FromEXE.asUInt,
        (io.toID.rs1 =/= 0.U && io.fromMEM.waddr === io.toID.rs1 && io.fromMEM.regWrite) -> BypassTypeEnum.FromMEM.asUInt,
        (io.toID.rs1 =/= 0.U && io.fromWB .waddr === io.toID.rs1 && io.fromWB .regWrite) -> BypassTypeEnum.FromWB .asUInt,
        true.B -> BypassTypeEnum.FromREG.asUInt,
    ))

    io.toID.bypassTypeB := PriorityMux(Seq(
        (io.toID.rs2 =/= 0.U && io.fromEXE.waddr === io.toID.rs2 && io.fromEXE.regWrite) -> BypassTypeEnum.FromEXE.asUInt,
        (io.toID.rs2 =/= 0.U && io.fromMEM.waddr === io.toID.rs2 && io.fromMEM.regWrite) -> BypassTypeEnum.FromMEM.asUInt,
        (io.toID.rs2 =/= 0.U && io.fromWB .waddr === io.toID.rs2 && io.fromWB .regWrite) -> BypassTypeEnum.FromWB .asUInt,
        true.B -> BypassTypeEnum.FromREG.asUInt,
    ))

    // io.toID.newestDataGetable := !((io.toID.bypassTypeA === BypassTypeEnum.FromEXE.asUInt && io.toID.opASrc === OpASrc.RS1 && io.fromEXE.fuType === FuType.LSU)
    //                             ||
    //                               (io.toID.bypassTypeB === BypassTypeEnum.FromEXE.asUInt && io.toID.opBSrc === OpBSrc.RS2 && io.fromEXE.fuType === FuType.LSU)
    //                             ||
    //                               (io.toID.bypassTypeB === BypassTypeEnum.FromMEM.asUInt && io.toID.opBSrc === OpBSrc.RS2 && io.fromMEM.fuType === FuType.LSU)
    //                             ||
    //                               (io.toID.bypassTypeA === BypassTypeEnum.FromMEM.asUInt && io.toID.opASrc === OpASrc.RS1 && io.fromMEM.fuType === FuType.LSU))
    // 牺牲一定性能, 替换为此
    io.toID.newestDataGetable := !((io.toID.bypassTypeA === BypassTypeEnum.FromEXE.asUInt && !io.fromEXE.valid) ||
                                   (io.toID.bypassTypeB === BypassTypeEnum.FromEXE.asUInt && !io.fromEXE.valid) ||
                                   (io.toID.bypassTypeA === BypassTypeEnum.FromMEM.asUInt && !io.fromMEM.valid) ||
                                   (io.toID.bypassTypeB === BypassTypeEnum.FromMEM.asUInt && !io.fromMEM.valid))
  
}
