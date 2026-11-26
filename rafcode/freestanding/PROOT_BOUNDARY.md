# PRoot / Vectras / RAFCODE license boundary

## Distinct sources (not a claim of private copyright in upstream work)

| Module | Existing rightsholder/source | License |
| --- | --- | --- |
| `rafcode/freestanding/**` newly created files | Rafael Melo Reis, RAFCODE-Φ | MIT (see LICENSE-MIT) |
| Vectras original / this fork outside this module | xoureldeen and respective contributors | GPL-2.0, repository root LICENSE |
| Official PRoot implementation, **not imported in this module** | `proot-me/proot` and contributors | GPL-2.0-or-later |

Canonical PRoot origin: https://github.com/proot-me/proot
Official upstream license text: https://github.com/proot-me/proot/blob/master/COPYING
Observed upstream COPYING Git blob SHA-1: `d159169d1050894d3ea3b98e1c965c4058208fe1`.
Observed upstream README: `GPL-2.0-or-later`, license basis includes upstream
README and per-file notices; this recorded blob is a provenance **reference**,
not the user's SHA-256 artifact checksum.

## Rules

1. Do not recast any PRoot/Vectras existing source as original MIT code.
2. Do not remove existing copyright, license notices or original author credits.
3. Do not put PRoot code in the MIT subtree. Preserve original PRoot license and
   source offer if packaged/distributed; include original unmodified COPYING in
   any actual PRoot distribution. No PRoot payload is supplied here.
4. If linking MIT module with GPL-covered Vectras into one derivative/combined
   program, the downstream distribution must comply with GPL obligations;
   file-scoped MIT notices remain valid but are not an escape from GPL.
5. Authorial RAFCODE-Φ is a provenance identifier, not a claim that symbols,
   algorithms, ABI conventions or interfaces are owned exclusively.
6. This is an engineering classification, not legal advice. Resolve disputes
   with actual source ownership and competent license review before distribution.

## No dependency fiction

`PRoot=NOT_IMPLEMENTED`, `QEMU=NOT_IMPLEMENTED`,
`ptrace=NOT_IMPLEMENTED`, `syscall interception=NOT_IMPLEMENTED`,
`guest boot=NOT_RUN` in this freestanding core. Optional `raf_port`
callbacks do not themselves implement any platform feature. Pure path
normalization is *not* a filesystem sandbox.

Rollback: all changes under `rafcode/freestanding/` and isolated CI only;
no root LICENSE change, no PRoot import, no VM install path modification.
