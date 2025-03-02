#include <common.h>
#include <elf.h>

FILE *elf_fp = NULL;
bool elf_loaded = false;

// linked list of function table
typedef struct sym_functab
{
    char *name;
    Elf32_Addr value;
    uint32_t size;
    struct sym_functab *next;
} sym_functab;
static sym_functab *functab_head = NULL;

#define CALL 0
#define RET  1
typedef struct ftrace{
    uint32_t addr;
    char type;
    char *func_name;
    uint32_t func_addr;
    struct ftrace *next;
} ftrace_node;
static ftrace_node *ftrace_head = NULL;
static ftrace_node *ftrace_tail = NULL;

void extract_functab(Elf32_Sym *symtab, int symtab_num, char *strtab);

void init_elf(const char *elf_file)
{
    Elf32_Ehdr *elf_header = NULL;
    if (elf_file != NULL)
    {
        FILE *fp = fopen(elf_file, "r");
        Assert(fp, "Can not open '%s'", elf_file);
        elf_fp = fp;
        elf_header = (Elf32_Ehdr*)malloc(sizeof(Elf32_Ehdr));
    }
    else
    {
        return;
    }
    // check
    if (elf_fp != NULL && fread(elf_header, sizeof(Elf32_Ehdr), 1, elf_fp) == 1)
    {
        if (elf_header->e_ident[0] != 0x7f &&
            elf_header->e_ident[1] != 'E' &&
            elf_header->e_ident[2] != 'L' &&
            elf_header->e_ident[3] != 'F')
        {
            Log("Not a valid ELF file: %s", elf_file);
            free(elf_header);
            free(elf_fp);
            return;
        }
    }

    // read section header from elf file
    Elf32_Shdr *shdr = (Elf32_Shdr *)malloc(sizeof(Elf32_Shdr) * elf_header->e_shnum);
    if (shdr == NULL)
    {
        Log("malloc for shdr failed");
        free(elf_header);
        free(elf_fp);
        return;
    }
    fseek(elf_fp, elf_header->e_shoff, SEEK_SET);
    assert(fread(shdr, sizeof(Elf32_Shdr) * elf_header->e_shnum, 1, elf_fp) == 1);

    // read section header string table from elf file
    char shstrtab[shdr[elf_header->e_shstrndx].sh_size];
    // char *temp;
    rewind(elf_fp);
    fseek(elf_fp, shdr[elf_header->e_shstrndx].sh_offset, SEEK_SET);
    assert(fread(shstrtab, shdr[elf_header->e_shstrndx].sh_size, 1, elf_fp) == 1);

    // read symtab and strtab by section headers
    int symtab_num = 0;
    Elf32_Sym *symtab = NULL;
    char *strtab = NULL;
    for (int i = 0; i < elf_header->e_shnum; i++)
    {
        if (shdr[i].sh_type == SHT_SYMTAB)
        {
            symtab_num = shdr[i].sh_size / sizeof(Elf32_Sym);
            rewind(elf_fp);
            fseek(elf_fp, shdr[i].sh_offset, SEEK_SET);
            symtab = (Elf32_Sym *)malloc(shdr[i].sh_size);
            assert(fread(symtab, shdr[i].sh_size, 1, elf_fp) == 1);
        }
        else if (shdr[i].sh_type == SHT_STRTAB && i != elf_header->e_shstrndx)
        {
            rewind(elf_fp);
            fseek(elf_fp, shdr[i].sh_offset, SEEK_SET);
            strtab = (char *)malloc(shdr[i].sh_size);
            assert(fread(strtab, shdr[i].sh_size, 1, elf_fp) == 1);
        }
        else
        {
        }
    }
    Assert(symtab != NULL && strtab != NULL, "symtab or strtab read failed");
    Log("symtab and strtab read success");
    // puts(strtab);
    // for(int i = 0;i < symtab_num;i++){
    //     printf("symtab[%d]: %s\n", i, strtab + symtab[i].st_name);
    //     printf("Type: %d\n", ELF32_ST_TYPE(symtab[i].st_info));
    // }
    extract_functab(symtab, symtab_num, strtab);
    printf("elf file loaded\n");
    // for (sym_functab *p = functab_head->next; p != NULL; p = p->next)
    // {
    //     printf("name: %s, value: 0x%x, size: %d\n", p->name, p->value, p->size);
    // }
    elf_loaded = true;
    free(symtab);
    free(strtab);
    free(shdr);
    free(elf_header);
    fclose(elf_fp);
}

