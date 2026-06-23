#!/usr/bin/env python3
"""Validate the Motorola E7 Power ARM32 beta APK ABI contract."""
from __future__ import annotations
import argparse, json, sys, zipfile
from pathlib import Path

KNOWN_ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64", "riscv64"}
REQUIRED_LIBS = ["libXlorie.so", "libvectra_core_accel.so"]
WARN_ONLY_LIBS = {
    "libtermux.so": "ARM32 fallback: internal PTY/terminal JNI may be unavailable; VNC/QEMU screen path remains valid when libXlorie.so and runtime binaries load.",
    "libtermux-bootstrap.so": "Bootstrap native zip helper is optional in assets-loader mode; Java asset bootstrap path remains available.",
}

def args():
    p=argparse.ArgumentParser()
    p.add_argument('apk_path')
    p.add_argument('--policy', required=True)
    p.add_argument('--supported-abis', required=True)
    p.add_argument('--target-device', required=True)
    p.add_argument('--target-android', required=True)
    return p.parse_args()

def main():
    a=args(); apk=Path(a.apk_path)
    supported=[x.strip() for x in a.supported_abis.split(',') if x.strip()]
    failures=[]
    if a.policy != 'arm32-debug': failures.append(f"policy must be arm32-debug, got {a.policy}")
    if supported != ['armeabi-v7a']: failures.append(f"SUPPORTED_ABIS must be exactly armeabi-v7a, got {supported}")
    names=[]; found_abis=[]; required={f"lib/armeabi-v7a/{x}": False for x in REQUIRED_LIBS}; warn={}
    if not apk.exists():
        failures.append(f"APK does not exist: {apk}")
    else:
        try:
            with zipfile.ZipFile(apk) as z:
                names=z.namelist()
        except zipfile.BadZipFile:
            failures.append(f"APK is not a valid zip: {apk}")
    if names:
        for required_entry in ['AndroidManifest.xml','classes.dex']:
            if required_entry not in names: failures.append(f"missing {required_entry}")
        abis=sorted({n.split('/')[1] for n in names if n.startswith('lib/') and len(n.split('/'))>2})
        found_abis=abis
        if 'armeabi-v7a' not in abis: failures.append('missing lib/armeabi-v7a/ directory')
        forbidden=sorted(set(abis)-{'armeabi-v7a'})
        if forbidden: failures.append(f"forbidden ABI directories in ARM32 beta APK: {forbidden}")
        for entry in list(required):
            required[entry]=entry in names
            if not required[entry]: failures.append(f"missing required native library: {entry}")
        for lib, note in WARN_ONLY_LIBS.items():
            entry=f"lib/armeabi-v7a/{lib}"
            warn[entry]={"present": entry in names, "note": note}
    missing=[k for k,v in required.items() if not v]
    report={
        'apk_path': str(apk), 'apk_size_bytes': apk.stat().st_size if apk.exists() else 0,
        'target_device': a.target_device, 'target_android': a.target_android,
        'app_abi_policy': a.policy, 'supported_abis': supported, 'found_abis': found_abis,
        'required_libs': required, 'missing_required_libs': missing,
        'warn_only_libs': warn, 'status': 'PASS' if not failures else 'FAIL', 'failures': failures,
    }
    out=Path('reports'); out.mkdir(exist_ok=True)
    (out/'apk_arm32_beta_contract.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    md=["# APK ARM32 Beta Contract", "", f"- status: **{report['status']}**", f"- apk_path: `{apk}`", f"- target_device: {a.target_device}", f"- target_android: {a.target_android}", f"- app_abi_policy: `{a.policy}`", f"- supported_abis: `{','.join(supported)}`", f"- found_abis: `{','.join(found_abis) if found_abis else 'none'}`", "", "## Required libs"]
    md += [f"- {'PASS' if ok else 'FAIL'} `{k}`" for k,ok in required.items()]
    md += ["", "## Warn-only libs"] + [f"- {'present' if v['present'] else 'missing'} `{k}` — {v['note']}" for k,v in warn.items()]
    if failures: md += ["", "## Failures"] + [f"- {f}" for f in failures]
    (out/'APK_ARM32_BETA_CONTRACT.md').write_text("\n".join(md)+"\n")
    return 0 if not failures else 1
if __name__ == '__main__': sys.exit(main())
