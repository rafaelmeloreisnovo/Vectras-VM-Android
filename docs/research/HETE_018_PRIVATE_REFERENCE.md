# HETE-0.18 — referência privada para Vectras VM Android

**Paper canônico privado:** `rafaelmeloreisnovo/papers/docs/rmrcti/HETE_018_TOROIDAL_STABILITY_ENRICHMENT.md`  
**Commit:** `c444988dca0b36251a51dfe349256f75d6099b31`

## Papel adequado do Vectras

O Vectras não é a origem da hipótese nem deve incorporar `0,18` como constante. Seu papel futuro é prover ambientes reproduzíveis para executar o laboratório RMR-CTI em imagens/VMs controladas.

## Uso futuro permitido

- registrar imagem, arquitetura, kernel e toolchain;
- executar os mesmos traces em ambientes isolados;
- comparar ARM32, ARM64 e hosts distintos;
- preservar stdout, exit status, hashes e tempo;
- testar se o resultado depende do ambiente;
- empacotar artefatos privados de reprodução.

## Não implementar nesta fase

```text
não alterar scheduler
não alterar seleção de VM
não usar 0,18 como health score
não usar 0,18 como limiar de segurança
não declarar atrator
```

## Manifesto mínimo futuro

```text
vm_image_sha256
host_arch
 guest_arch
kernel_version
toolchain_version
rmrcti_commit
paper_version
trace_sha256
seed
params
output_sha256
claim_state
```

## Invariante

```text
Vectras reproduz o ambiente;
não decide a verdade da hipótese.
```

Privacidade: `private`  
Publicação: `blocked_without_author_approval`  
Assinatura: `RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ`
