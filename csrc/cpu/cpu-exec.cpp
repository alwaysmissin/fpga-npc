#include "utils.h"
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <cstdio>
#include <cstring>
#include <memory/paddr.h>
#include <debug.h>
#include <queue>
#include <watchpoint.h>
#include <verilated_fst_c.h>
#include <csignal>
#include <string>
#include <sstream>
#include <ctime>
#include <iomanip>
#include <cpu/isa-def.h>
#include <lightsss.h>

#ifdef CONFIG_WAVEFORM
vluint64_t main_time = 0;
#endif

extern VerilatedContext *contextp;
extern VysyxSoCFull *top;
extern VerilatedFstC *tfp;

word_t placeholder = 0;
cpu_state cpu;

char logbuf[128];
#define ilen 4

static bool g_print_step = false;
#define MAX_INST_TO_PRINT 100

/* itrace: ringbuffer */
// define the size of ring buffer
#define IRINGBUF_SIZE 20
// declare the ring buffer
static char iringbuff[IRINGBUF_SIZE][128];
// declare the index of ring buffer
static int iringbuff_index = -1;

uint64_t g_nr_guest_inst = 0;
uint64_t g_nr_cycle = 0;

extern __uint8_t pmem[0x8000000];
word_t v_to_p(word_t addr);
// use llvm to disassemble
extern "C" void disassemble(char *str, int size, uint64_t pc, uint8_t *code, int nbyte);
// extern "C" int inst_read(int addr);
LightSSS lightsss;
static bool start_waveform = false;

static bool diff_ok()
{
	return *cpu.done;
}

static void trace_and_difftest()
{
	word_t pc = *cpu.pc_done;
	// if (diff_ok())
#ifdef CONFIG_DIFFTEST
	// printf("pc: %x, npc: %x\n", pc, npc);
#ifdef CONFIG_WAVEFORM
	if (!start_waveform)
#endif
	difftest_step(pc);
#endif
	// watchpoint
	word_t old_value;
	WP *diff = check_diff(&old_value);
	if (diff != NULL)
	{
		if (diff->trigger_flag == false)
		{
			diff->trigger_flag = true;
			npc_state.state = NPC_STOP;
			printf("watchpoint %d changed:\n", diff->NO);
			printf("Expression: %s\n", diff->What);
			printf("%u -> %u\n", old_value, diff->value);
		}
		else
		{
			diff->trigger_flag = false;
		}
	}
}

