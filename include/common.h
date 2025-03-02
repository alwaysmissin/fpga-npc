#ifndef __COMMON_H__
#define __COMMON_H__
#include <assert.h>
#include <unistd.h>
#include <stdio.h>
#include <inttypes.h>
#include <macro.h>
#include <generated/autoconf.h>

// include verilator headers
#include "VysyxSoCFull.h"
#include "VysyxSoCFull__Dpi.h"
#include "VysyxSoCFull___024root.h"
#include <verilated.h>
#include <verilated_vcd_c.h>

#define NR_GPR 16
// include nvboard if target is RUN
#ifdef RUN
#include <nvboard.h>
void nvboard_bind_all_pins(VysyxSoCFull* top);
#endif

typedef __uint32_t word_t;
#include <debug.h>

#define FMT_WORD "0x%08" PRIx32
#endif