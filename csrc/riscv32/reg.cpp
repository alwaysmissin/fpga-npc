#include <common.h>
#include <utils.h>
#include <cpu/isa-def.h>

extern VysyxSoCFull *top;
extern VerilatedVcdC *tfp;
extern cpu_state cpu;

const char *regs[] = {
    "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
    "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
    "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
    "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

const char *csrs[] = {
  "mstatus", "mtvec", "mepc", "mcause", "mvendorid", "marchid"
};

// #define NR_GPR ARRLEN(top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__regFiles__DOT__R_ext__DOT__Memory)
// #define gpr(i) top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__regFiles__DOT__R_ext__DOT__Memory[i]

void reg_display()
{

  int data;
  for (int i = 1; i < NR_GPR; i++)
  {
    data = gpr(i - 1);
    printf("%s\t\t0x%08x\t%-16d\n", regs[i], data, data);
  }
}

/* if reg of 'name' exists, set success to true, else, set to false*/
word_t isa_reg_str2val(const char *s, bool *success) {
  *success = false;
  if(strcmp(s, "pc") == 0){
    *success = true;
    return top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__wbStage_io_fromMEM_bits_rpc;
  }
  for(int i = 0;i < sizeof(regs) / sizeof(regs[0]);i++){
    if(strcmp(regs[i], s) == 0){
      *success = true;
      return gpr(i - 1);
    }
  }
  return 0;
}

char* reg_name(int i){
  return (char *)regs[i];
}

char* csr_name(int i){
  return (char *)csrs[i];
}