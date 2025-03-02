package utils
import chisel3._

object ExceptionCodes extends ChiselEnum{
  val InstructionAddressMisaligned, 
  InstructionAccessFault,
  IllegalInstruction,
  Breakpoint,
  LoadAddressMisaligned,
  LoadAccessFault,
  StoreAddressMisaligned,
  StoreAccessFault,
  UserEnvironmentCall,
  SupervisorEnvironmentCall,
  Reserved1,
  MachineEnvironmentCall = Value
  // val ExceptionNone = Value
  // SupervisorTimerInterrupt,
  // MachineTimerInterrupt,
  // MachineExternalInterrupt = Value
  
}
