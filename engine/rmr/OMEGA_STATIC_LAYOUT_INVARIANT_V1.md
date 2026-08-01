# Ω Static Layout Invariant V1

Status: `IMPLEMENTED_REFERENCE / CLAIM_LIMITED`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`

## 1. Problema delimitado

O ecossistema já possui mecanismos que reduzem alocação dinâmica e relocação, mas eles estavam separados:

1. arenas estáticas e pools sem `malloc`;
2. executáveis ELF delimitados com zero relocações;
3. ZIPRAF `STORE` validado e mapeado diretamente;
4. janelas lógicas BUFFER/L1/L2;
5. máscaras que preservam bits fixos;
6. mapas de alocação e fault overlay em outro plano canônico.

A lacuna era um contrato comum para responder:

```text
o que nunca muda?
o que muda somente quando a base muda?
o que pode ser remapeado?
o que está pinado apenas durante uma sessão?
quando uma tabela de endereços pode ser reutilizada?
```

A invariante adotada é:

```text
endereço lógico = base de execução + offset imutável
```

Formalmente:

```text
A(o,t) = B(t) + Δ(o)
```

- `Δ(o)` é a posição relativa imutável do objeto dentro do layout;
- `B(t)` é a base à qual o layout foi ligado naquela época de mapeamento;
- mudar `B` não exige recalcular todos os `Δ`;
- ponteiros absolutos só podem ser reutilizados quando base e época permanecem iguais.

## 2. Auditoria cruzada dos núcleos existentes

### PCR_Rafaelia_Code_seed

`BAREMETAL_ARCHITECTURE_ANALYSIS.md` já identifica custom allocators, direct syscalls e redução de dependências como objetivos. Parte relevante permanece roadmap: um objetivo documentado não equivale a uma implementação executada.

### termux-app-rafacodephi

- `bootstrap_rafaelia/raf_arena.h` implementa bump allocation simples sobre memória fornecida pelo chamador;
- `app/src/main/cpp/lowlevel/baremetal_nomalloc.h` declara arena alinhada, matrizes e operações sem libm no hot path;
- `docs/APKC_EXECUTABLE_ELF_CONTRACT.md` delimita emissores `ET_EXEC` fixos e registra explicitamente que linker geral, símbolos e relocações permanecem `TOKEN_VAZIO` fora do stub provado.

Lacuna observada: a arena mínima de `raf_arena.h` não registra alinhamento, geração, identidade de região ou política de mobilidade.

### Vectras-VM-Android / RMR

- `engine/rmr/include/rmr_vectra_os.h` possui arena BSS, bump cursor e rollback O(1);
- `engine/rmr/src/rmr_unified_kernel.c` usa pools estáticos e arena fallback sem heap no núcleo delimitado;
- `app/src/main/java/com/vectras/vm/vectra/ZiprafDirectRuntime.kt` valida ZIP clássico `STORE`, cruza diretório central e cabeçalho local, verifica CRC-32 e trabalha com offsets delimitados;
- `ZiprafDirectRuntimeTest.kt` já possui teste de preservação de bits fixos.

Lacunas observadas:

1. `preserveFixedBits` preserva uma máscara numérica, mas não pina páginas nem fixa endereço virtual;
2. o runtime Java depende de `FileChannel.map`, portanto a base virtual pode variar entre processos;
3. o construtor atual mantém um mapeamento completo e `window()` cria novos mapeamentos por janela;
4. a verificação CRC usa `ByteBuffer.allocate`, portanto não é `NoMalloc` na camada Java;
5. o limite de `Int.MAX_VALUE` ainda restringe o extent integral mapeado pelo construtor.

Essas lacunas não invalidam o ZIPRAF direto. Elas apenas impedem promover `FIXED_PHYSICAL_ADDRESS` ou `ZERO_ALLOCATION_RUNTIME` para verdade global.

### GAIA_phi

`native/rafaelia_omega_v32/README.md` registra um ELF32 ARM `ET_EXEC`, estático, sem `PT_INTERP`, sem `PT_DYNAMIC`, sem símbolos externos indefinidos e com zero relocações no artefato observado. O próprio documento delimita corretamente: loaderless userspace Linux/Android não é firmware bare-metal físico.

## 3. Três identidades que não podem ser confundidas

```text
offset estável      = Δ dentro do layout
endereço virtual    = base do processo + Δ
posição física      = tradução inferior do SO/controladora
```

Portanto:

```text
FIXED_OFFSET != FIXED_VIRTUAL != FIXED_PHYSICAL
```

No Android/Linux com ASLR, o caminho recomendado é:

```text
manifesto de offsets imutáveis
+ base ligada em runtime
+ referências relativas
```

No bare metal com linker script e mapa de memória provado, pode existir:

```text
base física declarada
+ offsets imutáveis
+ zero tabela de relocação em runtime
```

A segunda forma não deve ser inferida a partir da primeira.

## 4. Estados de mobilidade

| Estado | Semântica |
|---|---|
| `MOVABLE_BASE` | a base pode mudar; offsets permanecem iguais |
| `FIXED_OFFSET` | a posição relativa não pode mudar dentro do layout |
| `PINNED_RUNTIME` | a base não pode mudar enquanto o vínculo estiver ativo |
| `REMAP_ONLY` | mudança permitida apenas como nova época de mapeamento |
| `PHYSICAL_FIXED` | exige mapa físico explícito e prova de plataforma |
| `TOKEN_VAZIO` | política ainda não demonstrada |

Atributos de arquivo, como `HIDDEN`, `READ_ONLY` e `SYSTEM`, pertencem a outro plano. Eles não fixam clusters nem endereços físicos.

## 5. Contrato `SCH/sch` provisório

A sequência lembrada como `S c h s C H` não foi tratada como padrão externo. Ela foi preservada como heurística RAFAELIA, com domínio explícito:

### Plano estrutural — `SCH`

```text
S = STATIC_OFFSET
C = CONTIGUOUS_REGION
H = HANDLE_RELATIVE
```

### Plano de evidência — `sch`

```text
s = stable layout epoch
c = checked bounds/alignment
h = hashed manifest signature
```

Assim:

```text
SCH = como a memória é organizada
sch = como demonstramos que o contrato observado continua o mesmo
```

Até existir especificação independente anterior que dê outro significado às letras, este mapeamento permanece `RAFAELIA_PROVISIONAL`.

## 6. Implementação de referência

Arquivos:

```text
engine/rmr/include/rmr_static_layout.h
engine/rmr/src/rmr_static_layout.c
demo_cli/src/rmr_static_layout_selftest.c
tools/test_rmr_static_layout.sh
```

O contrato usa:

```text
manifesto imutável
  ├── layout_epoch
  ├── total_size
  ├── base_alignment
  └── regiões
      ├── region_id
      ├── offset
      ├── size
      ├── alignment
      ├── mobility
      ├── semantic_state
      └── fixed_offset_bits
