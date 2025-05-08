package utils.exe

import chisel3._
import utils.RVConfig
import chisel3.util.Irrevocable
import chisel3.util.Cat
import chisel3.util._
import utils.Config.FPGAPlatform

class MultiplierFPGA(dataWidth: Int = 32, latency: Int = 0) extends BlackBox {
  override val desiredName = "multiplier"
  val io = IO(new Bundle {
    val CLK = Input(Bool())
    val A = Input(UInt(dataWidth.W))
    val B = Input(UInt(dataWidth.W))
    val P = Output(UInt((dataWidth * 2).W))
  })
}

class MultiplierSim(dataWidth: Int = 32, latency: Int = 0) extends Module {
  val io = IO(new Bundle {
    val A = Input(UInt(dataWidth.W))
    val B = Input(UInt(dataWidth.W))
    val P = Output(UInt((dataWidth * 2).W))
  })
  io.P := io.A * io.B
}

// class MultiplierGeneral(dataWidth: Int = 32) extends Module{

// }

class Multiplier(config: RVConfig, latency: Int = 0) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Irrevocable(new Bundle {
      val A = UInt(config.xlen.W)
      val B = UInt(config.xlen.W)
    }))
    val resp = Irrevocable(new Bundle {
      val P = UInt((config.xlen * 2).W)
    })
  })
  val busy = RegInit(false.B)
  val count = RegInit(0.U(log2Ceil(latency + 1).W))
  val result = Reg(UInt((config.xlen * 2).W))
  if (FPGAPlatform) {
    val multiplier = Module(new MultiplierFPGA(latency = latency))
    multiplier.io.CLK := clock.asBool
    multiplier.io.A <> io.req.bits.A
    multiplier.io.B <> io.req.bits.B
    io.resp.bits.P <> multiplier.io.P
    // 请求处理逻辑
    when(io.req.fire) {
      busy := true.B
      count := (latency - 1).U
    }

    // 计数器递减逻辑
    when(busy && count =/= 0.U) {
      count := count - 1.U
    }

    // 响应控制逻辑
    val done = busy && (count === 0.U)
    io.resp.valid := done

    // 完成握手后重置状态
    when(io.resp.fire) {
      busy := false.B
    }

    // 流控信号
    io.req.ready := !busy
  } else {
// 非FPGA平台仿真实现
    if (latency == 0) {
      // 组合逻辑零延迟模式
      io.req.ready := io.resp.ready
      io.resp.valid := io.req.valid
      io.resp.bits.P := io.req.bits.A * io.req.bits.B
    } else {
      // 带延迟的时序逻辑模式

      // 请求处理逻辑
      when(io.req.fire) {
        busy := true.B
        count := (latency - 1).U
        result := io.req.bits.A * io.req.bits.B // 立即计算结果
      }

      // 计数器递减逻辑
      when(busy && count =/= 0.U) {
        count := count - 1.U
      }

      // 响应控制逻辑
      val done = busy && (count === 0.U)
      io.resp.valid := done
      io.resp.bits.P := result

      // 完成握手后重置状态
      when(io.resp.fire) {
        busy := false.B
      }

      // 流控信号
      io.req.ready := !busy
    }
  }
}
