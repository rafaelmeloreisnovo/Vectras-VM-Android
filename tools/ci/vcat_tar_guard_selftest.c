/*
 * Authorial VCAT TAR planner adversarial tests (no stdlib/JNI/syscalls).
 * Hosted harness is intentionally separate from the freestanding core.
 * Exit status is the index of the first failing falsifier.
 */
#include "../../app/src/main/cpp/catalytic/vcat_bootstrap.inc"

static vcat_u8 header[VCAT_TAR_BLOCK_BYTES];
static vcat_tar_entry entry;

static void seed(void) {
    vcat_u32 i;
    for (i = 0u; i < VCAT_TAR_BLOCK_BYTES; ++i) header[i] = 0u;
    header[0] = (vcat_u8)'f';
    header[100] = (vcat_u8)'0';
    header[101] = (vcat_u8)'0';
    header[102] = (vcat_u8)'0';
    header[103] = (vcat_u8)'0';
    header[104] = (vcat_u8)'7';
    header[105] = (vcat_u8)'5';
    header[106] = (vcat_u8)'5';
    header[124] = (vcat_u8)'0';
    header[125] = (vcat_u8)'0';
    header[126] = (vcat_u8)'0';
    header[127] = (vcat_u8)'0';
    header[128] = (vcat_u8)'0';
    header[129] = (vcat_u8)'0';
    header[130] = (vcat_u8)'0';
    header[131] = (vcat_u8)'0';
    header[132] = (vcat_u8)'0';
    header[133] = (vcat_u8)'1';
    header[156] = (vcat_u8)'0';
    header[257] = (vcat_u8)'u';
    header[258] = (vcat_u8)'s';
    header[259] = (vcat_u8)'t';
    header[260] = (vcat_u8)'a';
    header[261] = (vcat_u8)'r';
}

static int has(vcat_u32 required) {
    vcat_tar_inspect(header, VCAT_TAR_BLOCK_BYTES, &entry);
    return (entry.flags & required) == required;
}
static int lacks(vcat_u32 prohibited) {
    vcat_tar_inspect(header, VCAT_TAR_BLOCK_BYTES, &entry);
    return (entry.flags & prohibited) == 0u;
}

int main(void) {
    seed();
    if (!has(VCAT_TAR_F_READY)) return 1;
    seed(); header[0]='.'; header[1]='/'; header[2]='f';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 2;
    seed(); header[0]='a'; header[1]='/'; header[2]='.'; header[3]='.'; header[4]='/'; header[5]='b';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 3;
    seed(); header[0]='a'; header[1]='/'; header[2]='/'; header[3]='b';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 4;
    seed(); header[0]='a'; header[1]='/'; header[2]='.'; header[3]='/'; header[4]='b';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 5;
    seed(); header[0]='a'; header[1]='\\'; header[2]='b';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 6;
    seed(); header[0]='a'; header[1]='\n'; header[2]='b';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 7;
    seed(); header[0]='a'; header[1]=0u; header[2]='b';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 8;
    seed(); header[0]='a'; header[1]='/'; /* Valid TAR directory terminator */
    header[156] = (vcat_u8)'5';
    if (!has(VCAT_TAR_F_READY)) return 9;
    seed(); header[124]='0'; header[125]=0u; header[126]='7';
    if (!lacks(VCAT_TAR_F_SIZE_OK | VCAT_TAR_F_READY)) return 10;
    seed(); header[100]='0'; header[101]=0u; header[102]='7';
    if (!lacks(VCAT_TAR_F_MODE_OK | VCAT_TAR_F_READY)) return 11;
    seed(); header[156]='2'; header[157]='.'; header[158]='.'; header[159]='/';
    if (!lacks(VCAT_TAR_F_SAFE_LINK | VCAT_TAR_F_READY)) return 12;
    seed(); header[156]='2'; header[157]='s'; header[158]='a'; header[159]='f'; header[160]='e';
    if (!has(VCAT_TAR_F_READY)) return 13;
    seed(); header[0]='/'; header[1]='x';
    if (!lacks(VCAT_TAR_F_SAFE_PATH | VCAT_TAR_F_READY)) return 14;
    return 0;
}
