BUILD_DIR = ./build
TARGET = ysyx_23060051.sv
MILL_VERSION = 0.12.4
gen_args ?= ""
FPGA_NPC_HOME = /home/jiunian/Program/fpga/npc

PRJ = npc_chisel

test:
	MILL_VERSION=$(MILL_VERSION) mill -i $(PRJ).test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	MILL_VERSION=$(MILL_VERSION) mill -i $(PRJ).runMain Elaborate $(gen_args) --target-dir $(BUILD_DIR)
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $(BUILD_DIR)/$(TARGET)

npc: verilog
	rm -rf $(FPGA_NPC_HOME)/vsrc/cpu
	mkdir -p $(FPGA_NPC_HOME)/vsrc/cpu
	cp $(BUILD_DIR)/*.sv $(FPGA_NPC_HOME)/vsrc/cpu

sim-npc: npc
	cd $(FPGA_NPC_HOME) && make sim

help:
	mill -i $(PRJ).runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean-npc:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help reformat checkformat clean-npc npc

# sim:
# 	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
# 	@echo "Write this Makefile by yourself."
