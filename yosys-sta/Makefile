PROJ_PATH = $(shell pwd)

DESIGN ?= ysyx_23060051
SDC_FILE ?= $(PROJ_PATH)/npc.sdc
RTL_FILES ?= $(shell find $(NPC_HOME)/vsrc/cpu -name "*.v" -or -name "*.sv")
COMMIT ?= $(shell git rev-parse --short HEAD)
export CLK_FREQ_MHZ ?= 100

RESULT_DIR = $(PROJ_PATH)/result/$(DESIGN)-$(CLK_FREQ_MHZ)MHz-$(COMMIT)
SCRIPT_DIR = $(PROJ_PATH)/scripts
NETLIST_SYN_V   = $(RESULT_DIR)/$(DESIGN).netlist.syn.v
NETLIST_FIXED_V = $(RESULT_DIR)/$(DESIGN).netlist.fixed.v
TIMING_RPT = $(RESULT_DIR)/$(DESIGN).rpt

init:
	bash -c "$$(wget -O - https://ysyx.oscc.cc/slides/resources/scripts/init-yosys-sta.sh)"

syn: $(NETLIST_SYN_V)
$(NETLIST_SYN_V): $(RTL_FILES) $(SCRIPT_DIR)/yosys.tcl
	mkdir -p $(@D)
	echo tcl $(SCRIPT_DIR)/yosys.tcl $(DESIGN) \"$(RTL_FILES)\" $@ | yosys -l $(@D)/yosys.log -s -

fix-fanout: $(NETLIST_FIXED_V)
$(NETLIST_FIXED_V): $(SCRIPT_DIR)/fix-fanout.tcl $(SDC_FILE) $(NETLIST_SYN_V)
	./bin/iEDA -script $^ $(DESIGN) $@ 2>&1 | tee $(RESULT_DIR)/fix-fanout.log

sta: $(TIMING_RPT)
$(TIMING_RPT): $(SCRIPT_DIR)/sta.tcl $(SDC_FILE) $(NETLIST_FIXED_V)
	./bin/iEDA -script $^ $(DESIGN) 2>&1 | tee $(RESULT_DIR)/sta.log
	cat $(RESULT_DIR)/synth_stat.txt | grep "Chip area for" | tee $(RESULT_DIR)/chip-area-summary.txt

clean:
	-rm -rf result/

.PHONY: init syn fix-fanout sta clean
