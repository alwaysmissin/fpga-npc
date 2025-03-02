package utils.csr

import chisel3._
import utils.RVConfig
import chisel3.util._
import utils.csr.CSRRegAddr._
import upickle.default
import utils.id.ControlLogic
import utils.ExceptionCodes

class CSRReadPort(config: RVConfig) extends Bundle{
    val raddr = Input(UInt(config.csr_width.W))
    val rdata = Output(UInt(config.xlen.W))
}

class CSRWritePort(config: RVConfig) extends Bundle{
    val wen = Input(Bool())
    val waddr = Input(UInt(config.csr_width.W))
    val wdata = Input(UInt(config.xlen.W))
}

class CSRCMD(config: RVConfig) extends Bundle{
    // val cmd = Input(UInt(CSRCMD.WIDTH.W))
    // val funct12 = Input(UInt(config.csr_width.W))
    val hasExcep = Input(Bool())
    val excepCode = Input(ExceptionCodes())
    val mret = Input(Bool())
    // val pc      = Input(UInt(config.xlen.W))
}

class FlushCMD(config: RVConfig) extends Bundle{
    // val flush = Output(Bool())
    val target    = Output(UInt(config.xlen.W))
}

class CSRRegFile(config: RVConfig) extends Module {
    val io = IO(new Bundle{
        val readPort = new CSRReadPort(config)
        val writePort = new CSRWritePort(config)
        val cmd = new CSRCMD(config)
        // val excpCMD = new FlushCMD(config)
        val excpCMD = Irrevocable(new FlushCMD(config))
    })

    val mstatus = RegInit(0x00001800.U(config.xlen.W))
    val mepc    = RegInit(0.U(config.xlen.W))
    val mcause  = RegInit(0.U(config.xlen.W))
    val mtvec   = RegInit(0.U(config.xlen.W))
    val mvendorid = 0x79737978.U(config.xlen.W)
    val marchid = 0x23060051.U(config.xlen.W)
    // dontTouch(mvendorid)
    // dontTouch(marchid)

    // io.excpCMD.flush := io.cmd.hasExcep || io.cmd.mret
    // // io.excpCMD.target <> mtvec
    // io.excpCMD.target := PriorityMux(Seq(
    //     io.cmd.hasExcep     -> mtvec,
    //     io.cmd.mret         -> mepc,
    // ))

    val enq = Wire(Irrevocable(Bool()))
    val flushQ = Queue.irrevocable(enq, 1, flow = true)
    enq.valid := io.cmd.hasExcep || io.cmd.mret
    val selMTvec = io.cmd.hasExcep
    enq.bits := selMTvec
    flushQ.ready := io.excpCMD.ready
    io.excpCMD.bits.target := Mux(flushQ.bits, mtvec, mepc)
    io.excpCMD.valid := flushQ.valid

    // 处理mret
    
    
    // set 7th bit of mstatus when mret
    when (io.cmd.mret){
        mstatus := mstatus | "h00000080".U
    }
    
    when(io.cmd.hasExcep){
        mcause := Cat(0.U((config.xlen - ExceptionCodes.getWidth).W), io.cmd.excepCode.asUInt)
        mepc   := io.writePort.wdata
        mstatus := mstatus.bitSet(7.U, mstatus(3))
    }
    when(io.writePort.wen){
        switch(io.writePort.waddr){
            is(MSTATUS){
                mstatus := io.writePort.wdata & "h00207888".U | "h00001800".U
            }
            is(MEPC){
                mepc    := io.writePort.wdata
            }
            is(MCAUSE){
                mcause  := io.writePort.wdata
            }
            is(MTVEC){
                mtvec   := io.writePort.wdata
            }
            // is(MVENDORID){
            //     mvendorid := io.writePort.wdata
            // }
            // is(MARCHID){
            //     marchid := io.writePort.wdata
            // }
        }
    }
    // when(io.writePort.wen){
    //     switch(io.cmd.funct12){
    //         is(funct12.ECALL){
    //             mcause := 0x0000000b.U
    //             mepc := io.writePort.wdata
    //         }
    //         is(funct12.MRET){
    //             mstatus := mstatus | "h00000080".U
    //         }
    //     }
    //     switch(io.writePort.waddr){
    //         is(MSTATUS){
    //             mstatus := io.writePort.wdata & "h00207888".U | "h00001800".U
    //         }
    //         is(MEPC){
    //             mepc    := io.writePort.wdata
    //         }
    //         is(MCAUSE){
    //             mcause  := io.writePort.wdata
    //         }
    //         is(MTVEC){
    //             mtvec   := io.writePort.wdata
    //         }
    //         is(MVENDORID){
    //             mvendorid := io.writePort.wdata
    //         }
    //         is(MARCHID){
    //             marchid := io.writePort.wdata
    //         }
    //     }
    // }
    // io.readPort.rdata := 0.U
    // switch(io.readPort.raddr){
    //     is(MSTATUS){
    //         io.readPort.rdata := mstatus
    //     }
    //     is(MEPC){
    //         io.readPort.rdata := mepc
    //     }
    //     is(MCAUSE){
    //         io.readPort.rdata := mcause
    //     }
    //     is(MTVEC){
    //         io.readPort.rdata := mtvec
    //     }
    //     is(MVENDORID){
    //         io.readPort.rdata := mvendorid
    //     }
    //     is(MARCHID){
    //         io.readPort.rdata := marchid
    //     }
    // }
    io.readPort.rdata := Mux1H(Seq(
        (io.readPort.raddr === MSTATUS) -> mstatus,
        (io.readPort.raddr === MEPC)    -> mepc,
        (io.readPort.raddr === MCAUSE)  -> mcause,
        (io.readPort.raddr === MTVEC)   -> mtvec,
        (io.readPort.raddr === MVENDORID) -> mvendorid,
        (io.readPort.raddr === MARCHID) -> marchid
    ))
  
}
