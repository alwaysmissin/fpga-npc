/***************************************************************************************
* Copyright (c) 2014-2022 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "mmu.h"
#include "sim.h"
#include "../../include/common.h"
#include "../../include/cpu/isa-csr-def.h"
#include <cstring>
#include <difftest-def.h>
#include <iostream>
#include <new>

#define NR_GPR MUXDEF(CONFIG_RVE, 16, 32)
// use tdata as a placeholder for mscratch

static std::vector<std::pair<reg_t, abstract_device_t*>> difftest_plugin_devices;
static std::vector<std::string> difftest_htif_args;
// static std::vector<std::pair<reg_t, mem_t*>> difftest_mem(
//     1, std::make_pair(reg_t(DRAM_BASE), new mem_t(0x8000000)));
static std::vector<std::pair<reg_t, mem_t*>> difftest_mem = 
{
  std::make_pair(reg_t(DRAM_BASE), new mem_t(0x10000000)),
  std::make_pair(reg_t(SRAM_BASE), new mem_t(0x2000))
};
static debug_module_config_t difftest_dm_config = {
  .progbufsize = 2,
  .max_sba_data_width = 0,
  .require_authentication = false,
  .abstract_rti = 0,
  .support_hasel = true,
  .support_abstract_csr_access = true,
  .support_abstract_fpr_access = true,
  .support_haltgroups = true,
  .support_impebreak = true
};


struct diff_context_t {
  word_t gpr[MUXDEF(CONFIG_RVE, 16, 32)];
  word_t pc;
  // word_t csrs[NR_CSR];
  CSR_LIST(CSR_DEF_DIFFCTX)
  // word_t mstatus, mie, mip, mtvec, mscratch, mepc, mcause, satp;
};

static sim_t* s = NULL;
static processor_t *p = NULL;
static state_t *state = NULL;

void sim_t::diff_init(int port) {
  p = get_core("0");
  state = p->get_state();
  // CSR_LIST(CSR_INIT_DIFF)
}

void sim_t::diff_step(uint64_t n) {
  // printf("pc: 0x%08x\n", state->pc);
  reg_t pc = state -> pc;
  step(n);
  // printf("pc: 0x%08x t1: 0x%08x a0: 0x%08x mstatus: 0x%08x\n", pc, state->XPR[6], state->XPR[10], state->mstatus->read());
  // if (state->pc == 0x8005b9a8){
  //   printf("mstatus: 0x%08x\n", state->mstatus->read());
  // }
}


void sim_t::diff_get_regs(void* diff_context) {
  struct diff_context_t* ctx = (struct diff_context_t*)diff_context;
  for (int i = 0; i < NR_GPR; i++) {
    ctx->gpr[i] = state->XPR[i];
  }
  ctx->pc = state->pc;
  CSR_LIST(CSR_GET)
  // ctx->mstatus = state->mstatus->read();
  // ctx->mie = state->mie->read();
  // ctx->mtvec = state->mtvec->read();
  // ctx->mepc = state->mepc->read();
  // ctx->mcause = state->mcause->read();
  // ctx->satp  = state->satp->read();
}


void sim_t::diff_set_regs(void* diff_context) {
  struct diff_context_t* ctx = (struct diff_context_t*)diff_context;
  for (int i = 0; i < NR_GPR; i++) {
    state->XPR.write(i, (sreg_t)((int32_t)ctx->gpr[i]));
  }
  state->pc = ctx->pc;
  CSR_LIST(CSR_SET)
}

void sim_t::diff_memcpy(reg_t dest, void* src, size_t n) {
  mmu_t* mmu = p->get_mmu();
  for (size_t i = 0; i < n; i++) {
    mmu->store<uint8_t>(dest+i, *((uint8_t*)src+i));
  }
}

extern "C" {

__EXPORT void difftest_memcpy(word_t addr, void *buf, size_t n, bool direction) {
  if (direction == DIFFTEST_TO_REF) {
    s->diff_memcpy(addr, buf, n);
  } else {
    assert(0);
  }
}

__EXPORT void difftest_regcpy(void* dut, bool direction) {
  if (direction == DIFFTEST_TO_REF) {
    s->diff_set_regs(dut);
  } else {
    s->diff_get_regs(dut);
  }
}

__EXPORT void difftest_exec(uint64_t n) {
  s->diff_step(n);
}

#define DEFAULT_PRIV "MSU"

/* Default value for --varch switch */
#define DEFAULT_VARCH "vlen:128,elen:64"
__EXPORT void difftest_init(int port) {
  difftest_htif_args.push_back("");
  const char *isa = "RV32" MUXDEF(CONFIG_RVE, "E", "I") "MAFDC";
  // const char *priv = "MU";
  cfg_t *cfg = new cfg_t(/*default_initrd_bounds=*/std::make_pair((reg_t)0, (reg_t)0),
            /*default_bootargs=*/nullptr,
            /*default_isa=*/isa,
            /*default_priv=*/DEFAULT_PRIV,
            /*default_varch=*/DEFAULT_VARCH,
            /*default_misaligned=*/false,
            /*default_endianness*/endianness_little,
            /*default_pmpregions=*/16,
            /*default_mem_layout=*/std::vector<mem_cfg_t>(),
            /*default_hartids=*/std::vector<size_t>(1),
            /*default_real_time_clint=*/false,
            /*default_trigger_count=*/4);
  s = new sim_t(cfg, false,
      difftest_mem, difftest_plugin_devices, difftest_htif_args,
      difftest_dm_config, nullptr, false, NULL,
      false,
      NULL,
      true);
  s->diff_init(port);
}

__EXPORT void difftest_raise_intr(uint64_t NO) {
  trap_t t(NO);
  p->take_trap_public(t, state->pc);
}

}
