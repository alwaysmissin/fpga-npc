#include <common.h>
#include <utils.h>
#include <memory/paddr.h>
#include <device/device.h>
#include <cpu/difftest.h>

extern VysyxSoCFull* top;
extern vluint64_t main_time;
void hello(){
	printf("hello world!");
}

__uint8_t pmem[0x8000000] = {};
char mtrace_buffer[128];
word_t v_to_p(word_t addr){
    return addr - 0x80000000;
}

static inline bool in_pmem(int addr){
	return addr - 0x80000000 < 0x8000000;
}

#define MROM_BASE 0x20000000
extern "C" void mrom_read(int32_t addr, int32_t *data) { 
    // printf("addr " FMT_WORD "\n", addr);
    Assert(addr >= MROM_BASE && addr < MROM_BASE + 0x1000, "illegal memmory read!!!(read address: 0x%08x)", addr);
    word_t paddr = addr - MROM_BASE;
	*data = *(int *)(&pmem[paddr&0xfffffffc]);
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "memory read : addr = " FMT_WORD ", data = " FMT_WORD, addr, *data);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
    return;
    // *data = 0x100073;
}
// extern "C" void pmem_read(int raddr, int* rdata)
// {
//     if (raddr == RTC_ADDR || raddr == RTC_ADDR + 4) {
//         difftest_skip_ref();        
//         *rdata = get_time() >> ((raddr - RTC_ADDR) * 8);
//         return;
//     }
//     if (raddr == SERIAL_PORT) {
//         difftest_skip_ref();
//         *rdata = 0;
//         return;
//     }
//     Assert(in_pmem(raddr), "illegal memmory read!!!(read address: 0x%08x)", raddr);
//     word_t paddr = v_to_p(raddr);
// 	*rdata = *(int *)(&pmem[paddr]);
// #ifdef CONFIG_MTRACE
//     snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
//                 "memory read : addr = " FMT_WORD ", data = " FMT_WORD, raddr, *rdata);
//     if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
//     puts(mtrace_buffer);
// #endif
// }

// extern "C" void inst_read(int addr, int* inst)
// {
//     // printf("mtrace %d: inst_read 0x%08x\n", main_time, addr);
//     Assert(in_pmem(addr), "illegal inst memmory read!!!(read address: 0x%08x)", addr);
//     word_t paddr = v_to_p(addr & ~0x3u);
//     *inst = *(int *)(&pmem[paddr]);
// }

extern "C" void pmem_write(int waddr, int wdata, char wmask, svBit* bresp){
    if (waddr == SERIAL_PORT) {
        difftest_skip_ref();
        putchar(wdata & 0xff);
        fflush(stdout);
        return;
    }
    Assert(in_pmem(waddr), "illegal memory write!!!(write address: 0x%08x)", waddr);
    *bresp = (svBit)in_pmem(waddr);
    word_t paddr = v_to_p(waddr);
    int wlen = 0;
    int temp = wdata;
    while(wmask){
		*(__uint8_t *)(&pmem[paddr]) = (uint8_t)temp;
		paddr ++;
		wmask >>= 1;
        wlen ++;
		temp >>= 8;	
    }
#ifdef CONFIG_MTRACE
    snprintf(mtrace_buffer, sizeof(mtrace_buffer), 
                "memory write: addr = " FMT_WORD ", len = %d, data = " FMT_WORD ", mask = 0x%x\n", waddr, wlen, wdata, wmask);
    if (MTRACE_COND) log_write("%s\n", mtrace_buffer);
    puts(mtrace_buffer);
#endif
}

long load_img(const char *img_file)
{
    if (img_file == NULL)
    {
        return 0;
    }
    FILE *fp = fopen(img_file, "rb");
    assert(fp != NULL);
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);

    printf("The image is %s, size = %ld\n", img_file, size);

    fseek(fp, 0, SEEK_SET);
    int ret = fread(pmem, size, 1, fp);
    assert(ret == 1);

    // void init_psram();
    // init_psram();
    init_flash(fp, size);

    fclose(fp);
    return size;
}

