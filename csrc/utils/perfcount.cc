#include <common.h>
#include <cpu/cpu.h>
#include <queue>
uint64_t IFUCounter = 0;
uint64_t ALUInstCounter = 0;
uint64_t LSUInstCounter = 0;
uint64_t BRUInstCounter = 0;
uint64_t CSRInstCounter = 0;
uint64_t InstFlushedCounter = 0;
uint64_t ALUCounter = 0;
uint64_t LSUCounter = 0;
void PerfCountIFU(){
  IFUCounter ++;

}

#define FuType_ALU 0
#define FuTYpe_LSU 1
#define FuType_BRU 2
#define FuType_CSR 3
void PerfCountIDU(char fuType){
  switch(fuType){
    case FuType_ALU:
      ALUInstCounter ++;
      break;
    case FuTYpe_LSU:
      LSUInstCounter ++;
      break;
    case FuType_BRU:
      BRUInstCounter ++;
      break;
    case FuType_CSR:
      CSRInstCounter ++;
      break;
  }
}

void PerfCountInstFlushed(){
  InstFlushedCounter ++;
}

void PerfCountEXU(){
  ALUCounter ++;
}

void PerfCountLSU(){
  LSUCounter ++;
}

// 用来统计IFU和LSU的时延
std::queue<uint64_t> IFURChannelQue;
std::queue<uint64_t> LSURChannelQue;
std::queue<uint64_t> LSUWChannelQue;

uint64_t IFURReqCounter = 0;
uint64_t IFURChannelLatencyTotal = 0;
uint64_t LSURReqCounter = 0;
uint64_t LSURChannelLatencyTotal = 0;
uint64_t LSUWReqCounter = 0;
uint64_t LSUWChannelLatencyTotal = 0;
extern uint64_t g_nr_cycle;

void IFULaunchARReq(){
  IFURChannelQue.push(g_nr_cycle);
}

void IFURecvRResp(){
  uint64_t ifuLaunchARReqCycle = IFURChannelQue.front();
  IFURChannelQue.pop();
  IFURChannelLatencyTotal += g_nr_cycle - ifuLaunchARReqCycle;
  IFURReqCounter ++;
}

void LSULaunchARReq(){
  LSURChannelQue.push(g_nr_cycle);
}

void LSURecvRResp(){
  uint64_t lsuLaunchARReqCycle = LSURChannelQue.front();
  LSURChannelQue.pop();
  LSURChannelLatencyTotal += g_nr_cycle - lsuLaunchARReqCycle;
  LSURReqCounter ++;
}

void LSULaunchAWReq(){
  LSUWChannelQue.push(g_nr_cycle);
}

void LSURecvBResp(){
  uint64_t lsuLaunchAWWreqCycle = LSUWChannelQue.front();
  LSUWChannelQue.pop();
  LSUWChannelLatencyTotal += g_nr_cycle - lsuLaunchAWWreqCycle;
  LSUWReqCounter ++;
}

// uint64_t icacheAccessCounter = 0;
uint64_t icacheMissCounter = 0;
uint64_t icacheSkipCounter = 0;

// void PerfCountICacheAccess(){
//   icacheAccessCounter ++;
// }
void PerfCountICacheMiss(){
  icacheMissCounter ++;

}

void PerfCountSkipCache(){
  icacheSkipCounter ++;
}