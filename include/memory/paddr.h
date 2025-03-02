#ifndef __PADDR_H__
#define __PADDR_H__
#include <common.h>
#define PC_INIT 0x30000000

// FLASH definitions
#define FLASH_BASE 0x30000000
#define FLASH_SIZE 0x10000000
// PSRAM definitions
#define PSRAM_BASE 0x80000000
#define PSRAM_SIZE 0x400000
// SDRAM definitions
#define SDRAM_BASE 0xa0000000
#define SDRAM_SIZE 0x2000000
#define SDRAM_NR_BANKS    4
#define SDRAM_NR_ROWS    8192
#define SDRAM_NR_COLS    512
// word_t pmem_read(__uint32_t addr);
// void pmem_write(__uint32_t addr, word_t data);
long load_img(const char *img_file);
void init_flash(FILE* fp, long size);

#endif