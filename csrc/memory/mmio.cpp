#include <common.h>
#include <utils.h>
#include <memory/paddr.h>
#include <device/device.h>
#include <cpu/difftest.h>
#include <svdpi.h>
extern char mtrace_buffer[128];
extern VysyxSoCFull *top;
extern "C" void serial_read(int raddr, int* rdata){
    assert(raddr == SERIAL_PORT);
    *rdata = 0;
    return;
}

extern "C" void serial_write(int waddr, int wdata, char wmask, svBit* bresp){
    assert(waddr == SERIAL_PORT);
    difftest_skip_ref();
    putchar(wdata & 0xff);
    fflush(stdout);
    *bresp = true;
    return;
}

extern "C" void rtc_read(int raddr, int* rdata){
    assert(raddr == RTC_ADDR || raddr == RTC_ADDR + 4);
    // difftest_skip_ref();        
    uint64_t time = get_time();
    *rdata = time >> ((raddr - RTC_ADDR) * 8);
    return;
}

__uint8_t flash[FLASH_SIZE] = {};

void init_flash(FILE* fp, long size){
    fseek(fp, 0, SEEK_SET);
    int ret = fread(flash, size, 1, fp);
    assert(ret == 1);
    // word_t* ptr = (word_t*)flash;
    // for (int addr = 0; addr < FLASH_SIZE; addr += sizeof(word_t)) {
    //     *ptr = addr;
    //     ptr ++;
    // }
}

extern "C" void flash_read(int32_t addr, int32_t *data) {
    Assert(addr >= 0 && addr < FLASH_SIZE, "illegal flash access!!!(read address: 0x%08x)", addr);
    // word_t paddr = addr - FLASH_BASE;
    *data = *(int *)(&flash[addr&0xfffffffc]);
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "flash read : addr = " FMT_WORD ", data = " FMT_WORD, addr, *data);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
    return;
}

__uint8_t psram[PSRAM_SIZE] = {};
void init_psram(){
    word_t* ptr = (word_t*)psram;
    for (int addr = 0; addr < PSRAM_SIZE; addr += sizeof(word_t)) {
        *ptr = addr;
        ptr ++;
    }
}

extern "C" void psram_read(int32_t addr, int32_t *data){
    Assert(addr >= 0 && addr < PSRAM_SIZE, "illegal psram access!!!(read address: 0x%08x)", addr);
    *data = *(int *)(&psram[addr&0xfffffffc]);
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "psram read : addr = " FMT_WORD ", data = " FMT_WORD, addr, *data);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
    return;
}

extern "C" void psram_write(int32_t waddr, char wlen, int32_t wdata){
    Assert(waddr >= 0 && waddr < PSRAM_SIZE, "illegal psram access!!!(read address: 0x%08x)", waddr);
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "psram write : addr = " FMT_WORD ", data = " FMT_WORD ", len = " FMT_WORD, waddr, wdata, (int)wlen);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
    switch (wlen)
    {
    case 1: *(uint8_t  *)(&psram[waddr]) = wdata; return;
    case 2: *(uint16_t *)(&psram[waddr]) = wdata; return;
    case 4: *(uint32_t *)(&psram[waddr]) = wdata; return;
    default: assert(0);
    }
}

uint16_t sdram[SDRAM_NR_BANKS][SDRAM_NR_ROWS][SDRAM_NR_COLS][2] = {};
uint16_t (*activedRows[SDRAM_NR_BANKS])[SDRAM_NR_COLS][2] = {NULL, NULL, NULL, NULL};
// uint16_t (*actived_row)[SDRAM_NR_COLS] = NULL;
// uint8_t ba_r = 0;
#define ActivateRowInBank(ba, a) ((activedRows[(ba)]) = &sdram[(ba)][(a)])
#define GetActivedRowInBank(ba, a) ((*activedRows[(ba)])[(a)])

extern "C" void sdram_active(const svBitVecVal* ba, const svBitVecVal* a){
    Assert((*ba < SDRAM_NR_BANKS && *ba >= 0) || (*a < SDRAM_NR_ROWS  && *a  >= 0), "illegal sdram access!!!(bank = " FMT_WORD ", row = " FMT_WORD ")", *ba, *a);
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "sdram activate : bank = " FMT_WORD ", row = " FMT_WORD, *ba, *a);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
    // assert(*ba < SDRAM_NR_BANKS && *ba >= 0);
    // assert(*a < SDRAM_NR_ROWS  && *a  >= 0);
    // ba_r = *ba;
    // actived_row = &sdram[*ba][*a];
    // activedRows[*ba] = &sdram[*ba][*a];
    ActivateRowInBank(*ba, *a);
}

