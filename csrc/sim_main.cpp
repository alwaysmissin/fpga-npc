#include <common.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <getopt.h>
#include <memory/paddr.h>
#include <verilated_fst_c.h>

#include <cpu/cpu.h>


void init_sdb();
void init_log(const char *log_file);
void init_elf(const char *elf_file);
extern "C" void init_disasm(const char *triple);
void sdb_mainloop();
void sdb_set_batch_mode();
void init_difftest(char *ref_so_file, long img_size, int port);
int is_exit_status_bad();

char* wave_file = NULL;
static char* img_file  = NULL;
static char* elf_file  = NULL;
static char* log_file  = NULL;
static char* diff_so_file = NULL;
static int difftest_port = 1234;

VerilatedContext* contextp = NULL;
VerilatedFstC* tfp = NULL;
// Vtop* top = NULL;
VysyxSoCFull* top = NULL;
// static TOP_NAME dut;

// void nvboard_bind_all_pins(Vtop* top);
static int parse_args(int argc, char *argv[]){
	const struct option table[] = {
		{"waveform", required_argument, NULL, 'w'},
		{"batch"   , no_argument	  , NULL, 'b'},
		{"log"     , required_argument, NULL, 'l'},
		{"diff"    , required_argument, NULL, 'd'},
		{"port"    , required_argument, NULL, 'p'},
    	{"elf"     , required_argument, NULL, 'e'},
		{0		   , 0                , NULL, 0}
	};
	int o;
	while( (o = getopt_long(argc, argv, "-bw:l:e:d:p:", table, NULL)) != -1){
		switch(o){
			case 'b':
				sdb_set_batch_mode();
				break;
			case 'w': 
				wave_file = optarg; 
				break;
			case 'l':
				log_file = optarg; break;
			case 'd':
				diff_so_file = optarg; break;
			case 'p':
				sscanf(optarg, "%d", &difftest_port); break;
			case 'e':
				elf_file = optarg; break;
			case 1:
				img_file = optarg;
				break;
			default: exit(0);
		}
	}
	return 0;
}

int main(int argc, char *argv[]){
	parse_args(argc, argv);
	Verilated::commandArgs(argc, argv);
	contextp = new VerilatedContext;
	contextp -> commandArgs(argc, argv);
	tfp = new VerilatedFstC;
	// top = new Vtop(contextp);
	top = new VysyxSoCFull(contextp);
#ifdef CONFIG_WAVEFORM
	Verilated::mkdir("logs");
	Verilated::traceEverOn(true);
	// top -> trace(tfp, 99); 
	top -> trace(tfp, 1);
	tfp -> open(wave_file ? wave_file : "logs/wave.vcd"); 
#endif
#ifdef RUN
	nvboard_bind_all_pins(top);
	nvboard_init();
#endif
	init_log(log_file);
	init_elf(elf_file);
	long img_size = load_img(img_file);
	
	// while(!contextp -> gotFinish()){
	// 	cpu_exec(-1);
	// }

	init_sdb();
	init_disasm("riscv32-pc-linux-gnu");
	reset(100);
#ifdef CONFIG_DIFFTEST
	init_difftest(diff_so_file, img_size, difftest_port);
#endif
	sdb_mainloop();
	// delete contextp;
	if (tfp){
		tfp->close();
		delete tfp;
		tfp = nullptr;
	}
	delete(top);
	delete(contextp);
	return is_exit_status_bad();
}
