#include <dlfcn.h>

#include <common.h>
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <cpu/isa-def.h>
#include <cpu/isa-csr-def.h>
#include <memory/paddr.h>
#include <utils.h>
#include <queue>

enum{ DIFFTEST_TO_DUT, DIFFTEST_TO_REF };
extern uint8_t pmem[0x8000000];
extern VysyxSoCFull *top;
extern cpu_state cpu;
struct diff_context_t{
  word_t gpr[NR_GPR];
  word_t pc;  
  mstatus_t mstatus;
  mtvec_t mtvec;
  word_t placeholder1;
  mepc_t mepc;
  mcause_t mcause;
  word_t placeholder2;
  mvendorid_t mvendorid;
  marchid_t marchid;
} ctx;

void (*ref_difftest_memcpy)(word_t addr, void *buf, size_t n, bool direction) = NULL;
void (*ref_difftest_regcpy)(void *dut, bool direction) = NULL;
void (*ref_difftest_exec)(uint64_t n) = NULL;

// #ifdef DIFFTEST
static bool is_skip_ref = false;
static int skip_dut_nr_inst = 0;
static int pc_to_skip;
static std::queue<int> pcs_to_skip;

void difftest_skip_ref(){
    // is_skip_ref = true;
    pc_to_skip = top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__exeStage_io_fromID_bits_rpc;
    pcs_to_skip.push(pc_to_skip);
    // printf("skip " FMT_WORD "\n", pc_to_skip);
    // skip_dut_nr_inst = 0;
}

extern "C" void diff_skip(){
    difftest_skip_ref();
}

void init_difftest(char *ref_so_file, long img_size, int port){
    assert(ref_so_file != NULL);

    void *handle = NULL;
    handle = dlopen(ref_so_file, RTLD_LAZY);
    if (handle == NULL){
        printf("dlopen - %s\n", dlerror());
    }
    assert(handle != NULL);

    ref_difftest_memcpy = (void (*)(word_t, void *, size_t, bool))dlsym(handle, "difftest_memcpy");
    assert(ref_difftest_memcpy);

    ref_difftest_regcpy = (void (*)(void *, bool))dlsym(handle, "difftest_regcpy");
    assert(ref_difftest_regcpy);
    
    ref_difftest_exec = (void (*)(uint64_t))dlsym(handle, "difftest_exec");
    assert(ref_difftest_exec);

    void (*ref_difftest_init)(int) = (void (*)(int))dlsym(handle, "difftest_init");
    assert(ref_difftest_init);

    ref_difftest_init(port);
    ref_difftest_memcpy(PC_INIT, pmem, img_size, DIFFTEST_TO_REF);
    // diff_context_t *ctx = (diff_context_t *)malloc(sizeof(diff_context_t));
    // ctx.gpr = &(top->rootp->top__DOT__cpu__DOT__rf__DOT__gpr[0]);
    for (int i = 0; i < NR_GPR; i ++){
        ctx.gpr[i] = (word_t)gpr(i);
    }
    ctx.pc  = PC_INIT;
    ref_difftest_regcpy(&ctx, DIFFTEST_TO_REF);
    printf("difftest init over!\n");
    // ref_difftest_memcpy = dlsym(handle, "difftest_memcpy");
    // assert(ref_difftest_memcpy);
}


char* reg_name(int i);
char* csr_name(int i);

static word_t diff_npc = 0;
bool difftest_checkregs(struct diff_context_t *ref_r, word_t pc){
    // static word_t npc = 0;
    if (diff_npc != 0 && diff_npc != pc){
        printf("[difftest] Error: pc is different at pc = " FMT_WORD " , REF = 0x%08x, DUT = 0x%08x\n", pc, diff_npc, pc);
        return false;
    }
    for (int i = 1; i < NR_GPR; i ++){
        if (ref_r -> gpr[i] != gpr(i - 1)){
            printf("[difftest] Error: reg $%s is different at pc = " FMT_WORD " , REF = 0x%08x, DUT = 0x%08x\n", reg_name(i), pc, ref_r -> gpr[i], gpr(i - 1));
            // printf("a2: " FMT_WORD ", a3: " FMT_WORD "\n", ref_r -> gpr[12], ref_r -> gpr[13]);
            return false;
        }
    }
    word_t *ctx_csrs = (word_t *)&(ref_r->mstatus);
    word_t **cpu_csrs = (word_t **)&cpu.mstatus;
    for (int i = 0;i < NR_CSR - 2;i ++){
        if (ctx_csrs[i] != *cpu_csrs[i]){
            printf("[difftest] Error: csr $%s is different at pc = " FMT_WORD ", REF = 0x%08x, DUT = 0x%08x\n", csr_name(i), pc, ctx_csrs[i], *cpu_csrs[i]);
            return false;
        }
    }
    diff_npc = ref_r -> pc;
    return true;
}

static void checkregs(struct diff_context_t *ref, word_t pc){
    if (!difftest_checkregs(ref, pc)){
        npc_state.state = NPC_ABORT;
        npc_state.halt_pc = pc;
    }
}

void difftest_step(word_t pc){
    word_t pc_done = *cpu.pc;
    if (!pcs_to_skip.empty() && pc_done == pcs_to_skip.front()){
        for (int i = 1; i < NR_GPR; i ++){
            ctx.gpr[i] = (word_t)gpr(i - 1);
        }
        // TODO: NPC的判断在多周期取指不能这么计算
        // 暂且认为npc为pc+4, 因为目前需要跳过的指令都是访存访问外设的指令
        // word_t pc_exe = top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__memStage_io_fromEXE_bits_r_pc;
        // word_t pc_mem = top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__wbStage_io_fromMEM_bits_r_pc;
        word_t npc = pcs_to_skip.front() + 4;
        ctx.pc = pc_done + 4;
        diff_npc = npc;
        ref_difftest_regcpy(&ctx, DIFFTEST_TO_REF);
        pcs_to_skip.pop();
        // is_skip_ref = false;
        // pc_to_skip = 0;
        return;
    }
    struct diff_context_t ref_r;
    ref_difftest_exec(1);
    ref_difftest_regcpy(&ref_r, DIFFTEST_TO_DUT);
    // printf("pc = " FMT_WORD ", npc = " FMT_WORD "\n", pc, npc);
    checkregs(&ref_r, pc);
    // printf("[difftest] difftest pass at pc = " FMT_WORD "\n", pc);
}

// #endif