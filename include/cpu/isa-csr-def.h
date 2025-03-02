#ifndef __ISA_CSR_DEF_H__
#define __ISA_CSR_DEF_H__
#include <common.h>

#define NR_CSR 6

typedef union mstatus_t{
    word_t val;
    struct mstatus_data{
        int UIE   : 1;
        int SIE   : 1;
        int       : 1;
        int MIE   : 1;
        int UPIE  : 1;
        int SPIE  : 1;
        int       : 1;
        int MPIE  : 1;
        int SPP   : 1;
        int       : 2;
        int MPP   : 2;
        int FS    : 2;
        int XS    : 2;
        int MPRV  : 1;
        int SUM   : 1;
        int MXR   : 1;
        int TVM   : 1;
        int TW    : 1;
        int TSR   : 1;
        int       : 8;
        int SD    : 1;
    } fields;
} mstatus_t;

typedef union mtvec_t{
    word_t val;
    struct mtvec_data{
        int MODE  : 2;
        int BASE  : 30;
    } fields;
} mtvec_t;

typedef union mepc_t{
    word_t val;
    struct mepc_data{
        int mepc : 32;
    } fields;
} mepc_t;

typedef union mcause_t{
    word_t val;
    struct mcause_data{
        int code : 31;
        int intr : 1;
    } fields;
} mcause_t;

typedef union mvendorid_t{
    word_t val;
    struct mvendorid{
        int bank  : 25;
        int offset: 7;
    } fields;
} mvendorid_t;

typedef union marchid_t{
    word_t val;
    struct marchid{
        int archid: 32;
    } fields;
} marchid_t;

#endif