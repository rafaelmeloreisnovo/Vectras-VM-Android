#!/usr/bin/env bash
# verify_vectra_os_contract.sh — G1 do VECTRA_OS_LIVING_SYSTEM_GAP_LEDGER.
#
# Prova executável do contrato pré-compilador/linker de redução de símbolos:
#   1. compila um probe .so com a metodologia (-ffunction-sections,
#      -fdata-sections, -fvisibility=hidden, --gc-sections, --exclude-libs);
#   2. captura os warnings -Wunused-* como SINAL de eliminação (informação,
#      não falha — suprimir o warning mataria o pipeline de gc-sections);
#   3. audita o binário: .dynsym exporta SOMENTE a API pública de 3 símbolos;
#   4. confirma o efeito do --gc-sections (vos_tick_sw ausente do binário);
#   5. varre símbolos proibidos no hot path (malloc/free/printf/...);
#   6. registra evidência em reports/vectra_os_contract_report.md.
#
# Uso: tools/verify_vectra_os_contract.sh [CC]
set -u

CC="${1:-${CC:-cc}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${ROOT}/build/vectra_os_contract"
REPORT="${ROOT}/reports/vectra_os_contract_report.md"
SRC_C="${ROOT}/engine/rmr/src/rmr_vectra_os.c"
INC="${ROOT}/engine/rmr/include"

mkdir -p "${OUT_DIR}" "${ROOT}/reports"

ARCH="$(uname -m 2>/dev/null || echo unknown)"
case "${ARCH}" in
  x86_64)        SRC_S="${ROOT}/engine/rmr/interop/rmr_vectra_os_x86_64.S" ;;
  aarch64|arm64) SRC_S="${ROOT}/engine/rmr/interop/rmr_vectra_os_arm64.S" ;;
  armv7*|armv8l) SRC_S="${ROOT}/engine/rmr/interop/rmr_vectra_os_armv7.S" ;;
  riscv64)       SRC_S="${ROOT}/engine/rmr/interop/rmr_vectra_os_riscv64.S" ;;
  *) echo "[vectra-os-contract] SKIP: arch ${ARCH} sem interop .S"; exit 0 ;;
esac

PROBE="${OUT_DIR}/libvectra_os_probe.so"
WARN_LOG="${OUT_DIR}/compile_warnings.log"

CFLAGS_METH="-O3 -std=c11 -Wall -Wextra -DRMR_JNI_BUILD=1 -fPIC \
  -ffunction-sections -fdata-sections -fvisibility=hidden"
LDFLAGS_METH="-shared -Wl,--gc-sections -Wl,--exclude-libs,ALL"

# 1-2. compile capturando warnings (stderr) como sinal de eliminação
if ! ${CC} ${CFLAGS_METH} -I"${INC}" "${SRC_C}" "${SRC_S}" ${LDFLAGS_METH} \
     -o "${PROBE}" 2>"${WARN_LOG}"; then
  echo "[vectra-os-contract] FAIL: probe nao compilou"
  cat "${WARN_LOG}"
  exit 1
fi

UNUSED_WARNINGS="$(grep -c 'Wunused' "${WARN_LOG}" 2>/dev/null || true)"

fail=0

# 3. .dynsym deve exportar somente a API pública de 3 símbolos
EXPORTED="$(nm -D --defined-only "${PROBE}" 2>/dev/null \
  | awk '{print $3}' | grep -v '^$' | sort)"
EXPECTED="vos_caps_report
vos_init
vos_selftest"
if [ "${EXPORTED}" != "${EXPECTED}" ]; then
  echo "[vectra-os-contract] FAIL: exportados divergem da API publica:"
  echo "${EXPORTED}"
  fail=1
fi

# 4. efeito gc-sections: vos_tick_sw (sinalizado por -Wunused-function)
#    nao pode sobreviver no binario final
if nm "${PROBE}" 2>/dev/null | grep -q 'vos_tick_sw'; then
  echo "[vectra-os-contract] FAIL: vos_tick_sw sobreviveu ao gc-sections"
  fail=1
fi

# 5. simbolos proibidos no hot path (undefined = dependencia real)
FORBIDDEN="$(nm -D --undefined-only "${PROBE}" 2>/dev/null \
  | grep -wE 'malloc|calloc|realloc|free|mmap|brk|printf|clock_gettime' || true)"
if [ -n "${FORBIDDEN}" ]; then
  echo "[vectra-os-contract] FAIL: simbolos proibidos no probe:"
  echo "${FORBIDDEN}"
  fail=1
fi

# 6. evidencia
COMMIT="$(git -C "${ROOT}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
{
  echo "# VECTRA_OS contract report"
  echo
  echo "- Data: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- Commit: ${COMMIT}"
  echo "- Arch: ${ARCH}"
  echo "- CC: ${CC}"
  echo "- Flags: \`${CFLAGS_METH} ${LDFLAGS_METH}\`"
  echo
  echo "## Sinal de eliminação (warnings -Wunused capturados)"
  echo
  echo "- Total: ${UNUSED_WARNINGS:-0} (warning = seção morta sinalizada ao gc-sections; não suprimir)"
  echo
  echo '```'
  grep 'Wunused' "${WARN_LOG}" 2>/dev/null || echo "(nenhum nesta arch)"
  echo '```'
  echo
  echo "## Símbolos exportados (.dynsym)"
  echo
  echo '```'
  echo "${EXPORTED}"
  echo '```'
  echo
  echo "## Resultado"
  echo
  if [ "${fail}" -eq 0 ]; then
    echo "- CONTRATO VÁLIDO: API pública = 3 símbolos; gc-sections efetivo; hot path sem símbolos proibidos."
  else
    echo "- CONTRATO FALSIFICADO: ver falhas acima."
  fi
} > "${REPORT}"

if [ "${fail}" -ne 0 ]; then
  echo "[vectra-os-contract] FAIL (report: ${REPORT})"
  exit 1
fi
echo "[vectra-os-contract] OK exported=3 unused_warnings=${UNUSED_WARNINGS:-0} (report: ${REPORT})"
exit 0
