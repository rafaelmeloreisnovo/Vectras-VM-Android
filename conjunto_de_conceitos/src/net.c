/* SPDX-License-Identifier: GPL-2.0-only
 * conjunto_de_conceitos / net.c
 * Autoria: Rafael Melo Reis. Clean-room.
 * Rede freestanding: TCP via syscalls socket/connect, HTTP/1.0 GET cru.
 * DNS A minimo sobre UDP. Sem libc, sem heap (buffers estaticos .bss).
 *
 * FRONTEIRA HONESTA:
 *  - http:// puro funciona por syscalls diretas.
 *  - https:// (TLS) NAO e suportado: exige modulo de criptografia.
 *    Marcado como LACUNA; ver CONCEITOS.md (modulo TLS futuro, autoral).
 *  - Codegen verificado por cross-compile+disasm; runtime em alvo ARM
 *    real e NAO VERIFICADO neste ambiente (sem qemu/sem dispositivo).
 */
#include "../include/abi.h"

/* socket(2): AF_INET=2; SOCK_STREAM=1; SOCK_DGRAM=2 */
#if defined(__aarch64__)
  #define SC_socket_n 0xc6
  #define SC_connect_n 0xcb
#else
  #define SC_socket_n 0x119
  #define SC_connect_n 0x11b
#endif

extern v0* memcpy(v0*, const v0*, unsigned long);
extern sz  cdc_strlen(const char*);

static u16 be16(u16 v){ return (u16)((v>>8)|(v<<8)); }
static u32 ip4(u8 a,u8 b,u8 c,u8 d){ /* ordem de rede (big-endian) em u32 LE */
  return (u32)a | ((u32)b<<8) | ((u32)c<<16) | ((u32)d<<24);
}

/* sockaddr_in: 16 bytes exatos */
struct sa4 { u16 fam; u16 port; u32 addr; u8 pad[8]; };

static sz tcp_open(u32 addr_be, u16 port){
  sz fd = cdc_sc(SC_socket_n, 2/*AF_INET*/, 1/*STREAM*/, 0, 0, 0);
  if(fd<0) return fd;
  struct sa4 s; s.fam=2; s.port=be16(port); s.addr=addr_be;
  for(int i=0;i<8;i++) s.pad[i]=0;
  sz r = cdc_sc(SC_connect_n, fd, (sz)&s, sizeof(s), 0, 0);
  if(r<0){ cdc_close(fd); return r; }
  return fd;
}

/* monta "GET <path> HTTP/1.0\r\nHost: <host>\r\n\r\n" em dst, retorna len */
static sz http_req(char* dst, const char* path, const char* host){
  static const char g[]="GET "; static const char h1[]=" HTTP/1.0\r\nHost: ";
  static const char h2[]="\r\nConnection: close\r\n\r\n";
  sz n=0; const char* p;
  for(p=g ;*p;p++) dst[n++]=*p;
  for(p=path;*p;p++) dst[n++]=*p;
  for(p=h1;*p;p++) dst[n++]=*p;
  for(p=host;*p;p++) dst[n++]=*p;
  for(p=h2;*p;p++) dst[n++]=*p;
  return n;
}

/* GET por IP literal. Escreve corpo+cabecalho cru em out (cap). Retorna bytes. */
sz cdc_http_get_ip(u8 a,u8 b,u8 c,u8 d, u16 port,
                   const char* host, const char* path,
                   char* out, sz cap){
  static char req[1024];
  sz fd = tcp_open(ip4(a,b,c,d), port);
  if(fd<0) return fd;
  sz rl = http_req(req, path, host);
  sz w  = cdc_write(fd, req, rl);
  if(w<0){ cdc_close(fd); return w; }
  sz total=0;
  for(;;){
    sz k = cdc_read(fd, out+total, cap-total);
    if(k<=0) break;
    total+=k;
    if(total>=cap) break;
  }
  cdc_close(fd);
  return total;
}

/* ---- DNS A minimo sobre UDP (runtime NAO VERIFICADO) ----
 * Monta consulta para <host>, envia ao resolver, le 1 registro A.
 * Resolver padrao = 1.1.1.1:53. Retorna ip_be (u32) ou <0 em erro.
 */
sz cdc_dns_a(const char* host, u8 ra,u8 rb,u8 rc,u8 rd){
  static u8 q[512]; static u8 r[512];
  /* cabecalho DNS: id, flags(RD=0x0100), qd=1 */
  sz n=0;
  q[n++]=0x13; q[n++]=0x37;          /* id */
  q[n++]=0x01; q[n++]=0x00;          /* RD */
  q[n++]=0x00; q[n++]=0x01;          /* QDCOUNT=1 */
  q[n++]=0; q[n++]=0; q[n++]=0; q[n++]=0; q[n++]=0; q[n++]=0;
  /* QNAME: rotulos prefixados por tamanho */
  sz li=n++; u8 lc=0;
  for(const char* p=host;;p++){
    if(*p=='.'||*p==0){ q[li]=lc; lc=0; li=n; if(*p) n++; if(*p==0) break; }
    else { q[n++]=(u8)*p; lc++; }
  }
  q[n++]=0;                           /* fim do nome */
  q[n++]=0x00; q[n++]=0x01;           /* QTYPE=A */
  q[n++]=0x00; q[n++]=0x01;           /* QCLASS=IN */

  sz fd = cdc_sc(SC_socket_n, 2, 2/*DGRAM*/, 0, 0, 0);
  if(fd<0) return fd;
  struct sa4 s; s.fam=2; s.port=be16(53); s.addr=ip4(ra,rb,rc,rd);
  for(int i=0;i<8;i++) s.pad[i]=0;
  if(cdc_sc(SC_connect_n, fd,(sz)&s,sizeof(s),0,0)<0){ cdc_close(fd); return -1; }
  if(cdc_write(fd,q,n)<0){ cdc_close(fd); return -1; }
  sz k = cdc_read(fd,r,sizeof(r));
  cdc_close(fd);
  if(k< (sz)(n+16)) return -1;
  /* salta cabecalho+pergunta; procura primeiro A (type=0x0001, rdlen=4) */
  sz i=n;
  while(i+10< k){
    if(r[i]==0x00 && r[i+1]==0x01 && r[i+2]==0x00 && r[i+3]==0x01 && r[i+9]==4){
      return (sz)ip4(r[i+10],r[i+11],r[i+12],r[i+13]);
    }
    i++;
  }
  return -1;
}
