#include <common.h>

extern uint64_t g_nr_guest_inst;
FILE *log_fp = NULL;

#define TRACE_START 0
#define TRACE_END 10000

void init_log(const char *log_file){
    log_fp = stdout;
    if (log_file != NULL){
        FILE *fp = fopen(log_file, "w");
        assert(fp != NULL);
        log_fp = fp;
    }
    Log("Log is written to %s", log_file ? log_file : "stdout");
}

// bool log_enable(){
//     return g_nr_guest_inst >= TRACE_START && g_nr_guest_inst <= TRACE_END;
// }