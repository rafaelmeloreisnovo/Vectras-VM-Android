# Vectras tools/ci — índice funcional (2026-10-25)

**Estado:** SOURCE_INSPECTED / RUNTIME_NOT_RUN / HOLD. A pasta contém 76 arquivos no corte de origem, incluindo scripts, testes e manifestos; não equivale a 76 aplicações independentes.

| Arquivo observado | Capacidade documentada |
|---|---|
| [audit_freestanding_link_probe.py](audit_freestanding_link_probe.py) | Inspeção de símbolos de um link ELF com entradas de binário, map e build log. |
| [build_provenance_closure_report.py](build_provenance_closure_report.py) | Consolidação de proveniência de build. |
| [rafaelia_rigor_pipeline.py](rafaelia_rigor_pipeline.py) | Verificação simbólica e numérica; usa numpy, sympy e matplotlib, portanto é ferramenta Python hospedada e não L0. |
| [check_abi_contract_drift.py](check_abi_contract_drift.py) | Detectar divergência de contrato ABI. |
| [run_tools_runtime_evidence_gate.sh](run_tools_runtime_evidence_gate.sh) | Checar recibos de runtime, exigindo insumos reais. |

**Evidência necessária:** SHA exato, toolchain e flags, resultados positivos/negativos, execução ARM32/64 quando aplicável, no-undefined/link e recibo verificável. Compilação e assinatura não demonstram APK instalado nem boot guest. Sem exclusão ou promoção automática; claim_allowed=false.
