/* SPDX-License-Identifier: GPL-2.0-only
 * conjunto_de_conceitos / disc.c  ("discador": evoca BBS/modem dos anos 90)
 * Autoria: Rafael Melo Reis. Clean-room, sem reuso de terceiros.
 * CLI freestanding: logo ASCII colorido, flags com default, menu estilo BBS,
 * benchmark NEON-vs-escalar. Sem libc, sem heap, sem GC, sem abstracao.
 * Entrada por _start cru (sem crt). Saida por syscalls.
 */
#include "../include/abi.h"

/* externs autorais (prim.c / net.c) */
extern u64 cdc_sum8_simd(const u8*, sz);
extern u64 cdc_sum8_scalar(const u8*, sz);
extern sz  cdc_u2s(u64, char*);
extern sz  cdc_strlen(const char*);
extern v0* memset(v0*, int, unsigned long);
extern sz  cdc_http_get_ip(u8,u8,u8,u8,u16,const char*,const char*,char*,sz);

/* ---- estado minimo (sem nomear muito; flags em bits) ---- */
static sz g_color = 1;

/* ---- saida sem stdio ---- */
static v0 put(const char* s){ cdc_write(1, s, cdc_strlen(s)); }
static v0 putn(u64 v){ char b[24]; sz n=cdc_u2s(v,b); cdc_write(1,b,n); }
static v0 col(const char* esc){ if(g_color) put(esc); }

/* ---- comparacao de string sem libc ---- */
static sz eq(const char* a, const char* b){
  sz i=0; while(a[i] && a[i]==b[i]) i++; return a[i]==0 && b[i]==0;
}

/* ---- logotipo ASCII colorido (autoral) ---- */
static v0 logo(v0){
  col("\x1b[1;36m");
  put("  ____  ___  _   _  ___ _   _ _   _ _____ ___  \n");
  put(" / ___|/ _ \\| \\ | |/ __| | | | \\ | |_   _/ _ \\ \n");
  put("| |   | | | |  \\| | |  | | | |  \\| | | || | | |\n");
  put("| |___| |_| | |\\  | |__| |_| | |\\  | | || |_| |\n");
  col("\x1b[1;35m");
  put(" \\____|\\___/|_| \\_|\\___|\\___/|_| \\_| |_| \\___/ \n");
  col("\x1b[0;90m");
  put("        d e   c o n c e i t o s  ::  discador\n");
  col("\x1b[1;33m");
  put("    freestanding | arm64/arm32 | NEON | sem heap\n");
  col("\x1b[0m");
}

/* ---- catalogo estilo BBS: conceitos selecionaveis ---- */
static v0 menu(v0){
  col("\x1b[1;32m");          put("\n  [ CATALOGO BBS / selecione ]\n\n"); col("\x1b[0m");
  put("   1) bench    : NEON vs escalar (reducao de bytes)\n");
  put("   2) fetch    : HTTP/1.0 GET por IP literal (http puro)\n");
  put("   3) caps     : contador de ciclos + frequencia\n");
  put("   4) conceitos: imprime o conjunto de conceitos\n");
  put("   0) sair\n\n");
  col("\x1b[1;36m");          put("  > "); col("\x1b[0m");
}

/* ---- benchmark: largura de banda da reducao ---- */
static u8 g_buf[1<<20];      /* 1 MiB em .bss, sem malloc */

static v0 bench(v0){
  memset(g_buf, 0xA5, sizeof(g_buf));
  u64 f = cdc_freq();
  /* aquece e mede SIMD */
  u64 t0=cdc_cycles(); u64 s1=cdc_sum8_simd(g_buf,sizeof(g_buf)); u64 t1=cdc_cycles();
  u64 s2=cdc_sum8_scalar(g_buf,sizeof(g_buf)); u64 t2=cdc_cycles();
  u64 dc_simd = t1-t0, dc_scal = t2-t1;
  col("\x1b[1;33m"); put("\n  [bench] reducao de 1 MiB\n"); col("\x1b[0m");
  put("   soma(simd)   = "); putn(s1); put("\n");
  put("   soma(escalar)= "); putn(s2); put("   (igual = correto)\n");
  put("   ciclos simd  = "); putn(dc_simd); put("\n");
  put("   ciclos escal = "); putn(dc_scal); put("\n");
  put("   cntfrq_el0   = "); putn(f); put(" Hz\n");
  /* speedup x100 (sem ponto flutuante) */
  if(dc_simd){ put("   speedup x100 = "); putn((dc_scal*100)/dc_simd); put("\n"); }
}

