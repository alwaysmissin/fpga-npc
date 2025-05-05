package utils.csr

import chisel3._
import utils.RVConfig
import chisel3.util._
import upickle.default
import utils.id.ControlLogic
import utils.ExceptionCodes
import utils.nutshellUtils.GenMask
import utils.nutshellUtils.ZeroExt
import utils.nutshellUtils.MaskedRegMap
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall

class CSRReadPort(config: RVConfig) extends Bundle {
  val raddr = Input(UInt(config.csr_width.W))
  val rdata = Output(UInt(config.xlen.W))
}

class CSRWritePort(config: RVConfig) extends Bundle {
  val wen = Input(Bool())
  val waddr = Input(UInt(config.csr_width.W))
  val wdata = Input(UInt(config.xlen.W))
}

class ExcepCMD(config: RVConfig) extends Bundle {
  // val cmd = Input(UInt(CSRCMD.WIDTH.W))
  // val funct12 = Input(UInt(config.csr_width.W))
  // val hasExcep = Input(Bool())
  val excepVec = Input(Vec(16, Bool()))
  // val excepCode = Input(UInt(4.W))
  val mret = Input(Bool())
  val pc = Input(UInt(config.xlen.W))
}

class RedirectCMD(config: RVConfig) extends Bundle {
  // val flush = Output(Bool())
  val target = Output(UInt(config.xlen.W))
}

object PRIV extends ChiselEnum {
  val User, Supervisor, Reserved, Machine = Value
}

class MstatusStruct extends Bundle {
  val sd = UInt(1.W)
  val pad0 = UInt(8.W)
  val tsr = UInt(1.W)
  val tw = UInt(1.W)
  val tvm = UInt(1.W)
  val mxr = UInt(1.W)
  val sum = UInt(1.W)
  val mprv = UInt(1.W)
  val xs = UInt(2.W)
  val fs = UInt(2.W)
  val mpp = UInt(2.W)
  val vs = UInt(2.W)
  val spp = UInt(1.W)
  val mpie = UInt(1.W)
  val ube = UInt(1.W)
  val spie = UInt(1.W)
  val pad1 = UInt(1.W)
  val mie = UInt(1.W)
  val pad2 = UInt(1.W)
  val sie = UInt(1.W)
  val pad3 = UInt(1.W)
}

class Interrupt extends Bundle {
  val eip = new Bundle {
    val m = Bool()
    val pad0 = Bool()
    val s = Bool()
    val pad1 = Bool()
  }
  val tip = new Bundle {
    val m = Bool()
    val pad0 = Bool()
    val s = Bool()
    val pad1 = Bool()
  }
  val sip = new Bundle {
    val m = Bool()
    val pad0 = Bool()
    val s = Bool()
    val pad1 = Bool()
  }
}

class InterruptSimple extends Bundle {
  val eip = Bool()
  val tip = Bool()
  val sip = Bool()
}

class InterruptCMD(config: RVConfig) extends Bundle {
  val pc = Input(UInt(config.xlen.W))
}

