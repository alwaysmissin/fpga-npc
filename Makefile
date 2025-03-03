TOPNAME = ysyxSoCFull
NXDC_FILEs = constr/top.nxdc
INC_PATH = ./include /usr/lib/llvm-14/include
VINC_PATH = vsrc vsrc/perip/uart16550/rtl vsrc/perip/spi/rtl
FPGA_CORE_PATH = /mnt/e/coding/graduation/cpu/cpu.srcs/sources_1/new/core

-include $(NPC_HOME)/include/config/auto.conf
-include $(NPC_HOME)/include/config/audo.conf.cmd

# INC_PATH += $(wildcard $(shell find ./include -type d))
# source code of cpp or v
C_SOURCE = $(shell find $(abspath ./csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
V_SOURCE = $(shell find $(abspath ./vsrc) -name "*.v" -or -name "*.sv")

# build dir
BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
V_TARGET = $(BUILD_DIR)/V$(TOPNAME)

include $(NVBOARD_HOME)/scripts/nvboard.mk
# flags for build
WNO = UNUSEDSIGNAL DECLFILENAME PINCONNECTEMPTY ASSIGNDLY DEFPARAM UNDRIVEN UNUSEDPARAM SYNCASYNCNET
WNOFLAGS = $(addprefix -Wno-, $(WNO))
VINCFLAGS = $(addprefix -I, $(abspath $(VINC_PATH)))
VERILATOR_FLAGS = -Wall --cc --build --trace-fst $(WNOFLAGS) $(VINCFLAGS) --timescale "1ns/1ns" --no-timing --autoflush
VERILAROR_FLAGS_NVB = 
INCFLAGS = $(addprefix -I, $(abspath $(INC_PATH)))
CLFAGS_TRACE += -DITRACE_COND=true
CFLAGS_TRACE += -DMTRACE_COND=true
CFLAGS += -g $(INCFLAGS) $(CFLAGS_TRACE) -DTOP_NAME="\"V$(TOPNAME)\"" -std=c++14 -fno-exceptions -D_GNU_SOURCE -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS -fPIE
LDFLAGS += -lSDL2 -lSDL2_image -lreadline $(shell llvm-config --libs)

include $(NPC_HOME)/scripts/config.mk

ARGS ?= -w $(NPC_HOME)/waveforms/npc-wave.fst -l $(NPC_HOME)/logs/npc-log.txt -d $(NEMU_HOME)/build/riscv32-nemu-interpreter-so -b
ELF ?= -e $(YSYX_HOME)/am-kernels/tests/cpu-tests/build/add-riscv32e-ysyxsoc.elf
IMG ?= $(YSYX_HOME)/am-kernels/tests/cpu-tests/build/add-riscv32e-ysyxsoc.bin

WAVE ?= $(NPC_HOME)/waveforms/npc-wave.fst
WAVECFG ?= $(NPC_HOME)/waveforms/npc-wave-config.gtkw

#constraint file generation
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND) : $(NXDC_FILEs)
	@mkdir -p $(BUILD_DIR)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@


all: sim 
	@echo "Write this Makefile by your self."

# simulation
sim: $(V_SOURCE) $(C_SOURCE)
	mkdir -p $(BUILD_DIR)
# verilator $(VERILATOR_FLAGS) \
# 	--top-module $(TOPNAME) $^ 
	verilator $(VERILATOR_FLAGS)  \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS))\
	 	--Mdir $(OBJ_DIR) --exe -o $(abspath $(V_TARGET))
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(V_TARGET) $(ARGS) -e $(ELF) $(IMG)


wave: sim
	gtkwave $(WAVE) $(WAVECFG)

C_SOURCE += $(SRC_AUTO_BIND)
# nvboard simulation
run: $(V_SOURCE) $(C_SOURCE) $(NVBOARD_ARCHIVE) $(SRC_AUTO_BIND)
	mkdir -p $(BUILD_DIR)
	verilator $(VERILATOR_FLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CFLAGS)) -CFLAGS -DRUN $(addprefix -LDFLAGS , $(LDFLAGS))\
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(V_TARGET))
	$(V_TARGET) $(ARGS) -b -e $(ELF) $(IMG)

perf: 
	make -C ./npc_chisel npc gen_args="sta"
	make -C ./yosys-sta sta

fpga:
	make -C ./npc_chisel npc gen_args="fpga"
	rm -rf $(FPGA_CORE_PATH)
	mkdir -pv $(FPGA_CORE_PATH)
	cp ./vsrc/cpu/*.sv $(FPGA_CORE_PATH)


.PHONY: clean sim run perf

# clean 
clean:
	rm -rf $(BUILD_DIR)
# include ../Makefile