static v0 caps(v0){
  col("\x1b[1;33m"); put("\n  [caps]\n"); col("\x1b[0m");
  put("   cntvct_el0 = "); putn(cdc_cycles()); put("\n");
  put("   cntfrq_el0 = "); putn(cdc_freq());  put(" Hz\n");
}

static v0 conceitos(v0){
  col("\x1b[1;35m"); put("\n  [conjunto de conceitos] ver CONCEITOS.md\n"); col("\x1b[0m");
  put("   - syscall cru (svc/swi)        : I/O sem libc\n");
  put("   - SIMD NEON + blocagem cache    : largura de banda\n");
  put("   - branchless / void / sem heap  : previsibilidade\n");
  put("   - TCP cru + HTTP/1.0            : aquisicao de dados\n");
  put("   - contador cntvct_el0           : medicao honesta\n");
}

/* exemplo fixo de fetch (IP literal; sem DNS no caminho default) */
static v0 fetch_demo(v0){
  static char out[1<<16];
  col("\x1b[0;90m");
  put("\n  [fetch] GET http://example -> IP literal 93.184.216.34:80\n");
  put("  (runtime depende de rede do dispositivo; http puro)\n");
  col("\x1b[0m");
  sz k = cdc_http_get_ip(93,184,216,34, 80, "example.com", "/", out, sizeof(out));
  if(k<=0){ put("  sem resposta (offline ou bloqueado)\n"); return; }
  put("  bytes recebidos = "); putn((u64)k); put("\n");
  cdc_write(1, out, k< 256 ? k : 256);
  put("\n");
}

/* ---- loop de menu (le 1 digito do stdin) ---- */
static v0 loop(v0){
  for(;;){
    menu();
    char c=0; sz r=cdc_read(0,&c,1);
    if(r<=0) return;                 /* EOF (pipe) encerra */
    if(c=='\n') continue;
    char sink; while(cdc_read(0,&sink,1)==1 && sink!='\n'){}  /* drena linha */
    if(c=='0') return;
    if(c=='1'){ bench(); continue; }
    if(c=='2'){ fetch_demo(); continue; }
    if(c=='3'){ caps(); continue; }
    if(c=='4'){ conceitos(); continue; }
    put("  opcao invalida\n");
  }
}

static v0 help(v0){
  put("uso: discador [flags]\n");
  put("  --logo       so o logotipo\n");
  put("  --bench      executa benchmark e sai\n");
  put("  --caps       imprime contadores e sai\n");
  put("  --conceitos  imprime o conjunto de conceitos\n");
  put("  --fetch      demo de GET por IP literal\n");
  put("  --no-color   desliga ANSI\n");
  put("  --menu       menu interativo (default)\n");
  put("  -h|--help    esta ajuda\n");
}

/* recebe o topo da pilha cru: [argc][argv0][argv1]... */
v0 cdc_main(sz* sp){
  sz argc = sp[0];
  char** argv = (char**)(sp+1);

  /* 1a passada: flags globais */
  for(sz i=1;i<argc;i++) if(eq(argv[i],"--no-color")) g_color=0;

  /* default sem args: logo + menu */
  if(argc<2){ logo(); loop(); cdc_exit(0); }

  for(sz i=1;i<argc;i++){
    const char* a=argv[i];
    if(eq(a,"--no-color")) continue;
    if(eq(a,"-h")||eq(a,"--help")){ help(); cdc_exit(0); }
    if(eq(a,"--logo")){ logo(); cdc_exit(0); }
    if(eq(a,"--bench")){ bench(); cdc_exit(0); }
    if(eq(a,"--caps")){ caps(); cdc_exit(0); }
    if(eq(a,"--conceitos")){ conceitos(); cdc_exit(0); }
    if(eq(a,"--fetch")){ fetch_demo(); cdc_exit(0); }
    if(eq(a,"--menu")){ logo(); loop(); cdc_exit(0); }
    put("flag desconhecida: "); put(a); put("\n"); help(); cdc_exit(2);
  }
  cdc_exit(0);
}

/* ---- _start cru, sem crt: passa SP para cdc_main ---- */
#if defined(__aarch64__)
__attribute__((naked,used)) v0 _start(v0){
  __asm__ volatile("mov x0, sp\n bl cdc_main\n");
}
#elif defined(__arm__)
__attribute__((naked,used)) v0 _start(v0){
  __asm__ volatile("mov r0, sp\n bl cdc_main\n");
}
#elif defined(__x86_64__)
__attribute__((naked,used)) v0 _start(v0){
  __asm__ volatile("mov %rsp, %rdi\n call cdc_main\n");
}
#endif
