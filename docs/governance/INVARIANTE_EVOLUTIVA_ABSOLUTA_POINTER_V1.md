# Vectras VM Android — ponte para a Invariante Evolutiva Absoluta V1

**Autoridade canônica:** `rafaelmeloreisnovo/Mapa`  
**Documento:** `governanca/invariantes/INVARIANTE_EVOLUTIVA_ABSOLUTA_V1.md`  
**Estado local:** `RESPONSIBILITY_POINTER`  
**Claim:** `claim_allowed=false`

## Responsabilidade deste repositório

Dentro da IEA, o Vectras preserva a passagem entre estrutura lógica e layout físico:

```text
arquivo real
→ spans e offsets
→ mapeamento/cópia/materialização/rejeição
→ memória e páginas
→ guest/runtime
→ receipt e failure mode
```

A evolução é válida quando mudanças no engine, QEMU, ZIPRAF, JNI, CI ou release preservam origem, revisão, contratos de entrada/saída, hashes, failure mode, rollback e fronteira de claim.

## Evidência delimitada do espelho auditado

```text
ZIP: Vectras-VM-Android-master (2)(7).zip
SHA-256: cf38364c301ccfc9b8fba9dbbff6af63b60a27e0bb222b1a9c19edf6b0b91372
arquivos: 2626
estado: MIRROR_HEAD_HIGH_CONFIDENCE_CORE
ZIPRAF real archive binding: 37/37 PASS
host mmap receipt: PASS
```

## Fronteiras preservadas

```text
host mmap layout ≠ Android mmap comprovado
mapeabilidade ≠ execução zero-copy
layout determinístico ≠ DMA
código QEMU presente ≠ guest boot validado
workflow existente ≠ CI remota PASS
```

## Próximo fechamento local

1. ligar receipts ZIPRAF a commits imutáveis;
2. executar probes Android ARM32/ARM64;
3. registrar guest boot e falhas reproduzíveis;
4. manter rollback e artefatos assinados separados de builds internos.

A forma pode evoluir; a cadeia entre bytes, layout, execução e prova não pode desaparecer.
