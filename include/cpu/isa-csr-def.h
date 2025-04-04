#ifndef __ISA_CSR_DEF_H__
#define __ISA_CSR_DEF_H__
#include <common.h>

#define NR_CSR 8

#define CSR_LIST(def) \
    def(mstatus  , 0x300, 0x00227888, true , 0x00001800) \
    def(mtvec    , 0x305, 0xffffffff, true , 0x00000000) \
    def(mscratch , 0x340, 0xffffffff, false, 0x00000000) \
    def(mepc     , 0x341, 0xffffffff, true , 0x00000000) \
    def(mcause   , 0x342, 0xffffffff, true , 0x00000000) \
    def(satp     , 0x180, 0xffffffff, false, 0x00000000) \
    def(mvendorid, 0xf11, 0xffffffff, false, 0x79737978) \
    def(marchid  , 0xf12, 0xffffffff, false, 0x23060051)

#define CSR_DEF(_name, _addr, _mask, _is_reg, _init_val) \
    _name##_t* _name;

// 新增两个辅助宏来处理不同情况
#define CSR_INIT_true(_name, _addr, _mask, _is_reg, _init_val) \
    cpu._name = (_name##_t*)&top->rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csrRegs__DOT__##_name;

#define CSR_INIT_false(_name, _addr, _mask, _is_reg, _init_val) \
    cpu._name = (_name##_t*)malloc(sizeof(_name##_t)); \
    cpu._name->val = _init_val;

// 使用条件表达式选择宏（需要将true/false转换为1/0）
#define CSR_INIT_TRUE_OR_FALSE(cond) CSR_INIT_##cond
#define CSR_INIT(_name, _addr, _mask, _is_reg, _init_val) \
    CSR_INIT_TRUE_OR_FALSE(_is_reg)(_name, _addr, _mask, _is_reg, _init_val)

#define CSR_DEF_DIFFCTX(_name, _iaddr, _wmask, _is_reg, _init) \
  word_t _name; \

#define CSR_GET(_name, _iaddr, _wmask, _is_reg, _init) \
  (ctx->_name) = state->csrmap[_iaddr]->read(); \

#define CSR_SET(_name, _iaddr, _wmask, _is_reg, _init) \
  state->csrmap[_iaddr]->write(ctx->_name); \

#define CSR_STR(_name, _addr, _wmask, _is_reg, _init) \
  ""#_name"", \


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

typedef union mscratch_t{
    word_t val;
    struct mscratch_data{
        int mscratch : 32;
    } fields;
} mscratch_t;

typedef union satp_t{
    word_t val;
    struct satp{
        int ppn : 22;
        int asid:  9;
        int mode:  1;
    } fields;
} satp_t;

#endif