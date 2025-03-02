/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/
#include <common.h>
#include "VysyxSoCFull__Dpi.h" 
/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>
word_t isa_reg_str2val(const char *s, bool *success);

// Lower priority is set a lower number
enum
{
  /* TODO: Add more token types */
  TK_NOTYPE = 256,
  NUM,
  HEX_NUM,
  REG_NAME,
  TK_OR,
  TK_AND,
  TK_EQ,
  TK_UEQ,
  TK_PLUS,
  TK_SUB,
  TK_MULT,
  TK_DIV,
  DEREF,
  PLACE_HOLDER1,
  TK_LBRACKET,
  TK_RBRACKET,
};

static struct rule
{
  const char *regex;
  int token_type;
} rules[] = {

    /* TODO: Add more rules.
     * Pay attention to the precedence level of different rules.
     */

    {" +", TK_NOTYPE},            // spaces
    {"0x[0-9|a-f]+", HEX_NUM},    // hex num
    {"-?[0-9]+", NUM},            // decimal integer num
    {"\\$[a-z0-9]{2}", REG_NAME}, // reg name
    {"\\(", TK_LBRACKET},         // left bracket
    {"\\)", TK_RBRACKET},         // right bracket
    {"\\+", TK_PLUS},             // plus
    {"\\-", TK_SUB},              // sub
    {"\\*", TK_MULT},             // times
    {"\\/", TK_DIV},              // divide
    {"==", TK_EQ},                // equal
    {"!=", TK_UEQ},               // unequal
    {"&&", TK_AND},               // bool and
    {"\\|\\|", TK_OR},            // bool or
};

#define NR_REGEX ARRLEN(rules)

static regex_t re[NR_REGEX] = {};

/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex()
{
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i++)
  {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0)
    {
      regerror(ret, &re[i], error_msg, 128);
      // panic("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
  }
}

typedef struct token
{
  int type;
  char str[32];
} Token;

static Token tokens[1024] __attribute__((used)) = {};
static int nr_token __attribute__((used)) = 0;

