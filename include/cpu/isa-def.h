#ifndef __ISA_DEF_H__
#define __ISA_DEF_H__
// isa.h
#include <common.h>
#include <cpu/cpu.h>
#include <cpu/isa-csr-def.h>

typedef struct {
  word_t *pc_if;
  word_t *pc_exe;
  word_t *pc_mem;
  bool *done;
  word_t *pc_done;
  word_t *inst;
  word_t (*gpr)[NR_GPR - 1];
  CSR_LIST(CSR_DEF)
  // mstatus_t *mstatus;
  // mtvec_t *mtvec;
  // word_t *placeholder1;
  // mepc_t *mepc;
  // mcause_t *mcause;
} cpu_state;

#endif