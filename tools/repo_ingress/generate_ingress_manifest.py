#!/usr/bin/env python3
"""Read-only, stdlib-only APK/DEX/ELF ingress manifest generator."""
from __future__ import annotations
import argparse, hashlib, json, os, re, struct, sys, zipfile
from collections import Counter, defaultdict
from pathlib import Path, PurePosixPath
from typing import Any, Iterable

VERSION="1.0.0"; DEX_HEADER=0x70
MACHINES={3:"x86",40:"arm",62:"x86_64",183:"aarch64",243:"riscv"}
ABI_MACHINE={"armeabi-v7a":"arm","arm64-v8a":"aarch64","x86":"x86","x86_64":"x86_64","riscv64":"riscv"}

def finding(code:str,severity:str,message:str,**details:Any)->dict[str,Any]:
    out={"code":code,"severity":severity,"message":message}
    if details: out["details"]=details
    return out

def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()

def safe_name(name:str)->bool:
    if not name or "\x00" in name or "\\" in name or name.startswith("/") or re.match(r"^[A-Za-z]:",name): return False
    return ".." not in PurePosixPath(name).parts

def parse_dex(data:bytes,size:int)->tuple[dict[str,Any],list[dict[str,Any]]]:
    out={"kind":"dex","actual_size":size}; issues=[]
    if len(data)<DEX_HEADER:
        return out,[finding("DEX_HEADER_TRUNCATED","critical","DEX header has fewer than 112 bytes.")]
    magic=data[:8]
    if not (magic[:4]==b"dex\n" and magic[4:7].isdigit() and magic[7]==0):
        return out,[finding("DEX_BAD_MAGIC","critical","Invalid DEX magic/version field.")]
    file_size,header_size,endian=struct.unpack_from("<III",data,0x20)
    out.update(version=magic[4:7].decode(),declared_file_size=file_size,declared_header_size=header_size,endian_tag=f"0x{endian:08x}")
    if file_size!=size: issues.append(finding("DEX_FILE_SIZE_MISMATCH","critical","DEX file_size differs from actual size.",declared=file_size,actual=size))
    if header_size!=DEX_HEADER: issues.append(finding("DEX_HEADER_SIZE_MISMATCH","critical","DEX header_size must be 0x70.",declared=header_size))
    if endian not in (0x12345678,0x78563412): issues.append(finding("DEX_ENDIAN_TAG_UNKNOWN","warning","Unknown DEX endian_tag.",declared=f"0x{endian:08x}"))
    return out,issues

def parse_elf(data:bytes)->tuple[dict[str,Any],list[dict[str,Any]]]:
    out={"kind":"elf"}; issues=[]
    if len(data)<20 or data[:4]!=b"\x7fELF":
        return out,[finding("ELF_HEADER_INVALID","critical","Invalid or truncated ELF header.")]
    order={1:"little",2:"big"}.get(data[5])
    out.update(**{"class":{1:"ELF32",2:"ELF64"}.get(data[4],f"unknown:{data[4]}"),"endianness":order or f"unknown:{data[5]}"})
    if order is None: return out,[finding("ELF_ENDIANNESS_UNKNOWN","critical","Unknown ELF endianness.")]
    mid=int.from_bytes(data[18:20],order); out.update(machine_id=mid,machine=MACHINES.get(mid,f"unknown:{mid}"))
    return out,issues

def archive_type(names:list[str],suffix:str)->str:
    s=set(names); has_dex=any(re.fullmatch(r"classes(?:\d+)?\.dex",n) for n in names)
    if suffix==".apk" or ("AndroidManifest.xml" in s and has_dex): return "apk"
    if suffix==".aab" or "BundleConfig.pb" in s or "base/manifest/AndroidManifest.xml" in s: return "aab"
    if suffix==".jar" or "META-INF/MANIFEST.MF" in s or any(n.endswith(".class") for n in names): return "jar"
    return "zip"

