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
void (*ref_difftest_raise_intr)(uint64_t NO) = NULL;

// #ifdef DIFFTEST
static bool is_skip_ref = false;
static int skip_dut_nr_inst = 0;
static int pc_to_skip;
static std::queue<int> pcs_to_skip;

void difftest_skip_ref(int skip_pc){
    // is_skip_ref = true;
    pc_to_skip = skip_pc;
    pcs_to_skip.push(pc_to_skip);
    // printf("skip " FMT_WORD "\n", pc_to_skip);
    // skip_dut_nr_inst = 0;
}

extern "C" void diff_skip(int skip_pc){
    difftest_skip_ref(skip_pc);
}

static int pc_before_intr_triggered = 0;
static int causeNo_intr_triggered = 0;
extern "C" void diff_raise_intr(int causeNO, int epc){
    pc_before_intr_triggered = *cpu.pc_mem;
    causeNo_intr_triggered = causeNO;
    printf("[difftest] raise intr at pc = " FMT_WORD ", causeNO = 0x%08x\n", *cpu.pc_mem, causeNO);
    // if (ref_difftest_raise_intr)
    //     ref_difftest_raise_intr(causeNO);
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

    ref_difftest_raise_intr = (void (*)(uint64_t))dlsym(handle, "difftest_raise_intr");
    assert(ref_difftest_raise_intr);

    ref_difftest_init(port);
    ref_difftest_memcpy(PC_INIT, pmem, img_size, DIFFTEST_TO_REF);
    for (int i = 0; i < NR_GPR; i ++){
        ctx.gpr[i] = (word_t)gpr(i);
    }
    ctx.pc  = PC_INIT;
    ctx.mstatus = *cpu.mstatus;
    ctx.mvendorid.val = 0x79737978;
    ctx.marchid.val = 0x23060051;
    ref_difftest_regcpy(&ctx, DIFFTEST_TO_REF);
    printf("difftest init over!\n");
}


char* reg_name(int i);
char* csr_name(int i);

#define SKIP_CSR(index, name, csr_base) ((index) == ((word_t*)&cpu.name - (csr_base)))

inline bool skip_checkcsrs(const word_t *cpu_csrs, int index){
  return SKIP_CSR(index, marchid, cpu_csrs) || 
         SKIP_CSR(index, mvendorid, cpu_csrs) ||
         SKIP_CSR(index, mscratch, cpu_csrs);
}

static word_t diff_npc = 0;
#ifdef CONFIG_DIFFTEST
std::queue<csr_ctx*> csr_ctx_q;
#endif
bool difftest_checkregs(struct diff_context_t *ref_r, word_t pc){
    bool check = true;
    // static word_t npc = 0;
    if (diff_npc != 0 && diff_npc != pc){
        check = false;
        printf("[difftest] Error: pc is different at pc = " FMT_WORD " , REF = 0x%08x, DUT = 0x%08x\n", pc, diff_npc, pc);
    }
    // word_t *ctx_csrs = (word_t *)&(ref_r->mstatus);
    // word_t **cpu_csrs = (word_t **)&cpu.mstatus;
    for (int i = 1; i < NR_GPR; i ++){
        if (ref_r -> gpr[i] != gpr(i)){
            check = false;
            printf("[difftest] Error: reg $%s is different at pc = " FMT_WORD " , REF = 0x%08x, DUT = 0x%08x\n", reg_name(i), pc, ref_r -> gpr[i], gpr(i));
            printf("mstatus: 0x%08x\n", ref_r -> mstatus.val);
        }
    }
    if (!csr_ctx_q.empty()){
        csr_ctx* csr_ctx_to_check = csr_ctx_q.front();
        if (csr_ctx_to_check->pc == pc){
            word_t *ctx_csrs = (word_t *)&(ref_r->mstatus);
            word_t *cpu_csrs = (word_t *)&(csr_ctx_to_check->mstatus);
            // word_t **cpu_csrs = (word_t **)&(cpu.mstatus);
            for (int i = 0;i < NR_CSR;i ++){
                if (!skip_checkcsrs(ctx_csrs, i) && ctx_csrs[i] != cpu_csrs[i]){
                    check = false;
                    printf("[difftest] Error: csr $%s is different at pc = " FMT_WORD ", REF = 0x%08x, DUT = 0x%08x\n", csr_name(i), pc, ctx_csrs[i], cpu_csrs[i]);
                    for (int j = 0;j < NR_CSR;j ++){
                        printf("$%s = 0x%08x(ref: 0x%08x)\n", csr_name(j), cpu_csrs[j], ctx_csrs[j]);
                    }
                }
            }
            csr_ctx_q.pop();
            free(csr_ctx_to_check);
        }
    }
    diff_npc = ref_r -> pc;
    return check;
}

static void checkregs(struct diff_context_t *ref, word_t pc){
    if (!difftest_checkregs(ref, pc)){
        npc_state.state = NPC_ABORT;
        npc_state.halt_pc = pc;
    }
}

void difftest_step(word_t pc){
    word_t pc_done = *cpu.pc_done;
    if (!pcs_to_skip.empty() && pc_done == pcs_to_skip.front()){
        for (int i = 1; i < NR_GPR; i ++){
            ctx.gpr[i] = (word_t)gpr(i);
        }
        word_t *ctx_csrs = (word_t *)&(ctx.mstatus);
        word_t **cpu_csrs = (word_t **)&cpu.mstatus;
        for (int i = 0;i < NR_CSR;i ++){
            ctx_csrs[i] = *cpu_csrs[i];
        }
        // TODO: NPC的判断在多周期取指不能这么计算
        // 暂且认为npc为pc+4, 因为目前需要跳过的指令都是访存访问外设的指令
        word_t npc = pcs_to_skip.front() + 4;
        ctx.pc = pc_done + 4;
        diff_npc = npc;
        ref_difftest_regcpy(&ctx, DIFFTEST_TO_REF);
        pcs_to_skip.pop();
        return;
    }
    struct diff_context_t ref_r;
    ref_difftest_exec(1);
    if (*cpu.pc_done == pc_before_intr_triggered){
        ref_difftest_raise_intr(causeNo_intr_triggered);
        pc_before_intr_triggered = 0;
        causeNo_intr_triggered = 0;
    }
    ref_difftest_regcpy(&ref_r, DIFFTEST_TO_DUT);
    checkregs(&ref_r, pc);
}

// #endif