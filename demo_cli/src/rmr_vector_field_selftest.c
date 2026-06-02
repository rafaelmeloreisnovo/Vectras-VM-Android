#include "rmr_vector_field.h"
#include <stdio.h>

int main(void) {
  RmR_VectorFieldState s;
  RmR_VectorField_Init(&s);

  const u32 flags = RmR_VectorField_RunIndex(&s, 0u, 7u);
  if ((flags & RMR_VECTOR_FLAG_FAILSAFE) != 0u) {
    printf("FAIL vector field failsafe flags=%08x\n", flags);
    return 1;
  }
  if (s.n_raw != 56u || s.n_mod42 != 14u || s.arc_deg != 56u) {
    printf("FAIL vector field base map n=%u mod42=%u arc=%u\n", s.n_raw, s.n_mod42, s.arc_deg);
    return 1;
  }
  if (s.chord_q16 != 20388u || s.h_q16 != 17656u || s.toroid_node != 983u) {
    printf("FAIL vector field geometry chord=%u h=%u node=%u\n", s.chord_q16, s.h_q16, s.toroid_node);
    return 1;
  }
  if (s.gap_q16 != 23943u || s.spiral_q16 != 23943u || s.phi_q8 != 207u) {
    printf("FAIL vector field contraction gap=%u spiral=%u phi=%u\n", s.gap_q16, s.spiral_q16, s.phi_q8);
    return 1;
  }

  RmR_VectorField_Init(&s);
  const u8 program[] = {
    RMR_VECTOR_OP_LOAD_NUM, 0u,
    RMR_VECTOR_OP_MOD42, 0u,
    RMR_VECTOR_OP_ARC360, 0u,
    RMR_VECTOR_OP_CHORD_Q16, 0u,
    RMR_VECTOR_OP_H_EQ_Q16, 0u,
    RMR_VECTOR_OP_TOROID_NODE, 0u,
    RMR_VECTOR_OP_CORRECT, 7u,
    RMR_VECTOR_OP_AUDIT, 0u,
    RMR_VECTOR_OP_SEAL, 0u
  };
  (void)RmR_VectorField_RunBytecode(&s, program, (u32)sizeof(program));
  if (s.n_raw != 56u || s.n_mod42 != 14u || s.toroid_node != 983u || s.phi_q8 != 205u) {
    printf("FAIL vector bytecode n=%u mod42=%u node=%u phi=%u\n", s.n_raw, s.n_mod42, s.toroid_node, s.phi_q8);
    return 1;
  }

  RmR_VectorField_Init(&s);
  (void)RmR_VectorField_RunIndex(&s, 0x16u, 99u);
  if ((s.flags & RMR_VECTOR_FLAG_VOID22) == 0u || (s.flags & RMR_VECTOR_FLAG_WATCHDOG) == 0u) {
    printf("FAIL vector safeguards flags=%08x\n", s.flags);
    return 1;
  }

  const u32 sig = RmR_VectorField_SmokeSignature();
  if (sig != 0xe30aefc6u) {
    printf("FAIL vector signature=%08x\n", sig);
    return 1;
  }

  printf("OK vector field selftest signature=%08x\n", sig);
  return 0;
}