def inspect_zip(path:Path,max_entries:int,probe:int)->tuple[dict[str,Any],list[dict[str,Any]]]:
    out={"kind":"zip","archive_type":"zip","entry_count":0,"dex_entries":[],"native_libraries":[]}; issues=[]
    try:
        with zipfile.ZipFile(path) as z:
            infos=z.infolist(); names=[i.filename for i in infos]; out["entry_count"]=len(infos)
            if len(infos)>max_entries:
                return out,[finding("ZIP_TOO_MANY_ENTRIES","critical","Archive exceeds entry limit.",limit=max_entries,actual=len(infos))]
            out["archive_type"]=archive_type(names,path.suffix.lower())
            for name,count in sorted(Counter(names).items()):
                if count>1: issues.append(finding("ZIP_DUPLICATE_ENTRY","critical","Duplicate central-directory name.",entry=name,count=count))
            for i in infos:
                if not safe_name(i.filename): issues.append(finding("ZIP_UNSAFE_PATH","critical","Unsafe archive path.",entry=i.filename))
                if i.flag_bits&1: issues.append(finding("ZIP_ENCRYPTED_ENTRY","critical","Encrypted entry cannot be auto-promoted.",entry=i.filename))
            if out["archive_type"]=="apk":
                if "AndroidManifest.xml" not in set(names): issues.append(finding("APK_MISSING_MANIFEST","critical","APK has no AndroidManifest.xml."))
                dex=[i for i in infos if re.fullmatch(r"classes(?:\d+)?\.dex",i.filename)]
                if not dex: issues.append(finding("APK_MISSING_DEX","critical","APK has no classes*.dex."))
                for i in dex:
                    with z.open(i) as f: head=f.read(DEX_HEADER)
                    parsed,sub=parse_dex(head,i.file_size); parsed["entry"]=i.filename; out["dex_entries"].append(parsed)
                    for x in sub: x.setdefault("details",{})["entry"]=i.filename
                    issues.extend(sub)
                for i in (x for x in infos if re.fullmatch(r"lib/[^/]+/[^/]+\.so",x.filename)):
                    with z.open(i) as f: head=f.read(min(probe,64))
                    parsed,sub=parse_elf(head); abi=PurePosixPath(i.filename).parts[1]; expected=ABI_MACHINE.get(abi)
                    out["native_libraries"].append({"entry":i.filename,"abi_path":abi,"expected_machine":expected,**parsed})
                    for x in sub: x.setdefault("details",{})["entry"]=i.filename
                    issues.extend(sub)
                    if expected and parsed.get("machine")!=expected:
                        issues.append(finding("ELF_ABI_PATH_MISMATCH","critical","ELF machine differs from APK ABI path.",entry=i.filename,abi=abi,expected=expected,actual=parsed.get("machine")))
    except (OSError,zipfile.BadZipFile,RuntimeError,NotImplementedError) as e:
        issues.append(finding("ZIP_READ_ERROR","critical",f"Cannot read ZIP central directory: {e}"))
    return out,issues

def textual(data:bytes)->bool:
    if not data: return True
    if b"\0" in data: return False
    try: data.decode("utf-8"); return True
    except UnicodeDecodeError: return False

def inspect(path:Path,repo:Path,max_entries:int=100000,probe:int=4096)->dict[str,Any]:
    rel=path.resolve().relative_to(repo.resolve()).as_posix(); size=path.stat().st_size
    with path.open("rb") as f: head=f.read(max(probe,DEX_HEADER))
    rec={"path":rel,"size_bytes":size,"sha256":sha256(path),"suffix":path.suffix.lower(),"classification":{"kind":"other"},"findings":[]}
    if head.startswith(b"\x7fELF"): cls,issues=parse_elf(head)
    elif head.startswith(b"dex\n"): cls,issues=parse_dex(head,size)
    elif head.startswith((b"PK\x03\x04",b"PK\x05\x06",b"PK\x07\x08")) or path.suffix.lower() in {".zip",".apk",".aab",".jar"}:
        cls,issues=inspect_zip(path,max_entries,probe)
    else: cls,issues={"kind":"text" if textual(head) else "binary"},[]
    rec["classification"]=cls; rec["findings"]=issues; return rec

def files(repo:Path,roots:Iterable[str],include_root:bool)->Iterable[Path]:
    selected=set()
    for name in roots:
        root=(repo/name).resolve()
        if root.is_file(): selected.add(root)
        elif root.is_dir(): selected.update(p.resolve() for p in root.rglob("*") if p.is_file())
    if include_root: selected.update(p.resolve() for p in repo.iterdir() if p.is_file())
    yield from sorted(selected,key=lambda p:p.relative_to(repo.resolve()).as_posix())

