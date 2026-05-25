# LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE

Guia prático para evolução do código-fonte em direção a execução low-level com:
- branchless quando coerente,
- sem heap no hot path,
- sem GC,
- sem atrito de abstrações desnecessárias.

## Regras de execução
1. Hot path: proibido `malloc/calloc/realloc/free`.
2. Preferir buffers estáticos/stack com limites explícitos.
3. Preferir aritmética inteira fixa (Q16.16 já adotado no projeto).
4. Em assembly/AArch64: priorizar `csel/csinc` para reduzir branch imprevisível.
5. Alinhar dados para SIMD (NEON) e manter acesso cache-friendly (L1/L2).

## Checklist de revisão
- [ ] Não há alocação dinâmica no caminho crítico.
- [ ] Não há dependência de GC.
- [ ] Estruturas prontas para vetorização NEON/SIMD.
- [ ] Loops com terminação provada.
- [ ] APIs de syscalls/interop explícitas e auditáveis.

## Comando de verificação estática
```bash
python3 tools/compliance/check_lowlevel_constraints.py
```

## Observação
Este gate é sintático e conservador. Ele não substitui profiling, revisão de assembly, nem validação funcional.
