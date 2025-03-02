#ifndef __ISA_DEF_H__
#define __ISA_DEF_H__
// isa.h
#include <common.h>
#include <cpu/cpu.h>
#include <cpu/isa-csr-def.h>

typedef struct {
  word_t *pc;
  word_t (*gpr)[NR_GPR];
  mstatus_t *mstatus;
  mtvec_t *mtvec;
  mepc_t *mepc;
  mcause_t *mcause;
  // mvendorid_t *mvendorid;
  // marchid_t *marchid;
} cpu_state;

#endif