def build(repo:Path,roots:Iterable[str],include_root:bool,max_entries:int,probe:int)->dict[str,Any]:
    records=[]; errors=[]
    for path in files(repo,roots,include_root):
        try: records.append(inspect(path,repo,max_entries,probe))
        except (OSError,ValueError) as e: errors.append({"path":path.relative_to(repo).as_posix(),"error":str(e)})
    by_hash=defaultdict(list)
    for r in records: by_hash[r["sha256"]].append(r["path"])
    duplicates=[{"sha256":h,"paths":sorted(ps)} for h,ps in sorted(by_hash.items()) if len(ps)>1]
    counts=Counter(x["code"] for r in records for x in r["findings"])
    critical=sum(x["severity"]=="critical" for r in records for x in r["findings"])
    out={"schema":"rafcodephi.ingress-manifest/v1","scanner_version":VERSION,"repo_root":".","roots":sorted(set(roots)),"include_root_files":include_root,
         "summary":{"files":len(records),"duplicates":len(duplicates),"critical_findings":critical,"scan_errors":len(errors),"finding_counts":dict(sorted(counts.items()))},
         "duplicates":duplicates,"files":records,"scan_errors":errors}
    if os.getenv("SOURCE_DATE_EPOCH"): out["source_date_epoch"]=os.environ["SOURCE_DATE_EPOCH"]
    return out

def markdown(m:dict[str,Any])->str:
    s=m["summary"]; lines=["# Ingress Manifest — APK / DEX / ELF / loose files","",f"- Files scanned: **{s['files']}**",f"- Duplicate hash groups: **{s['duplicates']}**",f"- Critical findings: **{s['critical_findings']}**",f"- Scan errors: **{s['scan_errors']}**","","## Findings","","| Severity | Code | Path | Message |","|---|---|---|---|"]
    rows=0
    for r in m["files"]:
        for x in r["findings"]:
            lines.append(f"| {x['severity']} | `{x['code']}` | `{r['path'].replace('|','\\|')}` | {x['message'].replace('|','\\|')} |"); rows+=1
    if not rows: lines.append("| — | — | — | No structural findings. |")
    lines+=["","## Duplicate content","","| SHA-256 | Paths |","|---|---|"]
    if m["duplicates"]:
        for g in m["duplicates"]: lines.append(f"| `{g['sha256']}` | "+"<br>".join(f"`{p}`" for p in g["paths"])+" |")
    else: lines.append("| — | No duplicate hash groups. |")
    lines+=["","## Promotion rule","","Inventory evidence is not integrated runtime code. Promotion requires provenance, a reviewed destination, a domain test and rollback.",""]
    return "\n".join(lines)

def args(argv:list[str])->argparse.Namespace:
    p=argparse.ArgumentParser(description=__doc__); p.add_argument("--repo-root",type=Path,default=Path("."))
    p.add_argument("--roots",nargs="*",default=["Incluir","_incoming","__DELTA__"]); p.add_argument("--include-root-files",action="store_true")
    p.add_argument("--json-out",type=Path,default=Path("reports/ingress/ingress_manifest.json")); p.add_argument("--markdown-out",type=Path,default=Path("reports/ingress/ingress_manifest.md"))
    p.add_argument("--max-zip-entries",type=int,default=100000); p.add_argument("--max-entry-probe",type=int,default=4096)
    p.add_argument("--fail-on",choices=("none","critical","any"),default="critical"); return p.parse_args(argv)

def main(argv:list[str]|None=None)->int:
    a=args(sys.argv[1:] if argv is None else argv); repo=a.repo_root.resolve()
    m=build(repo,a.roots,a.include_root_files,a.max_zip_entries,a.max_entry_probe)
    jo=a.json_out if a.json_out.is_absolute() else repo/a.json_out; mo=a.markdown_out if a.markdown_out.is_absolute() else repo/a.markdown_out
    jo.parent.mkdir(parents=True,exist_ok=True); mo.parent.mkdir(parents=True,exist_ok=True)
    jo.write_text(json.dumps(m,indent=2,sort_keys=True)+"\n",encoding="utf-8"); mo.write_text(markdown(m),encoding="utf-8")
    total=sum(len(r["findings"]) for r in m["files"]); critical=m["summary"]["critical_findings"]
    print(f"ingress-manifest: files={m['summary']['files']} findings={total} critical={critical} errors={m['summary']['scan_errors']}")
    if m["summary"]["scan_errors"]: return 2
    if a.fail_on=="critical" and critical: return 3
    if a.fail_on=="any" and total: return 4
    return 0
if __name__=="__main__": raise SystemExit(main())
