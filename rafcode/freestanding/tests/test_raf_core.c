/* SPDX-License-Identifier: MIT — HOSTED TEST ONLY; never linked into core. */
#include <stdio.h>
#include <string.h>
#include "raf_core.h"

static int total, failed;
#define EXPECT(label, condition) do { ++total; if (!(condition)) { \
    fprintf(stderr,"FAIL %s:%d %s\n",__FILE__,__LINE__,label); ++failed; \
} } while (0)
#define B(x) ((const raf_u8 *)(x))

int main(void) {
    raf_u8 out[512];
    raf_u32 n = 0u;
    raf_status st;
    void *p = 0;
    raf_u8 arena_mem[128];
    raf_arena arena = { arena_mem, (raf_u32)sizeof(arena_mem), 0u };
    raf_bind bindings[3] = {
       { B("/"), 1u, B("/host"), 5u },
       { B("/usr"), 4u, B("/private/usr"), 12u },
       { B("/us"), 3u, B("/trick"), 6u }
    };
    raf_result res;
    raf_port unconnected = { 0, 0 };
    raf_u32 produced = 777u;
    raf_cmd cmd = { RAF_CMD_CRC32C, "123456789", 9u, 0, 0u, 0, 0u };
    EXPECT("crc32c reference", raf_crc32c("123456789", 9u)==0xe3069283u);
    EXPECT("crc32c empty", raf_crc32c("", 0u)==0u);
    raf_dispatch(&cmd, &res);
    EXPECT("dispatch checksum", res.status==RAF_OK && res.checksum==0xe3069283u && res.bytes_written==0u);
    cmd.kind = 999u;
    raf_dispatch(&cmd, &res);
    EXPECT("dispatch unknown", res.status==RAF_UNSUPPORTED);
    raf_dispatch(0, &res);
    EXPECT("dispatch null", res.status==RAF_INVALID);
    st=raf_path_normalize(B("/usr//local/./bin/../lib/"), 25u,out,sizeof(out),&n);
    EXPECT("normalize dot+dotdot", st==RAF_OK && n==14u && !memcmp(out,"/usr/local/lib",n+1u));
    st=raf_path_normalize(B("/a/b/../../c"), 12u,out,sizeof(out),&n);
    EXPECT("normalize collapse", st==RAF_OK && n==2u && !memcmp(out,"/c",3u));
    st=raf_path_normalize(B("/../../etc"), 10u,out,sizeof(out),&n);
    EXPECT("normalize above root", st==RAF_ESCAPE_DENIED);
    st=raf_path_normalize(B("relative"), 8u,out,sizeof(out),&n);
    EXPECT("reject relative", st==RAF_INVALID);
    st=raf_path_normalize(B("/"), 1u,out,sizeof(out),&n);
    EXPECT("root", st==RAF_OK && n==1u && out[0]=='/' && out[1]==0);
    st=raf_path_normalize(B("/abcdef"), 7u,out,3u,&n);
    EXPECT("short out", st==RAF_NO_SPACE);
    st=raf_path_normalize(B("/a\0b"), 4u,out,sizeof(out),&n);
    EXPECT("embedded NUL", st==RAF_INVALID);
    st=raf_path_translate(bindings,3u,B("/usr/lib"),8u,out,sizeof(out),&n);
    EXPECT("longest prefix", st==RAF_OK && n==16u && !memcmp(out,"/private/usr/lib",n+1u));
    st=raf_path_translate(bindings,3u,B("/usb"),4u,out,sizeof(out),&n);
    EXPECT("segment boundary", st==RAF_OK && n==9u && !memcmp(out,"/host/usb",n+1u));
    st=raf_path_translate(bindings,3u,B("/us"),3u,out,sizeof(out),&n);
    EXPECT("exact rule", st==RAF_OK && n==6u && !memcmp(out,"/trick",n+1u));
    st=raf_path_translate(bindings,3u,B("/"),1u,out,sizeof(out),&n);
    EXPECT("root translate", st==RAF_OK && n==5u && !memcmp(out,"/host",n+1u));
    out[0]='!'; st=raf_path_translate(bindings,3u,B("/usr/lib"),8u,out,6u,&n);
    EXPECT("translate does not overwrite on error", st==RAF_NO_SPACE && out[0]=='!');
    st=raf_path_translate(bindings,0u,B("/usr/lib"),8u,out,sizeof(out),&n);
    EXPECT("missing rule typed", st==RAF_NO_MAPPING);
    st=raf_path_translate(bindings,3u,B("/usr/./lib"),10u,out,sizeof(out),&n);
    EXPECT("translate requires canonical", st==RAF_INVALID);
    bindings[2].host_n=0u;
    st=raf_path_translate(bindings,3u,B("/usr/lib"),8u,out,sizeof(out),&n);
    EXPECT("deny invalid rule", st==RAF_INVALID);
    bindings[2].host_n=6u;
    st=raf_arena_take(&arena,8u,8u,&p);
    EXPECT("arena aligned", st==RAF_OK && p && ((raf_uptr)p&7u)==0u);
    st=raf_arena_take(&arena,256u,1u,&p);
    EXPECT("arena fails without cursor mutation", st==RAF_NO_SPACE && arena.cursor<=128u);
    st=raf_arena_take(&arena,8u,3u,&p);
    EXPECT("arena invalid alignment", st==RAF_INVALID);
    raf_zero(arena_mem, sizeof(arena_mem));
    EXPECT("zero explicit", arena_mem[5]==0u && arena_mem[127]==0u);
    cmd.kind=RAF_CMD_NORMALIZE;cmd.input="/a/./b";cmd.input_n=6u;cmd.output=out;cmd.output_capacity=sizeof(out);
    raf_dispatch(&cmd,&res);
    EXPECT("dispatch normalize",res.status==RAF_OK && res.bytes_written==4u && !memcmp(out,"/a/b",5u));
    cmd.kind=RAF_CMD_TRANSLATE;cmd.input="/usr/bin";cmd.input_n=8u;cmd.rules=bindings;cmd.rule_count=3u;
    raf_dispatch(&cmd,&res);
    EXPECT("dispatch translate",res.status==RAF_OK && !memcmp(out,"/private/usr/bin",17u));
    bindings[1].guest_n=0u;
    raf_dispatch(&cmd,&res);
    EXPECT("invalid bind fail closed",res.status==RAF_INVALID && res.bytes_written==0u);
    EXPECT("port no provider typed", raf_port_invoke(&unconnected,1u,"a",1u,out,sizeof(out),&produced)==RAF_TOKEN_VAZIO && produced==0u);
    EXPECT("port null provider typed", raf_port_invoke(0,1u,"a",1u,out,sizeof(out),&produced)==RAF_TOKEN_VAZIO);
    EXPECT("port missing produced invalid", raf_port_invoke(0,1u,"a",1u,out,sizeof(out),0)==RAF_INVALID);
    EXPECT("translate exact content", raf_path_translate(bindings,3u,B("/us"),3u,out,sizeof(out),&n)==RAF_INVALID); /* invalid rule remains */
    printf("RAFCODE_HOST_TESTS=%d FAILURES=%d\n",total,failed);
    return failed?1:0;
}
