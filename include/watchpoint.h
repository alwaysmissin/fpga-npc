#ifndef WATCHPOINT_H
#define WATCHPOINT_H

typedef struct watchpoint {
  int NO;
  struct watchpoint *next;
  char What[65535];   // What, Expression
  word_t value;
  bool trigger_flag;
  /* TODO: Add more members if necessary */

} WP;
void init_wp_pool();
WP* new_wp();
void free_wp(word_t n);
void info_wp();
WP* check_diff(word_t* old_value);

#endif