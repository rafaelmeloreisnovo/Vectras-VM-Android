# Vectras — Tri-Repo Mission Cohesion Binding V1

**Binding ID:** `VECTRAS-TRI-REPO-MISSION-COHESION-V1`  
**Canonical cohesion source:** `termux-app-rafacodephi@f83921f6a04e99199bdbef6a9d56607c150f4f2c`  
**Vectras baseline:** `89e47837e551a565ceb7158cba1ced6954e72d93`  
**RafPolimata baseline:** `3956bc6a7d7527ce3535661d170507c761b27fcb`  
**claim_allowed:** `false`

Vectras remains an optional governed runtime backend. This binding adds no Android/QEMU/package/bootstrap/UI behavior and does not promote runtime evidence.

## Role boundary

```text
Authorized bounded request
 -> Vectras dispatch/bootstrap
 -> runtime/process evidence
 -> guest/device evidence when actually observed
 -> scoped receipt
```

Vectras does not own mission semantics, corpus semantics, model-weight training authority, provider/legal/repository authority, manual promotion, or scientific claim promotion.

## Cross-repo authority

- `termux-app-rafacodephi`: orchestration + bounded governance.
- `Vectras-VM-Android`: optional governed runtime backend.
- `RafPolimata`: analysis/compiler/freestanding/research-validation authority within scoped gates.

## Required invariants

```text
SOURCE != EXECUTION
EXECUTION != EVIDENCE
EVIDENCE != CLAIM
TOKEN_VAZIO != 0
RETRIEVAL_CONTEXT != WEIGHT_UPDATE
LEARN_APPEND_ONLY != ONLINE_SELF_TRAINING
CONTINUE_APPROVED_SCOPE != AUTONOMOUS_GOAL_CREATION
DISPATCH != RUNTIME_PROOF
GUEST_BOOT != PHYSICAL_DEVICE_PROOF
```

## External/runtime gates preserved

```text
Android/Termux físico          = TOKEN_VAZIO_DEVICE
multi-repo runtime real        = TOKEN_VAZIO_EXECUTION
identidade remota              = TOKEN_VAZIO_RUNTIME
autorização provider/legal     = TOKEN_VAZIO_EXTERNAL_AUTHORITY
ruleset live                   = TOKEN_VAZIO_EXTERNAL_AUTHORITY
server-side enforcement        = TOKEN_VAZIO_EXTERNAL_AUTHORITY
promoção manual                = TOKEN_VAZIO_MANUAL_AUTHORITY
CodeScan credencial/análise    = TOKEN_VAZIO_SECRET
treino/fine-tuning de pesos    = NÃO AUTORIZADO
scientific claim promotion     = false
claim_allowed                  = false
```

No local source or CI result may self-promote any external gate above.
