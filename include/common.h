#ifndef __COMMON_H__
#define __COMMON_H__
#include <stdlib.h>
#include <stdint.h>
#include <inttypes.h>
#include <stdbool.h>
#include <string.h>
#include <assert.h>
#include <unistd.h>
#include <stdio.h>
#include <inttypes.h>
#include <macro.h>
#include <generated/autoconf.h>


typedef __uint32_t word_t;
typedef int32_t sword_t;
typedef word_t vaddr_t;
typedef uint32_t paddr_t;
#include <debug.h>

#define FMT_WORD "0x%08" PRIx32
#endif