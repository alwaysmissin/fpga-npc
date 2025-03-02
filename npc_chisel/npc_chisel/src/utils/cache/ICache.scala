package utils.cache

import chisel3._
import chisel3.util._
import utils.RVConfig
import utils.bus.{AR, R}
import utils.bus.AXIBURST
import chisel3.util.switch
import chisel3.util.circt.dpi.RawClockedVoidFunctionCall
import utils.bus.SRAMLike.SRAMLikeReq
import utils.bus.SRAMLike.SRAMLikeRResp

final case class ICacheInfo(
  config: CacheBasicConfig,
) extends Bundle {
  val tags = Vec(config.ways, UInt(config.tagWidth.W))
  val lru =  if (config.ways != 1) UInt(log2Up(config.ways).W) else null
}

object ICacheState extends ChiselEnum{
  val IDLE, READ, COMMIT, FINISH = Value
}

class ICache(config: RVConfig, icache: CacheBasicConfig, skipSRAM: Boolean = true, burstEnable: Boolean = true) extends Module{
  assert(icache.ways <= 2, "ICache only support 2-way-associate cache or direct map cache")
  import ICacheState._
  val io = IO(new Bundle{
    // val arFromIF = Flipped(Irrevocable(new AR(config)))
    // val rToIF = Irrevocable(new R(config))
    // val arToMem = Irrevocable(new AR(config))
    // val rFromMem = Flipped(Irrevocable(new R(config)))
    val arFromIF = Flipped(Irrevocable(new SRAMLikeReq(config)))
    val rToIF    = Irrevocable(new SRAMLikeRResp(config))
    val arToMem  = Irrevocable(new SRAMLikeReq(config))
    val rFromMem = Flipped(Irrevocable(new SRAMLikeRResp(config)))
    val fencei = Input(Bool())
  })
  val valids = Reg(Vec(icache.blocks, Vec(icache.ways, Bool())))
  val dataRams = Seq.fill(icache.ways)(
    SyncReadMem(icache.blocks, Vec(icache.blockWords, UInt(config.xlen.W)))
  )
  val infoRams = SyncReadMem(icache.blocks, new ICacheInfo(icache))

  val reqValid = RegEnable(true.B, false.B, io.arFromIF.fire)
  when (io.rToIF.fire && !io.arFromIF.fire){
    reqValid := false.B
  }
  val req = RegEnable(io.arFromIF.bits, io.arFromIF.fire)
  val blockValids = Reg(Vec(icache.ways, Bool()))
  val indexPreIF = Wire(UInt(icache.indexWidth.W))
  val preIF = {
    val addr = io.arFromIF.bits.addr
    // get info from info ram
    indexPreIF := Mux(io.arFromIF.fire, addr(icache.indexRangeHi, icache.indexRangeLo), req.addr(icache.indexRangeHi, icache.indexRangeLo))
    blockValids := valids(indexPreIF)
  }

  
  val dataResp = Seq.fill(icache.ways)(Wire(UInt(config.xlen.W)))
  val infoResp = Wire(new ICacheInfo(icache))
  val hit = Wire(Bool())
  val index = req.addr(icache.indexRangeHi, icache.indexRangeLo)
  val offset = req.addr(icache.offsetRangeHi, icache.offsetRangeLo)
  val tag = req.addr(icache.tagRangeHi, icache.tagRangeLo)
  val wordOffset = {
    if (icache.wordOffsetRangeHi < icache.wordOffsetRangeLo) 0.U 
    else req.addr(icache.wordOffsetRangeHi, icache.wordOffsetRangeLo)
  }
  val hitData = Wire(UInt(config.xlen.W))
  val IF = {
    for (i <- 0 until icache.ways){
      dataResp(i) := dataRams(i).read(indexPreIF)(wordOffset)
    }
    infoResp := infoRams.read(indexPreIF)
    val hits = blockValids.zip(infoResp.tags).map {case(valid, t) => valid && t === tag}
    hit := hits.reduce(_ || _)
    hitData := Mux1H(hits, dataResp)
    if (icache.ways != 1){
      when (hit){
        val newInfo = Wire(infoResp.copy())
        newInfo.tags := infoResp.tags
        newInfo.lru := hits(0)
        infoRams.write(index, newInfo)
      }
    }
  }

  val arToMemW = WireDefault(0.U.asTypeOf(io.arToMem))

  val state = RegInit(IDLE)
  // val counter = RegInit(0.U(log2Ceil(icache.blockWords).W))

  val reqCounter = RegInit(0.U(log2Ceil(icache.blockWords).W))
  val respCounter = RegInit(0.U(log2Ceil(icache.blockWords).W))

