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

#include "sdb.h"
#include "watchpoint.h"
#define NR_WP 32



static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool() {
  int i;
  for (i = 0; i < NR_WP; i ++) {
    wp_pool[i].NO = i;
    wp_pool[i].next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]);
    wp_pool[i].trigger_flag = false;
  }

  head = NULL;
  free_ = wp_pool;
}

// if there is no free node, return NULL
WP* new_wp(){
  if(free_ == NULL){
    return NULL;
  }
  // get a free node from free_
  WP* node = free_;
  free_ = free_ -> next;

  // if current head is NULL, make the node be head
  if(head == NULL){
    head = node;
    node -> next = NULL;
  }
  // else, insert the new node into the head list
  else{
    WP* iter = NULL;
    WP* pre = NULL;
    for(iter = head ; iter != NULL ; pre = iter, iter = iter -> next){
      if(node -> NO < iter -> NO){
        if(pre == NULL){
          head = node;
        } else {
          pre -> next = node;
        }
        node -> next = iter;
        break;
      } else if(iter -> next == NULL){
        iter -> next = node;
        node -> next = NULL;
      }
    }
  }

  assert(node != NULL);
  return node;
}

void free_wp(word_t n){
  if(n > 32 || n < 0){
    printf("No such watchpoint.\n");
    return ;
  }
  WP* node = NULL;
  // search for NO.N node in head list
  WP* iter = NULL;
  WP* pre = NULL;
  for(iter = head; iter != NULL ; pre = iter, iter = iter -> next){
    if(iter -> NO == n){
      if(pre == NULL){
        head = iter -> next;
      }
      else{
        pre -> next = iter -> next;
      }
      node = iter;
      break;
    }
    if(iter -> next == NULL){
      printf("No.%d watchpoints haven't been used", n);
    }
  }
  // make sure we have found the correct node
  assert(node != NULL);
  memset(node->What, 0, sizeof(node->What));
  node -> value = 0;
  for(pre = NULL, iter = free_ ; iter != NULL ; pre = iter, iter = iter -> next){
    if(node -> NO < iter -> NO){
      if(pre == NULL){
        free_ = node;
      } else {
        pre -> next = node;
      }
      node -> next = iter;
      break;
    } else if(iter -> next == NULL){
      iter -> next = node;
      node -> next = NULL;
    }
  }
}

void info_wp(){
  printf("NO\t\tWhat\n");
  WP* iter = NULL;
  for(iter = head;iter != NULL;iter = iter -> next){
    printf("%d\t\t%s\n", iter -> NO, iter -> What);
  }
}

WP* check_diff(word_t* old_value){
  WP* iter = NULL;
  word_t value;
  bool success;
  for(iter = head;iter != NULL;iter = iter -> next){
    value = expr(iter -> What, &success);
    if(value != iter -> value){
      *old_value = iter -> value;
      iter -> value = value;
      return iter;
    }
  }
  return NULL;
}
