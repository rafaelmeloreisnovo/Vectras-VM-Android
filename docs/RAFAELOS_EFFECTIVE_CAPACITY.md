# RAFAELOS ∴ Effective Capacity, Harmonic Fidelity and Spectral Channels

> Documento técnico para transformar a linguagem RAFAELIA em métricas mensuráveis: throughput, canais, fidelidade harmônica, gama espectral, MB/s por canal e limites reais no Android 14 / ARM64.

## 1. Escopo

Este arquivo define como estimar a capacidade efetiva dos módulos:

- `rafcore`
- `zrf`
- `zipraf`
- `netraf`
- `rafstorage`
- `deepseek_local`

O objetivo é separar:

1. **capacidade física real**: CPU, RAM, storage, rede, Android runtime;
2. **capacidade de representação**: hashes, eventos, vetores, metadados;
3. **capacidade espectral**: análise de áudio/sinais por FFT ou bancos de filtros;
4. **capacidade simbólica**: intenção, semântica, estado e origem — registrada como metadado, nunca como prova mágica.

## 2. Fórmulas-base

### 2.1 Throughput por canal

```text
MB_s_por_canal = sample_rate_hz * bytes_por_sample / 1_000_000
MB_s_total     = MB_s_por_canal * canais
```

Exemplo com áudio/sinal em `float32`:

```text
sample_rate = 48_000 Hz
bytes/sample = 4
1 canal = 48_000 * 4 = 192_000 B/s ≈ 0,192 MB/s
1024 canais = 196,608 MB/s
4096 canais = 786,432 MB/s
```

### 2.2 Resolução espectral

```text
hz_por_bin = sample_rate_hz / fft_size
```

Exemplo:

```text
48_000 / 4096 ≈ 11,72 Hz/bin
20_000 Hz de faixa útil / 4096 ≈ 4,88 Hz/bin
```

### 2.3 Capacidade de catálogo ZRF

```text
bytes_por_registro_zrf ≈ header + sha256 + size + flags + timestamp + path_ref
```

Estimativa conservadora:

```text
128 a 512 bytes por registro
1.000.000 registros ≈ 128 MB a 512 MB
```

### 2.4 Eficiência ZIPRAF

```text
compressao_efetiva = tamanho_bruto / tamanho_zipraf
```

Estimativas por tipo:

| Tipo de dado | Compressão comum |
|---|---:|
| texto / logs | 5x a 20x |
| JSON / JSONL | 3x a 10x |
| DEX | 1,2x a 2x |
| APK já comprimido | 1,0x a 1,3x |
| áudio lossy | 1,0x a 1,1x |
| binário randomizado/criptografado | 1,0x |

## 3. Capacidade por módulo

## 3.1 rafcore

Função: coordenar buffers, filas, execução local, estado e políticas.

| Métrica | Estimativa em ARM64 intermediário |
|---|---:|
| canais lógicos simultâneos | 32 a 256 |
| buffer por canal | 64 KB a 4 MB |
| throughput RAM local | 100 a 800 MB/s |
| latência de fila | microssegundos a milissegundos |
| gargalo principal | GC/ART se usar Java; I/O se usar storage |

Capacidade harmônica: não é codec; apenas roteia e agenda análise de sinais.

## 3.2 zrf

Função: registrar eventos e vetores de prova.

ZRF não é áudio. Ele registra:

- hash;
- tamanho;
- origem;
- estado (`PASS`, `FAIL`, `TOKEN_VAZIO`, etc.);
- tempo;
- caminho;
- tags de análise;
- evidência mínima.

| Métrica | Estimativa |
|---|---:|
| registro mínimo | 128 B |
| registro completo | 256 a 512 B |
| 1 MB de ZRF | ~2.000 a 8.000 registros |
| 100 MB de ZRF | ~200.000 a 800.000 registros |

Fidelidade: alta para cadeia de custódia se o hash for preservado; baixa se faltar origem.

## 3.3 zipraf

Função: empacotar artefatos e relatórios.

| Tipo de operação | Throughput esperado |
|---|---:|
| ZIP store sem compressão | 50 a 300 MB/s |
| deflate texto/log | 10 a 80 MB/s |
| deflate APK/DEX já comprimido | 5 a 50 MB/s |

Fidelidade: sem perda se usar ZIP/deflate normal. ZIPRAF deve preservar SHA256 por arquivo.

## 3.4 netraf

Função: comunicação e interoperabilidade.

| Canal | Capacidade típica |
|---|---:|
| localhost/socket local | 100 a 1000 MB/s |
| pipe/processo local | 50 a 500 MB/s |
| shared memory | 1 a 5 GB/s |
| Wi-Fi real | 2,5 a 25 MB/s |
| rede móvel | variável; 0,1 a 20 MB/s |

