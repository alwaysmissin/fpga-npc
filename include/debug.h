#ifndef __DEBUG_H__
#define __DEBUG_H__

#include <common.h>
#include <utils.h>

void assert_fail_msg();
#define Assert(cond, format, ...) \
    do { \
        if (!(cond)) { \
            printf(ANSI_FMT(format, ANSI_FG_RED) "\n", ## __VA_ARGS__); \
            assert_fail_msg(); \
            assert(cond); \
        } \
    } while(0)

#define Log(format, ...) \
    _Log(ANSI_FMT("[%s:%d %s] " format, ANSI_FG_BLUE) "\n", \
        __FILE__, __LINE__, __func__, ## __VA_ARGS__)


#endif