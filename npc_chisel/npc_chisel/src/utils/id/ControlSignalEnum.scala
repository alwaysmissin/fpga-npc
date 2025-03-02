// package utils.IDStage

// import chisel3.ChiselEnum

// class ControlSignalEnum {
  
// }

// object ALUSrc1 extends ChiselEnum{
//     val RS1, ZERO, PC = Value
// }

// object ALUSrc2 extends ChiselEnum{
//     val RS2, IMM, CSR = Value
// }


// object ALUOp extends ChiselEnum{
//     val ADD, SUB = Value
//     val XOR, AND, OR = Value
//     val SLL, SRL, SRA = Value
//     val MULL, MULH, MULHU = Value
//     val DIVU, DIVS = Value
//     val LTU, GEU, LTS, GES, EQ = Value
// }

// object MemWrite extends ChiselEnum{
//     val B, H, W = Value
// }

// object MemRead extends ChiselEnum{
//     val BS, BU = Value
//     val HS, HU = Value
//     val W = Value
// }

// object NextPCSrc extends ChiselEnum{
//     val PC4 = Value
//     val Branch = Value
//     val Jump, JumpR = Value
//     val CSR_J = Value
// }

// object RegWriteSrc extends ChiselEnum{
//     val ALURes = Value
//     val IMM = Value
//     val PC4 = Value
//     val MEM = Value
//     val CSR = Value
// }