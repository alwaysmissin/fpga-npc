#include <common.h>
#include <cpu/cpu.h>
// #include <memory/paddr.h>
#include <readline/readline.h>
#include <readline/history.h>
#include "VysyxSoCFull__Dpi.h"
#include "sdb.h"
#include "watchpoint.h"

void reg_display();
void init_regex();
void show_ftrace();
word_t expr(char *e, bool *success);

int is_batch_mode = false;

static int cmd_help(char *args); 

static char *rl_gets()
{
    static char *line_read = NULL;

    if (line_read)
    {
        free(line_read);
        line_read = NULL;
    }

    line_read = readline("(npc) ");

    if (line_read && *line_read)
    {
        add_history(line_read);
    }

    return line_read;
}

static int cmd_c(char *args)
{
    cpu_exec(-1);
    return 0;
}

static int cmd_si(char *args)
{
    if (args == NULL)
        cpu_exec(1);
    else
    {
        int n = atoi(strtok(args, " "));
        if (n == 0)
            cpu_exec(1);
        else
            cpu_exec(n);
    }
    return 0;
}

static int cmd_q(char *args)
{
    npc_state.state = NPC_QUIT;
    return -1;
}

static int cmd_info(char *args)
{
    if (args == NULL)
    {
        printf("Please input the parameter\n");
        return 0;
    }
    char option = strtok(args, " ")[0];
    switch (option)
    {
    case 'r':
        reg_display();
        break;
    case 'w':
        info_wp();
        break;
    default:
        printf("Please input the right option, r(egisters) or w(atchpoints)\n");
        break;
    }
    return 0;
}

static int cmd_x(char *args)
{
    if (args == NULL)
    {
        printf("Please input the right format: x N EXPR\n");
        return 0;
    }
    int n = atoi(strtok(NULL, " "));
    char *hex_string;
    word_t addr;
    word_t data;

    // if(args == NULL){
    //   printf("Please input the right format: x N EXPR\n");
    //   return 0;
    // }
    hex_string = strtok(NULL, " ");
    if (hex_string == NULL)
    {
        printf("Please input the address\n");
        return 0;
    }
    sscanf(hex_string, "%x", &addr);
    for (int i = 0; i < n; i++)
    {
        // pmem_read(addr, (int*)&data);
        printf("0x%8x: 0x%08x\n", addr, data);
        addr += 4;
    }
    return 0;
}

static int cmd_p(char *args)
{
    if (args == NULL)
    {
        printf("Please input the expression: p EXPR\n");
        return 0;
    }
    bool success = false;
    word_t result = expr(args, &success);
    if (success)
        printf("%s = %u\n", args, result);
    else
        printf("Expression Error\n");
    return 0;
}

static int cmd_px(char *args)
{
    if (args == NULL)
    {
        printf("Please input the expression: p/x EXPR\n");
        return 0;
    }
    bool success = false;
    word_t result = expr(args, &success);
    if (success)
        printf("%s = 0x%08x\n", args, result);
    else
        printf("Expression Error\n");
    return 0;
}

static int cmd_w(char *args)
{
    if (args == NULL)
    {
        printf("Please input the expression to be watched: w EXPR\n");
        return 0;
    }
    bool success = false;
    word_t result = expr(args, &success);
    if (!success)
    {
        printf("Expression Error!\n");
        return 0;
    }
    WP *watchpoint = new_wp();
    if (watchpoint == NULL)
    {
        printf("There is no free node left\n");
        return 0;
    }
    // set the properity of watchpoint
    strcpy(watchpoint->What, args);
    watchpoint->value = result;
    printf("$%d: %s\n", watchpoint->NO, watchpoint->What);
    return 0;
}

static int cmd_d(char *args)
{
    if (args == NULL)
    {
        printf("Please input the NO of watchpoint to delete: d NO\n");
        return 0;
    }
    int NO = atoi(args);
    free_wp(NO);
    return 0;
}

void sdb_set_batch_mode()
{
    is_batch_mode = true;
}

static int cmd_bt(char *args){
  show_ftrace();
  return 0;
}

static struct
{
    const char *name;
    const char *description;
    int (*handler)(char *);
} cmd_table[] = {
    {"help", "Display information about all supported commands", cmd_help},
    {"c", "Continue the execution of the program", cmd_c},
    {"q", "Exit NPC", cmd_q},
    {"si", "Step N instructions and then pause", cmd_si},
    {"info", "print the status of registers or the infomation of watchpoints", cmd_info},
    {"x", "scan the memory and print it", cmd_x},
    {"p", "Get the value of the EXPR", cmd_p},
    {"p/x", "Get the value of the EXPR and print in hex", cmd_px},
    {"w", "Set watchpoint", cmd_w},
    {"d", "Delete the assigned watchpoint", cmd_d},
    { "bt", "Print the ftrace", cmd_bt},
};

#define NR_CMD ARRLEN(cmd_table)

static int cmd_help(char *args) {
  /* extract the first argument */
  char *arg = strtok(NULL, " ");
  int i;

  if (arg == NULL) {
    /* no argument given */
    for (i = 0; i < NR_CMD; i ++) {
      printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
    }
  }
  else {
    for (i = 0; i < NR_CMD; i ++) {
      if (strcmp(arg, cmd_table[i].name) == 0) {
        printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
        return 0;
      }
    }
    printf("Unknown command '%s'\n", arg);
  }
  return 0;
}


void sdb_mainloop()
{
    if (is_batch_mode)
    {
        cmd_c(NULL);
        return;
    }

    for (char *str; (str = rl_gets()) != NULL;)
    {
        char *str_end = str + strlen(str);

        char *cmd = strtok(str, " ");
        if (cmd == NULL)
        {
            continue;
        }

        char *args = cmd + strlen(cmd) + 1;
        if (args >= str_end)
        {
            args = NULL;
        }

        int i;
        for (i = 0; i < NR_CMD; i++)
        {
            if (strcmp(cmd, cmd_table[i].name) == 0)
            {
                if (cmd_table[i].handler(args) < 0)
                {
                    return;
                }
                break;
            }
        }

        if (i == NR_CMD)
        {
            printf("Unknown command '%s'\n", cmd);
        }
    }
}

void init_sdb()
{
    /* Compile the refular expressions. */
    init_regex();
    init_wp_pool();
}