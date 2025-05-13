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

#define CONFIG_LIGHTSSS

#define FMT_WORD "0x%08" PRIx32

#define EXIT_FAILURE 1
#define FAIT_EXIT    exit(EXIT_FAILURE);
// process sleep time
#define WAIT_INTERVAL 5
// max number of checkpoint process at a time
#define SLOT_SIZE 2
#endif