Regra: NETRAF não deve enviar dados privados por padrão. Rede só com autorização explícita e logs.

## 3.5 rafstorage

Função: catálogo local auditável.

| Catálogo | Tamanho de índice |
|---|---:|
| 10.000 arquivos | 1 a 5 MB |
| 1.000.000 arquivos | 128 a 512 MB |
| 1 TB catalogado | 10 a 100 MB se índice resumido; 128 MB+ se completo |

`rafstorage` não multiplica espaço físico. Ele reduz custo de localização e prova.

## 3.6 deepseek_local

Função: varredura local segura, sem execução, sem invasão.

| Operação | Throughput esperado |
|---|---:|
| busca de magic bytes | 100 a 500 MB/s |
| hashes SHA256 | 50 a 250 MB/s |
| parser ZIP central directory | 50 a 300 MB/s |
| análise de strings | 20 a 150 MB/s |
| FFT/sinal | depende do tamanho e canais |

## 4. Fidelidade harmônica e gama espectral

Quando os módulos analisarem sinais ou áudio, usar parâmetros explícitos.

### 4.1 Faixas

| Sinal | Faixa típica |
|---|---:|
| voz humana telefônica | 300 Hz a 3400 Hz |
| voz ampla | 80 Hz a 8 kHz |
| áudio hi-fi | 20 Hz a 20 kHz |
| sensores/ruído eletrônico | depende do ADC e amostragem |

### 4.2 Canais FFT

| FFT size | Resolução em 48 kHz | Uso |
|---:|---:|---|
| 512 | 93,75 Hz/bin | leve, rápido |
| 1024 | 46,875 Hz/bin | voz/sinal básico |
| 4096 | 11,719 Hz/bin | análise fina |
| 8192 | 5,859 Hz/bin | mais fino, mais latência |

### 4.3 MB/s por canal em float32

| Sample rate | 1 canal | 64 canais | 1024 canais | 4096 canais |
|---:|---:|---:|---:|---:|
| 8 kHz | 0,032 MB/s | 2,048 MB/s | 32,768 MB/s | 131,072 MB/s |
| 16 kHz | 0,064 MB/s | 4,096 MB/s | 65,536 MB/s | 262,144 MB/s |
| 48 kHz | 0,192 MB/s | 12,288 MB/s | 196,608 MB/s | 786,432 MB/s |
| 96 kHz | 0,384 MB/s | 24,576 MB/s | 393,216 MB/s | 1.572,864 MB/s |

### 4.4 Interpretação prática

No Realme Note 50, 4096 canais float32 em 48 kHz pode estourar CPU/RAM se combinado com análise pesada. Começar com:

```text
FFT 1024 ou 2048
64 a 256 canais lógicos
float32 ou int16 conforme precisão
```

## 5. Matriz de capacidade efetiva

| Configuração | Uso | Capacidade recomendada |
|---|---|---:|
| Seguro mínimo | inventário APK/XAPK | 50 a 300 MB/s de leitura |
| ZRF catálogo | 100k arquivos | 12 a 50 MB de índice |
| ZIPRAF relatório | logs/textos | compressão 5x a 20x |
| NETRAF local | IPC | 50 a 500 MB/s |
| Sinal voz | voz local autorizada | 16 kHz, 1 a 8 canais |
| Espectral médio | análise detalhada | 48 kHz, FFT 2048, 64 canais |
| Espectral pesado | laboratório | 48 kHz, FFT 4096, 256+ canais |

## 6. O que entra no código-fonte

Próximos arquivos a criar:

```text
rafaelia/include/raf_capacity.h
rafaelia/src/raf_capacity.c
rafaelia/tests/test_raf_capacity.c
```

Funções mínimas:

```c
uint64_t raf_bytes_per_second(uint32_t sample_rate_hz, uint32_t bytes_per_sample, uint32_t channels);
double raf_mb_per_second(uint64_t bytes_per_second);
double raf_hz_per_bin(uint32_t sample_rate_hz, uint32_t fft_size);
uint64_t raf_zrf_index_bytes(uint64_t records, uint32_t bytes_per_record);
```

## 7. Estados e prudência

- Estes números são estimativas de engenharia.
- Valores reais dependem de kernel, scheduler, storage, temperatura, throttling, GC, NDK, flags e estado do aparelho.
- Toda medição real deve vir com log, device info, hash do binário, data e estado `PASS/FAIL/RUNTIME`.

## 8. Retroalimentação

**F_ok:** agora a arquitetura tem métrica: canais, MB/s, FFT, throughput e storage.

**F_gap:** falta benchmark real no Realme Note 50.

**F_next:** implementar `raf_capacity.h/c` e testes C puros para validar os cálculos no Termux/NDK.