// extract function table from symtab
void extract_functab(Elf32_Sym *symtab, int symtab_num, char *strtab)
{
    functab_head = (sym_functab *)malloc(sizeof(sym_functab));
    sym_functab *prev = functab_head;
    for (int i = 0; i < symtab_num; i++)
    {
        if (ELF32_ST_TYPE(symtab[i].st_info) == STT_FUNC)
        {
            sym_functab *new_functab = (sym_functab *)malloc(sizeof(sym_functab));
            new_functab->name = (char *)malloc(strlen(strtab + symtab[i].st_name) + 1);
            strcpy(new_functab->name, strtab + symtab[i].st_name);
            new_functab->value = symtab[i].st_value;
            new_functab->size = symtab[i].st_size;
            new_functab->next = NULL;
            prev->next = new_functab;
            prev = prev->next;
        }
    }
}

char* query_funcname(word_t addr){
    for(sym_functab *p = functab_head -> next; p != NULL; p = p -> next){
        if(addr >= p -> value && addr < p -> value + p -> size){
            return p -> name;
        }
    }
    // impossible to reach here
    assert(0);
}

// print the ftrace
void show_ftrace(){
    if(ftrace_head == NULL){
        printf("ftrace is empty\n");
        return;
    }
    printf("ftrace:\n");
    int space_num = -2;
    for(ftrace_node *p = ftrace_head -> next; p != NULL; p = p -> next){
        if(p -> type == CALL){
            space_num += 2;
            printf("0x%08x: %*scall [%s@0x%08x]\n", p -> addr, space_num, "", p -> func_name, p -> func_addr);
        } else {
            printf("0x%08x: %*sret [%s]\n", p -> addr, space_num, "", p -> func_name);
            space_num -= 2;
        }
    }
}

// add ftrace node
extern "C" void ftrace(int pc, int target, int rd, int rs1){
    // if elf file not loaded, just return directly
    word_t pc_u = (word_t) pc;
    word_t target_u = (word_t)target;
    if (!elf_loaded){
        return;
    }
    if (ftrace_head == NULL){
        ftrace_head = (ftrace_node *)malloc(sizeof(ftrace_node));
        ftrace_tail = ftrace_head;
    }
    // function return
    if (rd == 0 && rs1 == 1){
        ftrace_node *new_node = (ftrace_node *)malloc(sizeof(ftrace_node));
        new_node -> addr = pc_u;
        new_node -> type = RET;
        new_node -> func_name = query_funcname(pc_u);
        new_node -> func_addr = 0;
        new_node -> next = NULL;
        ftrace_tail -> next = new_node;
        ftrace_tail = ftrace_tail -> next;
        // show_ftrace();
    }
    // function call
    else if(rd != 0) {
        extern VysyxSoCFull *top;
        // printf("pc: 0x%08x, target: 0x%08x, dnpc: 0x%08x\n", pc_u, target_u, top->rootp->top__DOT__cpu__DOT__id_stage__DOT__dnpc);
        ftrace_node *new_node = (ftrace_node *)malloc(sizeof(ftrace_node));
        new_node -> addr = pc_u;
        new_node -> type = CALL;
        new_node -> func_name = query_funcname(target_u);
        new_node -> func_addr = target_u;
        new_node -> next = NULL;
        ftrace_tail -> next = new_node;
        ftrace_tail = ftrace_tail -> next;
        // show ftrace
        // show_ftrace();
    } else {

    }
}