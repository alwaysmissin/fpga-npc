package utils
import chisel3._

trait ExceptionCodes{
  def InstructionAddressMisaligned = 0
  def InstructionAccessFault = 1
  def IllegalInstruction = 2
  def Breakpoint = 3
  def LoadAddressMisaligned = 4
  def LoadAccessFault = 5
  def StoreAMOAddressMisaligned = 6
  def StoreAMOAccessFault = 7
  def UserEnvironmentCall = 8
  def SupervisorEnvironmentCall = 9
  def MachineEnvironmentCall = 11
  def InstructionPageFault = 12
  def LoadPageFault = 13
  def StoreAMOPageFault = 15

  val ExcepPriority = Seq(
    Breakpoint,
    InstructionPageFault,
    InstructionAccessFault,
    IllegalInstruction,
    InstructionAddressMisaligned,
    MachineEnvironmentCall,
    SupervisorEnvironmentCall,
    UserEnvironmentCall,
    LoadAddressMisaligned,
    StoreAMOAddressMisaligned,
    LoadPageFault,
    StoreAMOPageFault,
    LoadAccessFault,
    StoreAMOAccessFault
  )
}