static void statistic() {
	char commit_hash[128];  // 用于存储提交哈希值的缓冲区
	FILE *fp = popen("git rev-parse HEAD", "r");  // 运行命令获取提交哈希值
	
	if (fp == NULL) {
			perror("Failed to run git command");
			return;
	}
	
	// 读取命令的输出
	if (fgets(commit_hash, sizeof(commit_hash), fp) != NULL) {
			Log("Current Commit Hash: %s", commit_hash);  // 打印提交哈希值
	} else {
			Log("Failed to get commit hash.\n");
	}
	
	pclose(fp);  // 关闭文件指针
#define NUMBERIC_FMT "%" PRIu64
	extern uint64_t IFUCounter;
	extern uint64_t ALUInstCounter;
	extern uint64_t LSUInstCounter;
	extern uint64_t BRUInstCounter;
	extern uint64_t CSRInstCounter;
	extern uint64_t InstFlushedCounter;
	extern uint64_t ALUCounter;
	extern uint64_t LSUCounter;
	extern uint64_t IFURReqCounter;
	extern uint64_t IFURChannelLatencyTotal;
	extern uint64_t LSURReqCounter;
	extern uint64_t LSURChannelLatencyTotal;
	extern uint64_t LSUWReqCounter;
	extern uint64_t LSUWChannelLatencyTotal;
	// extern uint64_t icacheAccessCounter;
	extern uint64_t icacheSkipCounter;
	extern uint64_t icacheMissCounter;
	extern uint64_t missBranchPrediction;
  Log("host cycle spent = " NUMBERIC_FMT " cycles", g_nr_cycle);
  Log("total guest instructions = " NUMBERIC_FMT, g_nr_guest_inst);
	Log("IPC = %f", g_nr_guest_inst / (double)g_nr_cycle);
	Log("---- PerfCount ----");
	Log("1. instructions");
	Log("the number of IFU instructions = " NUMBERIC_FMT, IFUCounter);
	Log("	├─ the number of ALU instructions = " NUMBERIC_FMT, ALUInstCounter);
	Log("	├─ the number of LSU instructions = " NUMBERIC_FMT, LSUInstCounter);
	Log("	├─ the number of BRU instructions = " NUMBERIC_FMT, BRUInstCounter);
	Log("	├─ the number of CSR instructions = " NUMBERIC_FMT, CSRInstCounter);
	Log(" └─ the number of instructions flushed = " NUMBERIC_FMT, InstFlushedCounter);
	Log("the number of ALU cycles = " NUMBERIC_FMT, ALUCounter);
	Log("the number of LSU cycles = " NUMBERIC_FMT, LSUCounter);
	Log("2. average latency of IFU and LSU");
	Log("average latency of IFU R Channel = " NUMBERIC_FMT "(total cycles)/" NUMBERIC_FMT "(times) = %lf", IFURChannelLatencyTotal, IFURReqCounter, IFURChannelLatencyTotal / (double)IFURReqCounter);
	Log("average latency of LSU R Channel = " NUMBERIC_FMT "(total cycles)/" NUMBERIC_FMT "(times) = %lf", LSURChannelLatencyTotal, LSURReqCounter, LSURChannelLatencyTotal / (double)LSURReqCounter);
	Log("average latency of LSU W Channel = " NUMBERIC_FMT "(total cycles)/" NUMBERIC_FMT "(times) = %lf", LSUWChannelLatencyTotal, LSUWReqCounter, LSUWChannelLatencyTotal / (double)LSUWReqCounter);
	Log("3. cache");
	uint64_t icacheAccessCounter = IFUCounter - icacheSkipCounter;
	Log("the number of icache access " NUMBERIC_FMT " times", icacheAccessCounter);
	// Log("the number of icache skip " NUMBERIC_FMT " times", icacheSkipCounter);
	Log("the number of icache miss " NUMBERIC_FMT " times", icacheMissCounter);
	Log("icache miss rate = %lf", icacheMissCounter / (double)icacheAccessCounter);
	Log("4. branch prediction");
	Log("the number of redirection " NUMBERIC_FMT " times", missBranchPrediction);
	Log("branch miss prediction accuracy = %lf", missBranchPrediction / (double)BRUInstCounter);
	Log("-------------------");
	
}
void reg_display();
static void ringbuf_print()
{
#ifdef CONFIG_ITRACE
	for (int i = 0; i < IRINGBUF_SIZE; i++)
	{
		printf(i == iringbuff_index ? "--> %s\n" : "    %s\n", iringbuff[i]);
	}
	reg_display();
#endif
}

static bool divide_condition(){
	#ifdef CONFIG_WAVEFORM

	static uint32_t count = 0;
	uint32_t new_count = g_nr_cycle / 500000;
	if (count != new_count || g_nr_cycle == 0){
		count = new_count;
		return true;
	}
	#endif
	return false;
}

static bool dump_condition(){
	// #ifdef CONFIG_DUMP_WHEN_SDRAM
	// uint32_t pc = *cpu.pc_if;
	// return (pc >= SDRAM_BASE && pc < SDRAM_BASE + SDRAM_SIZE);
	// #endif
	return true;
}

static void trace_new(){
	#ifdef CONFIG_WAVEFORM

	extern char* wave_file;
	tfp -> flush();
	tfp -> close();


	// 截取文件名称
	#ifndef CONFIG_WAVEFORM_OVERLAP
	std::string wave_file_str(wave_file);
	size_t last_dot = wave_file_str.find_last_of('.');
	std::string base_name = (last_dot == std::string::npos) ? wave_file_str : wave_file_str.substr(0, last_dot);

	// 获得当前时间
	std::time_t t = std::time(nullptr);
	std::tm tm = *std::localtime(&t);

	std::stringstream buf;
	buf << base_name << "_" << std::put_time(&tm, "%m%d_%H%M%S") << "_" << main_time / 1000000 << ".fst";
	std::string new_wave_file = buf.str();
	tfp -> open(new_wave_file.c_str());
	#else
	tfp -> open(wave_file);
	#endif
	#endif

	return;
}