static bool make_token(char *e)
{
  int position = 0;
  int i;
  regmatch_t pmatch;

  nr_token = 0;

  while (e[position] != '\0')
  {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i++)
    {
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 && pmatch.rm_so == 0)
      {
        char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        // Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s",
        //     i, rules[i].regex, position, substr_len, substr_len, substr_start);

        position += substr_len;

        /* TODO: Now a new token is recognized with rules[i]. Add codes
         * to record the token in the array `tokens'. For certain types
         * of tokens, some extra actions should be performed.
         */

        switch (rules[i].token_type)
        {
        case (NUM):
          // todo:
          assert(substr_len < 32);
          // if the previous token's type is NUM, the next must be an operation
          // so, if the next is stil NUM, it should be a minus num(if a num)
          // then, seprate the minus operation and the num, add them to tokens array
          if ((nr_token == 0 || tokens[nr_token - 1].type == NUM || tokens[nr_token - 1].type == TK_RBRACKET) && substr_start[0] == '-')
          {
            tokens[nr_token++].type = TK_SUB;
            snprintf(tokens[nr_token].str, substr_len, "%s\n", substr_start + 1);
            tokens[nr_token++].type = NUM;
          }
          else
          {
            snprintf(tokens[nr_token].str, substr_len + 1, "%s\n", substr_start);
            tokens[nr_token++].type = NUM;
          }
          // assert(check == substr_len);
          break;
        case (HEX_NUM):
          assert(substr_len < 32);
          snprintf(tokens[nr_token].str, substr_len + 1, "%s\n", substr_start);
          tokens[nr_token++].type = HEX_NUM;
          break;
        case (REG_NAME):
          assert(substr_start[0] == '$');
          snprintf(tokens[nr_token].str, substr_len, "%s\n", substr_start + 1);
          tokens[nr_token++].type = REG_NAME;
          break;
        case (TK_NOTYPE):
          break;
        case (TK_MULT):
          if (nr_token == 0)
          {
            tokens[nr_token++].type = DEREF;
          }
          else
          {
            if (tokens[nr_token - 1].type == NUM || tokens[nr_token - 1].type == TK_RBRACKET || tokens[nr_token - 1].type == HEX_NUM || tokens[nr_token - 1].type == REG_NAME)
              tokens[nr_token++].type = TK_MULT;
            else
              tokens[nr_token++].type = DEREF;
          }
          break;
        default:
          tokens[nr_token++].type = rules[i].token_type;
        }

        break;
      }
    }

    if (i == NR_REGEX)
    {
      printf("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  return true;
}

static bool check_parentheses(word_t p, word_t q, bool *bad)
{
  if (tokens[p].type == TK_LBRACKET && tokens[q].type == TK_RBRACKET)
  {
    int cnt = 0;
    bool flag = false;
    for (int i = p; i <= q; i++)
    {
      if (tokens[i].type == TK_LBRACKET)
        cnt++;
      else if (tokens[i].type == TK_RBRACKET)
      {
        cnt--;
        if (cnt < 0)
          break;
        else if (cnt == 0 && i < q)
        {
          flag = true;
        }
      }
    }
    if (cnt == 0)
    {
      // check over and legal
      // 2 situation: ((())), ()()()
      if (bad)
        *bad = false;
      return !flag;
    }
    else
    {
      if (bad)
        *bad = true;
      return false;
    }
  }
  return false;
}

int get_main_operation_index(int *p, int *q)
{
  int main_operation_index = *p;
  for (int i = *p; i < *q; i++)
  {
    if (tokens[i].type == NUM || tokens[i].type == HEX_NUM || tokens[i].type == REG_NAME)
      continue;
    // skip the bracket
    if (tokens[i].type == TK_LBRACKET)
    {
      int cnt = 0;
      int j;
      for (j = i; j < nr_token; j++)
      {
        if (tokens[j].type == TK_LBRACKET)
          cnt++;
        else if (tokens[j].type == TK_RBRACKET)
          cnt--;
        if (cnt == 0)
          break;
      }
      i = j;
    }

    // if current operation priority is lower, set it as main operation
    if (tokens[i].type <= tokens[main_operation_index].type ||
        // or, if these two operation priority is the same, set it as main operation
        (tokens[i].type & 0xffffffffe) == (tokens[main_operation_index].type & 0xfffffffe) ||
        (main_operation_index == *p &&
         (tokens[main_operation_index].type == NUM || tokens[main_operation_index].type == HEX_NUM || tokens[main_operation_index].type == REG_NAME)))
      main_operation_index = i;
  }
  return main_operation_index;
}

static int eval(int p, int q, bool *success)
{
  if (p > q && q >= 0)
  {
    *success = false;
  }
  else if (q < 0)
    return 0;
  else if (p == q)
  {
    // Here is a single token
    // handle the hex num, reg, and pointer here
    *success = true;
    int result;
    switch (tokens[p].type)
    {
    case NUM:
      result = atoi(tokens[p].str);
      break;
    case HEX_NUM:
      sscanf(tokens[p].str, "%x", &result);
      break;
    case REG_NAME:
      result = isa_reg_str2val(tokens[p].str, success);
      break;
    default:
      break;
    }
    return result;
  }
  else if (check_parentheses(p, q, NULL) == true)
  {
    return eval(p + 1, q - 1, success);
  }
  else
  {
    // get the main operation
    int op = get_main_operation_index(&p, &q);
    int value1 = eval(p, op - 1, success);
    int value2 = eval(op + 1, q, success);
    switch (tokens[op].type)
    {
    case TK_PLUS:
      return value1 + value2;
    case TK_SUB:
      return value1 - value2;
    case TK_MULT:
      return value1 * value2;
    case TK_DIV:
      if (value2 != 0)
        return value1 / value2;
      else
      {
        *success = false;
        printf("Error: Divided by 0.\n");
      }
      break;
    case DEREF:
      if (value2 >= 0x80000000 && value2 <= 0x8fffffff)
      {
        int result = 0;
        // pmem_read(value2, &result);
        return result;
      }
      else
      {
        *success = false;
        printf("address = 0x%8x is out of bound of pmrm [0x80000000, 0x87ffffff]\n", value2);
      }
      break;
    case TK_EQ:
      return value1 == value2;
    case TK_UEQ:
      return value1 != value2;
    case TK_AND:
      return (value1 != 0) && (value2 != 0);
    case TK_OR:
      return (value1 != 0) || (value2 != 0);
    default:
      assert(0);
    }
  }
  return 0;
}

word_t expr(char *e, bool *success)
{
  memset(tokens, 0, sizeof(tokens));
  nr_token = 0;
  if (!make_token(e))
  {
    *success = false;
    return 0;
  }
  // check the expression legal
  bool bad = false;
  word_t result = 0;
  check_parentheses(0, nr_token - 1, &bad);
  // the expression is legal, continue evalue it
  if (!bad)
  {
    *success = true;
    result = (word_t)eval(0, nr_token - 1, success);
  }
  else
  {
    printf("Bad expression\n");
  }

  return result;
}
