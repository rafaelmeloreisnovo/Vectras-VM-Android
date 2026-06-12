# VECTRA_OS contract report

- Data: 2026-06-12T16:26:55Z
- Commit: 25da9c2
- Arch: x86_64
- CC: cc
- Flags: `-O3 -std=c11 -Wall -Wextra -DRMR_JNI_BUILD=1 -fPIC   -ffunction-sections -fdata-sections -fvisibility=hidden -shared -Wl,--gc-sections -Wl,--exclude-libs,ALL`

## Sinal de eliminação (warnings -Wunused capturados)

- Total: 1 (warning = seção morta sinalizada ao gc-sections; não suprimir)

```
/home/user/Vectras-VM-Android/engine/rmr/src/rmr_vectra_os.c:73:19: warning: 'vos_tick_sw' defined but not used [-Wunused-function]
```

## Símbolos exportados (.dynsym)

```
vos_caps_report
vos_init
vos_selftest
```

## Resultado

- CONTRATO VÁLIDO: API pública = 3 símbolos; gc-sections efetivo; hot path sem símbolos proibidos.
