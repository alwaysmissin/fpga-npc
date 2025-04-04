#ifndef __CPU_CPU_H__
#define __CPU_CPU_H__

#include <common.h>

// include verilator headers
#include "VysyxSoCFull.h"
#include "VysyxSoCFull__Dpi.h"
#include "VysyxSoCFull___024root.h"
#include <verilated.h>
#include <verilated_vcd_c.h>

#define NR_GPR 32
// include nvboard if target is RUN
#ifdef RUN
#include <nvboard.h>
void nvboard_bind_all_pins(VysyxSoCFull* top);
#endif

void reset(int n);
void cpu_exec(uint64_t n);

#endif