  val replaceWay = RegInit(0.U(log2Ceil(icache.ways).W))
  val inst2Fire = RegInit(0.U(config.xlen.W))
  when (respCounter === wordOffset && io.rFromMem.fire){
    inst2Fire := io.rFromMem.bits.rdata
  }

  val preIFSkipCache = Wire(Bool())
  val IFSkipCache = Wire(Bool())
  if (skipSRAM){
    preIFSkipCache := (io.arFromIF.bits.addr >= 0x0f000000.U && io.arFromIF.bits.addr < (0x0f000000 + 0x2000).U)
    IFSkipCache := (req.addr >= 0x0f000000.U && req.addr < (0x0f000000 + 0x2000).U)
  } else {
    preIFSkipCache := false.B
    IFSkipCache := false.B
  }

  val burstable = if (burstEnable) req.addr >= 0xa0000000L.U && req.addr < (0xa2000000L + 0x2000000).U else false.B
  when (io.fencei){
    for (i <- 0 until icache.blocks){
      for (j <- 0 until icache.ways){
        valids(i)(j) := false.B
      }
    }
  }
  switch(state){
    is (IDLE) {
      when (reqValid && !hit && !IFSkipCache){
        arToMemW.valid := true.B
        arToMemW.bits.addr := Cat(req.addr(31, icache.offsetWidth), 0.U(icache.offsetWidth.W))
        arToMemW.bits.len  := Mux(burstable, (icache.blockWords - 1).U, req.len)
        arToMemW.bits.size := req.size
        if (icache.ways == 1){
          replaceWay := 0.U
        } else {
          replaceWay := infoResp.lru
        }
        when (io.arToMem.fire){
          reqCounter := reqCounter + 1.U
          state := {if(icache.blockWords == 1) COMMIT else Mux(burstable, COMMIT, READ)}
        }
      }
    }
    is (READ) {
      when (io.rFromMem.fire){
        for (i <- 0 until icache.ways){
          when (i.U === replaceWay){
            val wPort = dataRams(i)(index)
            wPort(respCounter) := io.rFromMem.bits.rdata
          }
        }
        respCounter := respCounter + 1.U
      }
      when (arToMemW.fire){
        reqCounter := reqCounter + 1.U
        when (reqCounter === icache.blockWords.U - 1.U){
          state := COMMIT
        }
      }
      // continue to launch ar request
      arToMemW.valid := true.B
      arToMemW.bits.addr := Cat(req.addr(31, icache.offsetWidth), 0.U(icache.offsetWidth.W)) + ((reqCounter) << 2)
      arToMemW.bits.len  := req.len
      arToMemW.bits.size := req.size
    }
    is (COMMIT) {
      when (io.rFromMem.fire){
        for (i <- 0 until icache.ways){
          when (i.U === replaceWay){
            val wPort = dataRams(i)(index)
            wPort(respCounter) := io.rFromMem.bits.rdata
          }
        }
        respCounter := respCounter + 1.U
        when (respCounter === (icache.blockWords - 1).U){
          state := FINISH
        }
        val newInfo = Wire(infoResp.copy())
        for (i <- 0 until icache.ways){
          when (i.U === replaceWay){
            newInfo.tags(i) := tag
          }.otherwise{
            newInfo.tags(i) := infoResp.tags(i)
          }
        }
        if (icache.ways != 1){
          newInfo.lru := ~infoResp.lru
        }
        // newInfo.tags(replaceWay) := tag
        // set the valid bit
        valids(index)(replaceWay) := true.B
        infoRams.write(index, newInfo)
      }
    }
    is (FINISH) {
      reqCounter := 0.U
      respCounter := 0.U
      when(io.rToIF.fire){
        state := IDLE
      }
    }
  }

  when (preIFSkipCache && (state === IDLE) && ((reqValid && hit) || !reqValid)){
    io.arFromIF <> io.arToMem
  }.otherwise {
    io.arFromIF.ready := (state === IDLE || state === FINISH) && ((reqValid && hit) || !reqValid)
    io.arToMem <> arToMemW
  }
  
  when (reqValid && IFSkipCache){
    io.rToIF    <> io.rFromMem
  }.otherwise{
    io.rToIF.valid := (hit && (state === IDLE) && reqValid) || (state === FINISH)
    io.rToIF.bits.rdata := Mux(state === FINISH, inst2Fire, hitData)
    io.rFromMem.ready := io.rFromMem.valid
  }
  
  if (config.simulation){

    RawClockedVoidFunctionCall(
      "PerfCountSkipCache"
    )(clock, enable = reqValid && io.rToIF.fire && IFSkipCache)

    RawClockedVoidFunctionCall(
      "PerfCountICacheMiss"
    )(clock, enable = (state === FINISH && io.rToIF.fire))
  }
  

  // io.arFromIF <> io.arToMem
  // io.rToIF    <> io.rFromMem
}
