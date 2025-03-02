package utils.bypass

import chisel3._

object BypassTypeEnum extends ChiselEnum{
    val FromEXE, FromMEM, FromWB = Value
    val FromREG = Value
  
}