```

Não há alocação dentro da biblioteca. A validação rejeita:

- regiões sobrepostas;
- IDs duplicados;
- desalinhamento;
- overflow de bounds;
- região `EMPTY/ABSENT` com tamanho não zero;
- região `PRESENT` com tamanho zero;
- bits fixos incompatíveis;
- tentativa de rebind em região pinada/física;
- regressão de época de mapeamento;
- leitura de região `FAULT`, `ABSENT` ou `TOKEN_VAZIO`.

## 7. Reutilização sem recalcular endereços internos

O ganho não vem de fingir que toda base é eterna. Ele vem de não armazenar um ponteiro absoluto para cada objeto.

Em vez de:

```text
object_0 = 0x7f10a040
object_1 = 0x7f10a880
object_2 = 0x7f10b200
```

armazenamos:

```text
base = B
object_0.offset = 0x0040
object_1.offset = 0x0880
object_2.offset = 0x1200
```

Quando a base muda de `B0` para `B1`:

```text
Δ0, Δ1, Δ2 permanecem
apenas o binding da base muda
```

Portanto:

```text
reuse(offset_table) = manifest_signature_antes == manifest_signature_depois
reuse(absolute_ptrs) = base_antes == base_depois AND mapping_epoch_antes == mapping_epoch_depois
```

Isso reduz trabalho de relocação por objeto, evita fragmentação interna causada por múltiplas pequenas alocações e facilita rollback por época.

## 8. Integração com ZIPRAF

Para uma entrada `STORE` validada:

```text
archive_base
+ local_header_offset
+ metadata_size
= payload_base
```

Dentro do payload:

```text
payload_address(object) = mapped_payload_base + object.relative_offset
```

O arquivo ZIP pode estar em outro local e a região virtual pode receber outra base. Mesmo assim, a floresta interna de offsets continua reutilizável se:

1. o diretório central e o cabeçalho local continuarem coerentes;
2. tamanho e CRC continuarem válidos;
3. o manifesto interno tiver a mesma assinatura;
4. a época de layout não tiver mudado.

O ZIPRAF fornece lastro de extent; o `rmr_static_layout` fornece lastro de offsets internos.

## 9. Fragmentação

A estratégia não promete “memória universalmente sem fragmentação”. Ela elimina classes específicas:

```text
sem free individual
+ arena/pool de tamanho conhecido
+ regiões ordenadas e alinhadas
+ reset/rollback por época
= sem fragmentação externa dentro daquela arena delimitada
```

Ainda podem existir:

- padding por alinhamento;
- fragmentação no filesystem;
- páginas virtuais não contíguas fisicamente;
- wear leveling e FTL no SSD;
- múltiplas janelas mapeadas pelo runtime;
- desperdício por superdimensionamento da arena.

Esses custos devem ser medidos separadamente.

## 10. Relação com faults e bad blocks

O layout não transforma bad block em endereço fixo. A composição correta é:

```text
ObjectID
→ relative span
→ logical mapping epoch
→ physical translation
→ sparse fault overlay
```

Quando um suporte físico falha:

```text
relative span permanece
mapping_epoch aumenta
physical target pode mudar
fault ledger preserva o destino anterior
```

Região `FAULT` não pode ser resolvida para leitura até uma política de recuperação produzir novo binding válido.

## 11. Gates

Executar:

```bash
sh tools/test_rmr_static_layout.sh
```

O selftest verifica:

- manifesto válido;
- assinatura determinística não nula;
- resolução por base + offset;
- arquivo/região vazia preservando posição sem payload;
- bounds adversarial;
- rebind com offsets reutilizados;
- invalidação de ponteiros absolutos após troca de base;
- bloqueio de época regressiva;
- fixed-bit mask;
- rejeição de overlap;
- bloqueio de mudança de base para região pinada;
- proibição de leitura de região marcada `FAULT`.

## 12. Limite epistemológico

Estado atual:

```yaml
reference_c_implementation: PRESENT
host_compilation_gate: PENDING_CI
relative_offset_reuse: TESTABLE
android_virtual_address_stability: NOT_CLAIMED
physical_address_stability: TOKEN_VAZIO
zipraf_zero_copy_end_to_end: TOKEN_VAZIO
zipraf_zero_allocation_runtime: false_for_current_java_path
independent_reproducibility: TOKEN_VAZIO
claim_allowed: false
```

## R3

```text
F_ok:
  arena estática + offsets relativos + ZIPRAF STORE + ELF sem relocações
  foram ligados por um contrato implementável e testável

F_gap:
  pinning físico, ASLR Android, linker geral, ZIPRAF >2GiB e prova independente
  continuam delimitados

F_next:
  executar gate CI, ligar o manifesto ao ZIPRAF central directory e medir
  page faults, mappings, p50/p95/p99 e bytes copiados
```