static void dump_wave(){
	#ifdef CONFIG_WAVEFORM
	extern char* wave_file;
	if (divide_condition() && !lightsss.is_child()) {
		int ret = lightsss.do_fork();
		if (ret == FORK_CHILD) {
			start_waveform = true;
			tfp -> flush();
			tfp -> close();
			tfp -> open(wave_file);
		}
	#ifdef CONFIG_WAVEFORM_DIVIDE
		trace_new();
	#endif
	}
	if (dump_condition() && start_waveform){
		if (lightsss.get_end_cycles() < g_nr_cycle)
			npc_state.state = NPC_ABORT;
		tfp -> dump(main_time);
		main_time ++;
	}
	#endif

}

static void exec_once()
{
	uint32_t death_loop_counter = 20000;
	do{
		top->clock = 0;
		top->eval();
		dump_wave();

		top->clock = 1;
		top->eval();
		dump_wave();
	#ifdef RUN
		nvboard_update();
	#endif
		g_nr_cycle ++;
		if (--death_loop_counter == 0){
			npc_state.state = NPC_ABORT;
			printf("NPC: death loop detected\n");
			break;
		}
	} while(!diff_ok());
#ifdef CONFIG_ITRACE
	char *p = logbuf;
	uint32_t pc = *cpu.pc_done;
	p += snprintf(p, sizeof(logbuf), FMT_WORD ":", pc);
	int i;
	uint8_t *inst = (uint8_t *)cpu.inst;
	for (i = ilen - 1; i >= 0; i--)
	{
		p += snprintf(p, 4, " %02x", inst[i]);
	}
	memset(p, ' ', 1);
	p += 1;
	// bug here
	disassemble(p, 128, pc, inst, 4);
	// write to ring buffer
	iringbuff_index = (iringbuff_index + 1) % IRINGBUF_SIZE;
	assert(iringbuff_index >= 0 && iringbuff_index < IRINGBUF_SIZE);
	memcpy(iringbuff[iringbuff_index], logbuf, sizeof(logbuf));
	// write to log
	log_write("%s\n", logbuf);
	// write to terminal
	if (g_print_step)
		printf("%s\n", logbuf);
#endif
}

void reset(int n)
{
	cpu.pc_if = &top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__ifStage__DOT__PC;
	cpu.pc_done = &top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__wbStage__DOT__pc_done;
	cpu.inst = &top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__wbStage__DOT__inst;
	cpu.pc_exe = &top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__exeStage_io_fromID_bits_rpc;
	cpu.pc_mem = &top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__memStage_io_fromEXE_bits_rpc;
	cpu.done = (bool*)&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__wbStage__DOT__done;
	cpu.gpr = (word_t (*)[NR_GPR - 1])&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__regFiles__DOT__R_ext__DOT__Memory;
	CSR_LIST(CSR_INIT)
	// cpu.mstatus = (mstatus_t*)&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csrRegs__DOT__mstatus;
	// cpu.mtvec = (mtvec_t*)&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csrRegs__DOT__mtvec;
	// cpu.mepc = (mepc_t*)&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csrRegs__DOT__mepc;
	// cpu.mcause = (mcause_t*)&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csrRegs__DOT__mcause;
	// cpu.placeholder1 = &placeholder;
	top->reset = 1;
	// cpu_exec(n);
	while (n--)
	{
		top->clock = 0;
		top->eval();
		dump_wave();
		top->clock = 1;
		top->eval();
		dump_wave();
	#ifdef RUN
		nvboard_update();
	#endif
	}
	top->reset = 0;
}

void ebreak(int inst)
{
	if (inst == 0x100073)
	{
		int exit_code = gpr(10);
		printf("exit code = %d\n", exit_code);
		// exit(exit_code);
		npc_state.state = NPC_END;
		npc_state.halt_ret = exit_code;
		npc_state.halt_pc = *cpu.pc_if;
	}
}

