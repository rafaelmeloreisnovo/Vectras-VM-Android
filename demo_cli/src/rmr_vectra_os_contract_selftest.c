/* rmr_vectra_os_contract_selftest - prova executável do contrato VECTRA_OS.
 *
 * Ref: docs/VECTRA_OS_LIVING_SYSTEM_GAP_LEDGER.md §3 (tabela-verdade VOS_CSEL
 * exigida), §4 G2 e §8 (next patch target). Cobre os itens do contrato que
 * existem hoje; X-macro/CAS/trampoline são gaps G3-G6 e ficam fora por design.
 *
 * Invariantes cobertas:
 *  1. Tabela-verdade VOS_CSEL — os 4 casos exigidos pelo ledger.
 *  2. vos_init() e vos_selftest() retornam 1 (FRAF converge, CRC íntegro).
 *  3. Arena mark/restore: após restore, o mesmo endereço é devolvido.
 *  4. Dispatch hotswap: troca de implementação ativa e retorno ao original
 *     reproduzem os mesmos resultados (rollback sem resíduo).
 *  5. vos_caps_report expõe as constantes FRAF do contrato.
 */
#include "rmr_vectra_os.h"

#include <stdio.h>

static u32 vos_stub_crc(const u8 *buf, u32 len, u32 init) {
  (void)buf;
  (void)len;
  (void)init;
  return 0x52414621u; /* "RAF!" — marcador do caminho trocado */
}

int main(void) {
  static const u8 vec[4] = {0x01u, 0x02u, 0x03u, 0x04u};
  u32 caps_out[4];

  /* 1. tabela-verdade VOS_CSEL (ledger §3) */
  if (VOS_CSEL(0, 10u, 20u) != 20u) {
    printf("FAIL csel(0,10,20)\n");
    return 1;
  }
  if (VOS_CSEL(1, 10u, 20u) != 10u) {
    printf("FAIL csel(1,10,20)\n");
    return 1;
  }
  if (VOS_CSEL(0, 0xAAAAAAAAu, 0x55555555u) != 0x55555555u) {
    printf("FAIL csel(0,AA,55)\n");
    return 1;
  }
  if (VOS_CSEL(1, 0xAAAAAAAAu, 0x55555555u) != 0xAAAAAAAAu) {
    printf("FAIL csel(1,AA,55)\n");
    return 1;
  }

  /* 2. init + selftest publicos */
  if (vos_init() != 1u) {
    printf("FAIL vos_init\n");
    return 1;
  }
  if (vos_selftest() != 1u) {
    printf("FAIL vos_selftest\n");
    return 1;
  }

  /* 3. arena mark/restore deterministico */
  {
    void *p1;
    void *p2;
    VOS_MARK();
    p1 = VOS_ARENA_ALLOC(48u);
    if (!p1) {
      printf("FAIL arena alloc #1\n");
      return 1;
    }
    VOS_RESTORE();
    p2 = VOS_ARENA_ALLOC(48u);
    if (p1 != p2) {
      printf("FAIL arena restore: enderecos divergem\n");
      return 1;
    }
    VOS_RESTORE();
  }

  /* 4. dispatch hotswap com rollback sem residuo */
  {
    vos_crc_fn_t original = vos_g_crc;
    u32 crc_before = VOS_CRC32C(vec, 4u, VOS_CHAIN_INIT);

    VOS_HOTSWAP_CRC(vos_stub_crc);
    if (VOS_CRC32C(vec, 4u, VOS_CHAIN_INIT) != 0x52414621u) {
      printf("FAIL hotswap nao ativou stub\n");
      return 1;
    }

    VOS_HOTSWAP_CRC(original);
    if (VOS_CRC32C(vec, 4u, VOS_CHAIN_INIT) != crc_before) {
      printf("FAIL hotswap rollback divergente\n");
      return 1;
    }
    if (crc_before == 0u || crc_before == 0xFFFFFFFFu) {
      printf("FAIL crc original fora do contrato de cadeia\n");
      return 1;
    }
  }

  /* 5. caps report expoe as constantes FRAF do contrato */
  vos_caps_report(caps_out);
  if (caps_out[1] != (u32)VOS_FRAF_STAR_Q16 ||
      caps_out[2] != (u32)VOS_FRAF_SCALE_Q16 ||
      caps_out[3] != (u32)VOS_FRAF_OFFSET_Q16) {
    printf("FAIL caps report constantes FRAF\n");
    return 1;
  }

  printf("OK vectra os contract selftest caps=0x%08x fstar=0x%08x\n",
         (unsigned)caps_out[0], (unsigned)caps_out[1]);
  return 0;
}