class CSRRegFile(config: RVConfig)
    extends Module
    with CsrConsts
    with ExceptionCodes {
  val io = IO(new Bundle {
    val readPort = new CSRReadPort(config)
    val writePort = new CSRWritePort(config)
    val excepCmd = new ExcepCMD(config)
    // val excpCMD = new FlushCMD(config)
    val intrCmd = Flipped(Irrevocable(new InterruptCMD(config)))
    val redirectCmd = Irrevocable(new RedirectCMD(config))
    val interrupt = Input(new InterruptSimple())
  })
  val privilege = RegInit(PRIV.Machine)

  val mstatus = RegInit(0x00001800.U(config.xlen.W))
  val mstatusStruct = mstatus.asTypeOf(new MstatusStruct)
  val mie = RegInit(0.U(config.xlen.W))
  val mipReg = RegInit(0.U(config.xlen.W))
  val mtvec = RegInit(0.U(config.xlen.W))
  val menvcfg = RegInit(0.U(config.xlen.W))
  val mscratch = RegInit(0.U(config.xlen.W))
  val mepc = RegInit(0.U(config.xlen.W))
  val mcause = RegInit(0.U(config.xlen.W))
  val mtval = RegInit(0.U(config.xlen.W))
  val pmpcfg0 = RegInit(0.U(config.xlen.W))
  val pmpaddr0 = RegInit(0.U(config.xlen.W))
  val mvendorid = RegInit(0x79737978.U(config.xlen.W))
  val marchid = RegInit(0x23060051.U(config.xlen.W))
  val mimpid = RegInit(0.U(config.xlen.W))
  val mhartid = RegInit(0.U(config.xlen.W))

  val mipWire = WireDefault(0.U.asTypeOf(new Interrupt))
  mipWire.sip.m := io.interrupt.sip
  mipWire.tip.m := io.interrupt.tip
  mipWire.eip.m := io.interrupt.eip
  val mip = (mipWire.asUInt | mipReg).asTypeOf(new Interrupt)
  dontTouch(mip)
  // dontTouch(mvendorid)
  // dontTouch(marchid)

  // io.excpCMD.flush := io.cmd.hasExcep || io.cmd.mret
  // // io.excpCMD.target <> mtvec
  // io.excpCMD.target := PriorityMux(Seq(
  //     io.cmd.hasExcep     -> mtvec,
  //     io.cmd.mret         -> mepc,
  // ))

  val setMtval = WireDefault(false.B)
  val setMtval_val = WireDefault(0.U(config.xlen.W))
  when (setMtval){
    mtval := setMtval_val
  }

  // handle mret
  when(io.excepCmd.mret) {
    val mstatusOld = WireDefault(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = WireDefault(mstatus.asTypeOf(new MstatusStruct))
    mstatusNew.mie := mstatusOld.mpie
    mstatusNew.mpie := true.B
    privilege := mstatusOld.mpp.asTypeOf(privilege)
    mstatusNew.mpp := 0.U
    mstatus := mstatusNew.asUInt
  }

  // handle interrupt and exception
  val intrVec = mie(11, 0) & mip.asUInt
  val intrNo =
    IntPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(intrVec(i), i.U, sum))
  val raiseIntr = intrVec.orR && mstatusStruct.mie.asBool
  val raiseExcep = io.excepCmd.excepVec.asUInt.orR
  val excepNo = ExcepPriority.foldRight(0.U)((i: Int, sum: UInt) =>
    Mux(io.excepCmd.excepVec(i), i.U, sum)
  )
  val causeNO =
    (raiseIntr << (config.xlen - 1)) | Mux(raiseIntr, intrNo, excepNo)
  val tvalClear = !(excepNo === InstructionAccessFault.U ||
    excepNo === InstructionAddressMisaligned.U ||
    excepNo === LoadAccessFault.U ||
    excepNo === LoadAddressMisaligned.U ||
    excepNo === StoreAMOAccessFault.U ||
    excepNo === StoreAMOAddressMisaligned.U) || raiseIntr

  val hadInterrupt = RegInit(false.B)
  when(raiseIntr) {
    hadInterrupt := true.B
  }.elsewhen(io.redirectCmd.fire) {
    hadInterrupt := false.B
  }
  io.intrCmd.ready := (raiseIntr || hadInterrupt) && io.redirectCmd.valid
  val raiseIntrExcep = raiseExcep || raiseIntr
  when(raiseIntrExcep) {
    mcause := causeNO
    mepc := Mux(raiseIntr, io.intrCmd.bits.pc, io.excepCmd.pc)
    val mstatusOld = WireDefault(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = WireDefault(mstatus.asTypeOf(new MstatusStruct))
    mstatusNew.mpie := mstatusOld.mie
    mstatusNew.mie := false.B
    mstatusNew.mpp := privilege.asTypeOf(mstatusNew.mpp)
    privilege := PRIV.Machine
    mstatus := mstatusNew.asUInt
    when(tvalClear) { mtval := 0.U }
    if (config.diff_enable) {
      RawClockedVoidFunctionCall(
        "diff_raise_intr",
        Option(Seq("causeNO", "epc"))
      )(clock, enable = raiseIntr, causeNO, io.intrCmd.bits.pc)
    }
  }

  val enq = Wire(Irrevocable(Bool()))
  val flushQ = Queue.irrevocable(enq, 1, flow = true)
  enq.valid := raiseIntrExcep || io.excepCmd.mret
  val selMTvec = raiseIntrExcep
  enq.bits := selMTvec
  flushQ.ready := io.redirectCmd.ready
  io.redirectCmd.bits.target := Mux(flushQ.bits, mtvec, mepc)
  io.redirectCmd.valid := flushQ.valid

  val mstatusMask = (~ZeroExt(
    (
      GenMask(31) |
        GenMask(30, 23) |
        GenMask(16, 15) |
        GenMask(6) |
        GenMask(4) |
        GenMask(2) |
        GenMask(0)
    ),
    32
  )).asUInt

  val mapping = Map(
    MaskedRegMap(Mstatus, mstatus, mstatusMask),
    MaskedRegMap(Mie, mie),
    MaskedRegMap(
      Mip,
      mip.asUInt,
      MaskedRegMap.UnwritableMask,
      MaskedRegMap.Unwritable
    ),
    MaskedRegMap(Mtvec, mtvec),
    MaskedRegMap(Menvcfg, menvcfg),
    MaskedRegMap(Mscratch, mscratch),
    MaskedRegMap(Mepc, mepc),
    MaskedRegMap(Mcause, mcause),
    MaskedRegMap(Mtval, mtval),
    MaskedRegMap(Pmpcfg0, pmpcfg0),
    MaskedRegMap(Pmpaddr0, pmpaddr0),
    MaskedRegMap(
      Mvendorid,
      mvendorid,
      MaskedRegMap.UnwritableMask,
      MaskedRegMap.Unwritable
    ),
    MaskedRegMap(
      Marchid,
      marchid,
      MaskedRegMap.UnwritableMask,
      MaskedRegMap.Unwritable
    ),
    MaskedRegMap(
      Mimpid,
      mimpid,
      MaskedRegMap.UnwritableMask,
      MaskedRegMap.Unwritable
    ),
    MaskedRegMap(
      Mhartid,
      mhartid,
      MaskedRegMap.UnwritableMask,
      MaskedRegMap.Unwritable
    )
  )

  val rdata = Wire(UInt(config.xlen.W))
  io.readPort.rdata := rdata
  MaskedRegMap.generate(
    mapping,
    io.readPort.raddr,
    rdata,
    io.writePort.waddr,
    io.writePort.wen,
    io.writePort.wdata
  )

}