void update_csr(int pc){
#ifdef CONFIG_DIFFTEST
	std::queue<csr_ctx*> csr_ctx_q;
	csr_ctx *ctx = (csr_ctx*)malloc(sizeof(csr_ctx));
	ctx->pc = pc;
	word_t *ctx_csrs = (word_t *)&(ctx->mstatus);
	word_t **cpu_csrs = (word_t **)&cpu.mstatus;
	for (int i = 0;i < NR_CSR;i ++){
		ctx_csrs[i] = *cpu_csrs[i];
	}
#endif
}

static void excute(uint64_t n)
{
	while (n--)
	{
		exec_once();
		g_nr_guest_inst++;
		if (npc_state.state != NPC_RUNNING)
		{
			break;
		}
		trace_and_difftest();
	}
}

void assert_fail_msg(){
	ringbuf_print();
#ifdef CONFIG_WAVEFORM
	tfp->dump(main_time);
	main_time++;
#endif
// delete contextp;
	tfp->close();
	delete(top);
	delete(contextp);
}


void intSignalHandler(int signum){
	signal(SIGINT, SIG_DFL);
#ifdef CONFIG_WAVEFORM
	dump_wave();
#endif
	if (tfp){
		tfp->close();
		delete tfp;
		tfp = nullptr;
	}
	printf("Waveform saved after Ctrl+C\n");
	ringbuf_print();
	lightsss.do_clear();
	if (top){
		delete(top);
	}
	if (contextp){
		delete(contextp);
	}
	raise(SIGINT);
}

void intSignalHandlerWhenSDB(int signum){
	signal(SIGINT, SIG_DFL);
	npc_state.state = NPC_STOP;
}

void abrtSignalHandler(int signum){
	signal(SIGABRT, SIG_DFL);
#ifdef CONFIG_WAVEFORM
	dump_wave();
#endif
	if (tfp){
		tfp->close();
		delete tfp;
		tfp = nullptr;
	}
	printf("Waveform saved after Aborted\n");
	ringbuf_print();
	lightsss.do_clear();
	if (top){
		delete(top);
	}
	if (contextp){
		delete(contextp);
	}
	raise(SIGABRT);
}

void segvSignalHandler(int signum){
	signal(SIGSEGV, SIG_DFL);
#ifdef CONFIG_WAVEFORM
	dump_wave();
#endif	
	if (tfp){
		tfp->close();
		delete tfp;
		tfp = nullptr;
	}
	printf("Waveform saved after Aborted\n");
	ringbuf_print();
	lightsss.do_clear();
	if (top){
		delete(top);
	}
	if (contextp){
		delete(contextp);
	}
	raise(SIGSEGV);
}

void cpu_exec(uint64_t n)
{
	g_print_step = (n < MAX_INST_TO_PRINT);
	switch (npc_state.state)
	{
	case NPC_END:
	case NPC_ABORT:
		printf("Program execution has ended. To restart the program, exit NPC and run again.\n");
		return;
	default:
		npc_state.state = NPC_RUNNING;
	}
	extern bool is_batch_mode;
	if (is_batch_mode){
		signal(SIGINT, intSignalHandler);
	} else {
		signal(SIGINT, intSignalHandlerWhenSDB);
	}
	signal(SIGABRT, abrtSignalHandler);
	signal(SIGSEGV, segvSignalHandler);
	excute(n);
	signal(SIGINT, SIG_DFL);
	signal(SIGABRT, SIG_DFL);
	signal(SIGSEGV, SIG_DFL);
	switch (npc_state.state)
	{
	case NPC_RUNNING:
		npc_state.state = NPC_STOP;
		break;

	case NPC_END:
	case NPC_ABORT:
		Log("npc: %s at pc = " FMT_WORD,
			(npc_state.state == NPC_ABORT ? ANSI_FMT("ABORT", ANSI_FG_RED) : (npc_state.halt_ret == 0 ? ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN) : ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED))),
			npc_state.halt_pc);
		#ifdef CONFIG_WAVEFORM
		#endif
		if (npc_state.state == NPC_ABORT || npc_state.halt_ret != 0){
			if (!lightsss.is_child()){
				lightsss.wakeup_child(g_nr_cycle);
				lightsss.do_clear();
			}else {
				tfp -> flush();
				tfp -> close();
				exit(0);
			}
			ringbuf_print();
		}
		// fall through
	case NPC_QUIT:
		statistic();
	}
}