extern "C" void sdram_read(const svBitVecVal* ba, const svBitVecVal* a, int *data){
    Assert((*ba < SDRAM_NR_BANKS && *ba >= 0) || (*a < SDRAM_NR_ROWS  && *a  >= 0), "illegal sdram access!!!(bank = " FMT_WORD ", row = " FMT_WORD ")", *ba, *a);
    // printf("try to read: bank = " FMT_WORD ", col = " FMT_WORD "\n", *ba, *a);
    // assert(*ba == ba_r);
    assert(*ba < SDRAM_NR_BANKS && *ba >= 0);
    assert(*a < SDRAM_NR_ROWS  && *a  >= 0);
    // *data = (int32_t)(*actived_row)[*a];
    *data = *(int32_t*)GetActivedRowInBank(*ba, *a);
    // printf("data: %d\n", *(int32_t*)GetActivedRowInBank(*ba, *a));    
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "sdram read : bank = " FMT_WORD ", col = " FMT_WORD ", data = " FMT_WORD, *ba, *a, *data);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
}

#define NoneMask      0xf
#define Byte0Mask     0xe
#define Byte1Mask     0xd
#define Byte2Mask     0xb
#define Byte3Mask     0x7
#define HalfwordLMask 0xc
#define HalfwordHMask 0x3
#define WordMask      0x0
extern "C" void sdram_write(const svBitVecVal* ba, const svBitVecVal* a, int wdata, const svBitVecVal* dqm){
	word_t pc = top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__wbStage__DOT__pc_done;
    // if (pc >= 0xa0000000 && pc < 0xafffffff){
    //     printf("try to write: bank = " FMT_WORD ", col = " FMT_WORD "\n", *ba, *a);
    // }
    Assert((*ba < SDRAM_NR_BANKS && *ba >= 0) || (*a < SDRAM_NR_ROWS  && *a  >= 0), "illegal sdram access!!!(bank = " FMT_WORD ", row = " FMT_WORD ")", *ba, *a);
    uint8_t wlen = (*dqm == NoneMask) ? 0 : 
                   (*dqm == Byte0Mask || *dqm == Byte1Mask || *dqm == Byte2Mask || *dqm == Byte3Mask) ? 1 :
                   (*dqm == HalfwordLMask || *dqm == HalfwordHMask) ? 2 :
                   (*dqm == WordMask) ? 4 : -1;
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "sdram write : bank = " FMT_WORD ", col = " FMT_WORD ", data = " FMT_WORD ", wlen = " FMT_WORD, *ba, *a, wdata, wlen);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
    // printf("ba: %d, ba_r: %d\n", *ba, ba_r);
    // assert(*ba == ba_r);
    assert(*ba < SDRAM_NR_BANKS && *ba >= 0);
    assert(*a < SDRAM_NR_ROWS  && *a  >= 0);
    switch (*dqm){
        case NoneMask: break;
        // case ByteLMask: (*(uint8_t  *)(&(*actived_row)[*a])) = wdata; break;
        // case ByteHMask: (*((uint8_t  *)(&(*actived_row)[*a]) + 1)) = wdata; break;
        // case HalfwordMask: (*(uint16_t *)(&(*actived_row)[*a])) = wdata; break;
        case Byte0Mask:     (*((uint8_t  *)(&GetActivedRowInBank(*ba, *a)) + 0)) = (uint8_t)wdata ; break;
        case Byte1Mask:     (*((uint8_t  *)(&GetActivedRowInBank(*ba, *a)) + 1)) = (uint8_t)(wdata >> 8) ; break;
        case Byte2Mask:     (*((uint8_t  *)(&GetActivedRowInBank(*ba, *a)) + 2)) = (uint8_t)(wdata >> 16) ; break;
        case Byte3Mask:     (*((uint8_t  *)(&GetActivedRowInBank(*ba, *a)) + 3)) = (uint8_t)(wdata >> 24) ; break;
        case HalfwordLMask: (*((uint16_t *)(&GetActivedRowInBank(*ba, *a)) + 0)) = (uint16_t)wdata; break;
        case HalfwordHMask: (*((uint16_t *)(&GetActivedRowInBank(*ba, *a)) + 1)) = (uint16_t)(wdata >> 16); break;
        case WordMask:      (*((uint32_t *)(&GetActivedRowInBank(*ba, *a))))     = wdata; break;
        default: assert(0);
    }
    // if (pc >= 0xa0000000 && pc < 0xafffffff){
    //     printf("write\n");
    // }
}