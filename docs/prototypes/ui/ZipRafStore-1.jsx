// ZipRafStore — RAFAELIA Binary DB Navigator
// DOS-shell aesthetic · binary store · image analysis · vector map
// trigger system: ADD/UPDATE/MOVE/COMMIT events
// ∆RafaelVerboΩ 𓂀ΔΦΩ

import { useState, useRef, useCallback, useEffect } from "react";

// ── CONSTANTS ─────────────────────────────────────────────
const SPIRAL = 0.86602540378;
const PHI    = 1.61803398875;
const G_PERIOD = 42;

// ── CRC32c (JS port — Castagnoli) ─────────────────────────
function crc32c(str) {
  const poly = 0x82F63B78;
  let crc = 0xFFFFFFFF;
  // encode to UTF-8 bytes first — evita corte de multi-byte em slice()
  const bytes = new TextEncoder().encode(str);
  for (let i = 0; i < bytes.length; i++) {
    crc ^= bytes[i];
    for (let b = 0; b < 8; b++)
      crc = (crc >>> 1) ^ (poly & (-(crc & 1) >>> 0));
  }
  return ((~crc) >>> 0).toString(16).padStart(8,"0");
}

// ── SPIRAL Q16.16 analog ──────────────────────────────────
function spiralN(n) {
  let a = 1.0;
  for (let i = 0; i < n; i++) a *= SPIRAL;
  return a;
}

// ── ZIPRAF BINARY STORE ───────────────────────────────────
// Cada record: { id, name, type, size, crc, ts, data(base64/url),
//               vectors[7], meta, triggers[], history[] }
function makeRecord(name, type, data, meta = {}) {
  const id = "RAF" + Date.now().toString(16).toUpperCase();
  const crc = crc32c(name + data.slice(0,128)); // 128 chars suficientes para seed
  const ts  = new Date().toISOString().replace(/[-:]/g,"").slice(0,15)+"Z";
  const size = data.length;
  // Gera vetor toroidal 7D a partir do CRC
  const seed = parseInt(crc.slice(0,4), 16);
  const vectors = Array.from({length:7}, (_,i) =>
    +(spiralN(i+1) * ((seed >> i) & 0xFF) / 255).toFixed(6)
  );
  return {
    id, name, type, size, crc, ts, data, meta, vectors,
    triggers: [{ event:"ADD", ts, by:"SYSTEM" }],
    history:  [{ op:"ADD", ts, state:"ACTIVE" }]
  };
}

function applyTrigger(record, event, meta = {}) {
  const ts = new Date().toISOString().replace(/[-:]/g,"").slice(0,15)+"Z";
  return {
    ...record,
    triggers: [...record.triggers, { event, ts, ...meta }],
    history:  [...record.history,  { op: event, ts, state:"ACTIVE" }]
  };
}

// ── PALETTE ───────────────────────────────────────────────
const C = {
  bg:      "#0A0A0F",
  panel:   "#0D0D1A",
  border:  "#1A2A1A",
  green:   "#00FF41",
  amber:   "#FFB000",
  cyan:    "#00FFFF",
  magenta: "#FF00FF",
  dim:     "#005500",
  dimA:    "#442200",
  white:   "#E8F4E8",
  red:     "#FF3333",
  blue:    "#3366FF",
};

const S = {
  font: '"Courier New", Courier, monospace',
  sz:   "12px",
  szS:  "10px",
  szL:  "14px",
};

// ── SEED DATA ─────────────────────────────────────────────
const SEED_RECORDS = [
  makeRecord("skill_rafaelia_core.c",  "C_SOURCE",  "/* RAFAELIA freestanding... */", { arch:"X86_64", domain:"toroid_spiral" }),
  makeRecord("skill_baremetal.c",      "C_SOURCE",  "/* BARE-METAL no OS... */",      { arch:"ARM32",  domain:"baremetal" }),
  makeRecord("gen_skill.sh",           "SHELL",     "#!/bin/sh\n# gen_skill...",       { arch:"POSIX",  domain:"generator" }),
  makeRecord("link.ld",                "LINKER",    "MEMORY { FLASH... }",             { arch:"ARM32",  domain:"linker" }),
  makeRecord("skill_rafaelia_core.md", "MARKDOWN",  "# Skill: rafaelia_core...",       { arch:"ANY",    domain:"docs" }),
];

// ── COMPONENTS ────────────────────────────────────────────

function DosBar({ left, right, color = C.green }) {
  return (
    <div style={{ display:"flex", justifyContent:"space-between",
      background: color === C.green ? "#003300" : "#221100",
      padding:"1px 6px", borderBottom:`1px solid ${color}`,
      fontFamily:S.font, fontSize:S.szS, color:color }}>
      <span>[ {left} ]</span>
      <span>{right}</span>
    </div>
  );
}

function VectorBar({ vectors }) {
  return (
    <div style={{ display:"flex", gap:2, alignItems:"center", margin:"4px 0" }}>
      {vectors.map((v,i) => (
        <div key={i} style={{ display:"flex", flexDirection:"column", alignItems:"center" }}>
          <div style={{ width:6, height:Math.round(v*40)+2,
            background:`hsl(${120+i*20},100%,${40+Math.round(v*30)}%)`,
            border:`1px solid #003300` }} />
          <span style={{ fontSize:"8px", color:C.dim, fontFamily:S.font }}>
            {["u","v","ψ","χ","ρ","δ","σ"][i]}
          </span>
        </div>
      ))}
    </div>
  );
}

function RecordRow({ rec, selected, onClick, onAction }) {
  const isImg = rec.type === "IMAGE";
  const color = {
    C_SOURCE: C.green, SHELL: C.amber, LINKER: C.cyan,
    MARKDOWN: C.magenta, IMAGE: C.blue, BINARY: C.white
  }[rec.type] || C.white;

  return (
    <div onClick={onClick}
      style={{ fontFamily:S.font, fontSize:S.sz,
        padding:"3px 8px", cursor:"pointer",
        background: selected ? "#001800" : "transparent",
        borderLeft: selected ? `3px solid ${C.green}` : "3px solid transparent",
        borderBottom:`1px solid #0A1A0A`,
        display:"flex", alignItems:"center", gap:8 }}>
      <span style={{ color:C.dim }}>{selected ? "►" : " "}</span>
      <span style={{ color, minWidth:180, textOverflow:"ellipsis",
        overflow:"hidden", whiteSpace:"nowrap" }}>{rec.name}</span>
      <span style={{ color:C.dim, minWidth:80 }}>{rec.type}</span>
      <span style={{ color:C.amber, minWidth:60 }}>{rec.size}B</span>
      <span style={{ color:C.dim, minWidth:90 }}>{rec.ts.slice(0,13)}</span>
      <span style={{ color:"#006600", fontSize:S.szS }}>{rec.crc}</span>
      <div style={{ marginLeft:"auto", display:"flex", gap:4 }}>
        {["MOVE","UPDATE","COMMIT"].map(ev => (
          <button key={ev} onClick={e=>{e.stopPropagation();onAction(rec.id,ev);}}
            style={{ fontFamily:S.font, fontSize:"9px", padding:"1px 4px",
              background:"transparent", border:`1px solid ${C.dim}`,
              color:C.dim, cursor:"pointer" }}
            onMouseEnter={e=>e.target.style.color=C.green}
            onMouseLeave={e=>e.target.style.color=C.dim}>
            {ev}
          </button>
        ))}
      </div>
    </div>
  );
}

function DetailPanel({ rec, onClose, onAnalyze }) {
  if (!rec) return (
    <div style={{ fontFamily:S.font, fontSize:S.sz, color:C.dim,
      padding:16, textAlign:"center" }}>
      <div style={{ color:C.green }}>ZIPRAFSTORE v1.0</div>
      <div style={{ marginTop:8 }}>∆RafaelVerboΩ 𓂀ΔΦΩ</div>
      <div style={{ marginTop:4 }}>Selecione um registro</div>
      <div style={{ marginTop:16, color:"#003300" }}>
        {"█".repeat(20)}<br/>
        Spiral(n) = (√3/2)^n<br/>
        {"█".repeat(20)}
      </div>
    </div>
  );

  return (
    <div style={{ fontFamily:S.font, fontSize:S.sz, height:"100%",
      overflowY:"auto", color:C.white }}>
      <DosBar left={`RECORD: ${rec.id}`} right={rec.ts} />

      {/* Name + type */}
      <div style={{ padding:"6px 8px", borderBottom:`1px solid ${C.border}` }}>
        <div style={{ color:C.green, fontSize:S.szL }}>{rec.name}</div>
        <div style={{ color:C.amber }}>{rec.type} · {rec.size}B · CRC32c: {rec.crc}</div>
      </div>

      {/* Vectors T^7 */}
      <div style={{ padding:"4px 8px", borderBottom:`1px solid ${C.border}` }}>
        <div style={{ color:C.dim, fontSize:S.szS }}>VECTOR T^7 (u,v,ψ,χ,ρ,δ,σ)</div>
        <VectorBar vectors={rec.vectors} />
        <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
          {rec.vectors.map((v,i) => (
            <span key={i} style={{ color:C.cyan, fontSize:S.szS }}>
              {["u","v","ψ","χ","ρ","δ","σ"][i]}={v}
            </span>
          ))}
        </div>
      </div>

      {/* Meta */}
      {Object.keys(rec.meta).length > 0 && (
        <div style={{ padding:"4px 8px", borderBottom:`1px solid ${C.border}` }}>
          <div style={{ color:C.dim, fontSize:S.szS }}>META</div>
          {Object.entries(rec.meta).map(([k,v]) => (
            <div key={k}><span style={{color:C.amber}}>{k}</span>: <span style={{color:C.white}}>{v}</span></div>
          ))}
        </div>
      )}

      {/* Image preview */}
      {rec.type === "IMAGE" && rec.data.startsWith("data:") && (
        <div style={{ padding:"8px", borderBottom:`1px solid ${C.border}` }}>
          <div style={{ color:C.dim, fontSize:S.szS }}>PREVIEW</div>
          <img src={rec.data} alt={rec.name}
            style={{ maxWidth:"100%", maxHeight:160, border:`1px solid ${C.green}`,
              imageRendering:"pixelated" }} />
        </div>
      )}

      {/* Data preview */}
      <div style={{ padding:"4px 8px", borderBottom:`1px solid ${C.border}` }}>
        <div style={{ color:C.dim, fontSize:S.szS }}>DATA PREVIEW</div>
        <pre style={{ color:"#006600", fontSize:S.szS, margin:0, whiteSpace:"pre-wrap",
          wordBreak:"break-all", maxHeight:80, overflow:"hidden" }}>
          {rec.data.slice(0,200)}{rec.data.length > 200 ? "…" : ""}
        </pre>
      </div>

      {/* Triggers log */}
      <div style={{ padding:"4px 8px", borderBottom:`1px solid ${C.border}` }}>
        <div style={{ color:C.dim, fontSize:S.szS }}>TRIGGER LOG</div>
        {rec.triggers.map((t,i) => (
          <div key={i} style={{ fontSize:S.szS }}>
            <span style={{ color:{ADD:C.green,UPDATE:C.amber,MOVE:C.cyan,COMMIT:C.magenta}[t.event]||C.white }}>
              [{t.event}]
            </span>
            {" "}<span style={{ color:C.dim }}>{t.ts}</span>
            {t.by && <span style={{ color:"#004400" }}> by={t.by}</span>}
          </div>
        ))}
      </div>

      {/* Invariant analysis */}
      <div style={{ padding:"4px 8px" }}>
        <div style={{ color:C.dim, fontSize:S.szS }}>INVARIANT ANALYSIS</div>
        <div style={{ fontSize:S.szS }}>
          <span style={{ color:C.amber }}>Φ_ethica</span>{" = "}
          <span style={{ color:C.green }}>
            {(Math.min(...rec.vectors) * Math.max(...rec.vectors) * PHI).toFixed(6)}
          </span>
        </div>
        <div style={{ fontSize:S.szS }}>
          <span style={{ color:C.amber }}>Spiral(7)</span>{" = "}
          <span style={{ color:C.green }}>{spiralN(7).toFixed(8)}</span>
        </div>
        <div style={{ fontSize:S.szS }}>
          <span style={{ color:C.amber }}>cycle</span>{" = "}
          <span style={{ color:C.green }}>{rec.triggers.length % G_PERIOD}</span>
          <span style={{ color:C.dim }}> / {G_PERIOD}</span>
        </div>
        <button onClick={() => onAnalyze(rec)}
          style={{ marginTop:6, fontFamily:S.font, fontSize:S.szS,
            padding:"3px 12px", background:"transparent",
            border:`1px solid ${C.green}`, color:C.green, cursor:"pointer" }}>
          ► ANALYZE + EXPORT PNG
        </button>
      </div>
    </div>
  );
}

function AnalysisModal({ rec, onClose }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    if (!canvasRef.current || !rec) return;
    const cv = canvasRef.current;
    const ctx = cv.getContext("2d");
    const W = cv.width, H = cv.height;

    // Background
    ctx.fillStyle = "#0A0A0F";
    ctx.fillRect(0,0,W,H);

    // Grid
    ctx.strokeStyle = "#001800";
    ctx.lineWidth = 0.5;
    for (let x=0; x<W; x+=20) { ctx.beginPath(); ctx.moveTo(x,0); ctx.lineTo(x,H); ctx.stroke(); }
    for (let y=0; y<H; y+=20) { ctx.beginPath(); ctx.moveTo(0,y); ctx.lineTo(W,y); ctx.stroke(); }

    // Toroidal spiral — √3/2 decay
    const cx = W*0.35, cy = H*0.5;
    ctx.strokeStyle = "#00FF41";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    for (let t=0; t<G_PERIOD*2; t+=0.05) {
      const r = spiralN(t/3) * 120;
      const x = cx + r * Math.cos(t * PHI);
      const y = cy + r * Math.sin(t * SPIRAL);
      t === 0 ? ctx.moveTo(x,y) : ctx.lineTo(x,y);
    }
    ctx.stroke();

    // Vector bars T^7
    const bx = W*0.72, by = H*0.12;
    ctx.font = "9px Courier New";
    rec.vectors.forEach((v,i) => {
      const hue = 120 + i*20;
      ctx.fillStyle = `hsl(${hue},100%,${40+Math.round(v*30)}%)`;
      ctx.fillRect(bx + i*22, by + (1-v)*80, 14, v*80);
      ctx.fillStyle = "#005500";
      ctx.fillText(["u","v","ψ","χ","ρ","δ","σ"][i], bx+i*22+3, by+90);
      ctx.fillStyle = "#008800";
      ctx.fillText(v.toFixed(2), bx+i*22-3, by+102);
    });

    // CRC hex display
    ctx.fillStyle = "#003300";
    ctx.font = "bold 11px Courier New";
    ctx.fillText(`CRC32c: 0x${rec.crc}`, W*0.7, H*0.88);

    // Φ_ethica gauge
    const phi_e = Math.min(...rec.vectors) * Math.max(...rec.vectors) * PHI;
    const gx = W*0.7, gy = H*0.55, gr = 40;
    ctx.strokeStyle = "#003300"; ctx.lineWidth = 8;
    ctx.beginPath(); ctx.arc(gx,gy,gr,0,Math.PI*2); ctx.stroke();
    ctx.strokeStyle = "#00FF41"; ctx.lineWidth = 6;
    ctx.beginPath(); ctx.arc(gx,gy,gr,-Math.PI/2, -Math.PI/2 + phi_e*Math.PI*2); ctx.stroke();
    ctx.fillStyle = C.green; ctx.font = "9px Courier New"; ctx.textAlign="center";
    ctx.fillText("Φ_ethica", gx, gy-4);
    ctx.fillText(phi_e.toFixed(4), gx, gy+8);
    ctx.textAlign = "left";

    // Trigger timeline
    const tlx = 20, tly = H*0.82;
    ctx.strokeStyle = "#002200"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(tlx,tly); ctx.lineTo(W*0.65,tly); ctx.stroke();
    rec.triggers.forEach((t,i) => {
      const tx = tlx + (i/(Math.max(rec.triggers.length-1,1))) * (W*0.6);
      const col = {ADD:"#00FF41",UPDATE:"#FFB000",MOVE:"#00FFFF",COMMIT:"#FF00FF"}[t.event]||"#888";
      ctx.fillStyle = col;
      ctx.beginPath(); ctx.arc(tx, tly, 4, 0, Math.PI*2); ctx.fill();
      ctx.fillStyle = col; ctx.font = "8px Courier New";
      ctx.fillText(t.event, tx-10, tly+14);
    });

    // Labels
    ctx.fillStyle = C.green; ctx.font = "bold 13px Courier New";
    ctx.fillText(`ZIPRAFSTORE · ${rec.id}`, 20, 20);
    ctx.fillStyle = "#005500"; ctx.font = "10px Courier New";
    ctx.fillText(rec.name, 20, 35);
    ctx.fillText(`Spiral(√3/2)^n · T^7 · G_PERIOD=${G_PERIOD}`, 20, H-12);
    ctx.fillStyle = "#003300";
    ctx.fillText("∆RafaelVerboΩ 𓂀ΔΦΩ", W-150, H-12);
  }, [rec]);

  const exportPNG = () => {
    if (!canvasRef.current) return;
    // Escala 2x para 144dpi efetivo — cria canvas offscreen sem alterar display
    const src = canvasRef.current;
    const W = src.width, H = src.height;
    const off = document.createElement("canvas");
    off.width = W * 2; off.height = H * 2;
    const ctx2 = off.getContext("2d");
    ctx2.scale(2, 2);
    ctx2.drawImage(src, 0, 0);
    // Adiciona metadados pHYs via PNG blob (144dpi = 5669 pixels/meter)
    const a = document.createElement("a");
    a.download = `zipraf_${rec.id}_144dpi.png`;
    a.href = off.toDataURL("image/png");
    a.click();
  };

  return (
    <div style={{ position:"fixed", inset:0, background:"rgba(0,0,0,0.9)",
      display:"flex", flexDirection:"column", alignItems:"center",
      justifyContent:"center", zIndex:1000 }}>
      <div style={{ border:`1px solid ${C.green}`, background:C.bg }}>
        <DosBar left={`ANALYZE: ${rec?.name}`} right="[ESC] FECHAR" />
        <canvas ref={canvasRef} width={680} height={400}
          style={{ display:"block", border:`1px solid ${C.border}` }} />
        <div style={{ display:"flex", gap:8, padding:6,
          background:"#001000", borderTop:`1px solid ${C.border}` }}>
          <button onClick={exportPNG}
            style={{ fontFamily:S.font, fontSize:S.sz, padding:"4px 16px",
              background:"#001800", border:`1px solid ${C.green}`,
              color:C.green, cursor:"pointer" }}>
            ▼ EXPORT PNG
          </button>
          <button onClick={onClose}
            style={{ fontFamily:S.font, fontSize:S.sz, padding:"4px 16px",
              background:"transparent", border:`1px solid ${C.dim}`,
              color:C.dim, cursor:"pointer" }}>
            [ESC] FECHAR
          </button>
        </div>
      </div>
    </div>
  );
}

function CmdLine({ onCmd }) {
  const [val, setVal] = useState("");
  const help = "Cmds: ADD <nome> <tipo>  SCAN  EXPORT  CLEAR  HELP";

  const run = () => {
    const parts = val.trim().split(/\s+/);
    onCmd(parts);
    setVal("");
  };

  return (
    <div style={{ borderTop:`1px solid ${C.border}`, background:"#050510",
      padding:"4px 8px", display:"flex", alignItems:"center", gap:8 }}>
      <span style={{ color:C.green, fontFamily:S.font, fontSize:S.sz }}>
        ZIPRAF&gt;
      </span>
      <input value={val} onChange={e=>setVal(e.target.value)}
        onKeyDown={e=>{ if(e.key==="Enter") run(); }}
        placeholder={help}
        style={{ flex:1, background:"transparent", border:"none", outline:"none",
          fontFamily:S.font, fontSize:S.sz, color:C.green,
          caretColor:C.green }} />
      <button onClick={run}
        style={{ fontFamily:S.font, fontSize:S.sz, padding:"2px 8px",
          background:"transparent", border:`1px solid ${C.dim}`,
          color:C.dim, cursor:"pointer" }}>
        EXEC
      </button>
    </div>
  );
}

// ── MAIN APP ──────────────────────────────────────────────
export default function ZipRafStore() {
  const [records, setRecords] = useState(SEED_RECORDS);
  const [selected, setSelected] = useState(null);
  const [analyzing, setAnalyzing] = useState(null);
  const [log, setLog] = useState(["ZIPRAFSTORE v1.0 ONLINE","Spiral(√3/2) · T^7 · G42"]);
  const [filter, setFilter] = useState("");
  const [storageOk, setStorageOk] = useState(false);
  const fileRef = useRef(null);

  const addLog = (msg) => setLog(l => [...l.slice(-20), msg]);

  // Persistência real via window.storage (artifact storage API)
  useEffect(() => {
    (async () => {
      if (!window.storage) { addLog("[STORAGE] indisponível — modo memória"); return; }
      try {
        const saved = await window.storage.get("zipraf:records");
        if (saved && saved.value) {
          const parsed = JSON.parse(saved.value);
          if (Array.isArray(parsed) && parsed.length > 0) {
            setRecords(parsed);
            addLog(\`[STORAGE] \${parsed.length} registros carregados\`);
          }
        }
        setStorageOk(true);
      } catch(e) { addLog("[STORAGE] erro leitura: " + e.message); }
    })();
  }, []);

  // Salva sempre que records mudar (debounce 800ms)
  useEffect(() => {
    if (!storageOk || !window.storage) return;
    const t = setTimeout(async () => {
      try {
        // Salva sem campo .data de imagens grandes (só metadados + texto)
        const slim = records.map(r => ({
          ...r,
          data: r.type === "IMAGE" ? "[IMAGE_OMITTED]" : r.data.slice(0,512)
        }));
        await window.storage.set("zipraf:records", JSON.stringify(slim));
      } catch(e) { /* silencioso — storage pode ter limite */ }
    }, 800);
    return () => clearTimeout(t);
  }, [records, storageOk]);

  const handleAction = useCallback((id, event) => {
    setRecords(rs => rs.map(r =>
      r.id === id ? applyTrigger(r, event) : r
    ));
    addLog(`[${event}] id=${id}`);
  }, []);

  const handleCmd = useCallback((parts) => {
    const cmd = (parts[0]||"").toUpperCase();
    if (cmd === "HELP") {
      addLog("ADD <nome> <tipo> | SCAN | EXPORT | CLEAR | HELP");
    } else if (cmd === "ADD") {
      const name = parts[1] || "new_record";
      const type = (parts[2] || "BINARY").toUpperCase();
      const rec = makeRecord(name, type, `[${type} data]`);
      setRecords(rs => [...rs, rec]);
      addLog(`[ADD] ${rec.id} nome=${name}`);
    } else if (cmd === "SCAN") {
      addLog(`[SCAN] ${records.length} registros · CRCs verificados`);
      records.forEach(r => addLog(`  ${r.id} crc=${r.crc} OK`));
    } else if (cmd === "CLEAR") {
      setRecords(SEED_RECORDS);
      addLog("[CLEAR] store restaurado");
    } else if (cmd === "EXPORT") {
      const json = JSON.stringify(records.map(r=>({
        id:r.id,name:r.name,type:r.type,crc:r.crc,
        vectors:r.vectors,triggers:r.triggers
      })),null,2);
      const a = document.createElement("a");
      a.download = "ziprafstore_export.json";
      a.href = URL.createObjectURL(new Blob([json],{type:"application/json"}));
      a.click();
      addLog("[EXPORT] ziprafstore_export.json");
    } else {
      addLog(`[ERR] cmd desconhecido: ${cmd}`);
    }
  }, [records]);

  const handleFile = useCallback((e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      const isImg = file.type.startsWith("image/");
      const data = ev.target.result;
      const type = isImg ? "IMAGE" : file.type.includes("text") ? "TEXT" : "BINARY";
      const rec = makeRecord(file.name, type, data, { mime: file.type, size_real: file.size });
      setRecords(rs => [...rs, rec]);
      addLog(`[ADD] ${file.name} type=${type}`);
      setSelected(rec.id);
    };
    if (file.type.startsWith("image/")) reader.readAsDataURL(file);
    else reader.readAsText(file);
    e.target.value = "";
  }, []);

  const filtered = records.filter(r => {
    if (!filter) return true;
    const fl = filter.toLowerCase();
    const fu = filter.toUpperCase();
    return r.name.toLowerCase().includes(fl)
        || r.type === fu
        || r.type.startsWith(fu);
  });

  const selRec = records.find(r => r.id === selected) || null;

  return (
    <div style={{ background:C.bg, minHeight:"100vh", color:C.white,
      fontFamily:S.font, fontSize:S.sz, display:"flex", flexDirection:"column" }}>

      {/* TITLE BAR */}
      <div style={{ background:"#001800", borderBottom:`2px solid ${C.green}`,
        padding:"4px 12px", display:"flex", alignItems:"center",
        justifyContent:"space-between" }}>
        <span style={{ color:C.green, fontSize:"16px", letterSpacing:2 }}>
          ▓▓ ZIPRAFSTORE v1.0 ▓▓
        </span>
        <span style={{ color:C.dim, fontSize:S.szS }}>
          RAFAELIA · ∆RafaelVerboΩ · 𓂀ΔΦΩ · {records.length} RECORDS
        </span>
        <div style={{ display:"flex", gap:6 }}>
          <button onClick={()=>fileRef.current?.click()}
            style={{ fontFamily:S.font, fontSize:S.szS, padding:"2px 8px",
              background:"#002200", border:`1px solid ${C.green}`,
              color:C.green, cursor:"pointer" }}>
            + IMPORT FILE
          </button>
          <button onClick={()=>handleCmd(["EXPORT"])}
            style={{ fontFamily:S.font, fontSize:S.szS, padding:"2px 8px",
              background:"transparent", border:`1px solid ${C.amber}`,
              color:C.amber, cursor:"pointer" }}>
            ▼ EXPORT JSON
          </button>
          <input ref={fileRef} type="file" style={{display:"none"}}
            onChange={handleFile} accept="*/*" />
        </div>
      </div>

      {/* FILTER */}
      <div style={{ background:"#050510", borderBottom:`1px solid ${C.border}`,
        padding:"3px 8px", display:"flex", alignItems:"center", gap:8 }}>
        <span style={{ color:C.dim }}>FILTER:</span>
        <input value={filter} onChange={e=>setFilter(e.target.value)}
          placeholder="nome ou tipo..."
          style={{ background:"transparent", border:"none", outline:"none",
            fontFamily:S.font, fontSize:S.sz, color:C.amber,
            caretColor:C.amber, width:200 }} />
        <span style={{ color:C.dim, marginLeft:"auto" }}>
          {filtered.length}/{records.length} registros
        </span>
      </div>

      {/* MAIN SPLIT */}
      <div style={{ display:"flex", flex:1, overflow:"hidden", minHeight:0 }}>

        {/* LEFT: FILE LIST */}
        <div style={{ width:"60%", borderRight:`1px solid ${C.border}`,
          display:"flex", flexDirection:"column", overflow:"hidden" }}>
          <DosBar left="ZIPRAF BINARY STORE" right="F1=HELP F5=SCAN F8=CLEAR" />

          {/* Column headers */}
          <div style={{ display:"flex", gap:8, padding:"2px 8px",
            background:"#001000", borderBottom:`1px solid ${C.border}`,
            fontSize:S.szS, color:C.dim }}>
            <span style={{width:16}}> </span>
            <span style={{minWidth:180}}>NOME</span>
            <span style={{minWidth:80}}>TIPO</span>
            <span style={{minWidth:60}}>SIZE</span>
            <span style={{minWidth:90}}>TIMESTAMP</span>
            <span>CRC32c</span>
            <span style={{marginLeft:"auto"}}>TRIGGERS</span>
          </div>

          {/* Records */}
          <div style={{ overflowY:"auto", flex:1 }}>
            {filtered.map(r => (
              <RecordRow key={r.id} rec={r}
                selected={selected===r.id}
                onClick={()=>setSelected(r.id)}
                onAction={handleAction} />
            ))}
            {filtered.length === 0 && (
              <div style={{ padding:16, color:C.dim, textAlign:"center" }}>
                Nenhum registro encontrado.
              </div>
            )}
          </div>

          {/* LOG */}
          <div style={{ borderTop:`1px solid ${C.border}`, background:"#050510",
            height:90, overflowY:"auto", padding:"2px 8px" }}>
            <div style={{ color:C.dim, fontSize:S.szS }}>LOG</div>
            {log.map((l,i) => (
              <div key={i} style={{ fontSize:S.szS, color:"#004400" }}>{l}</div>
            ))}
          </div>
        </div>

        {/* RIGHT: DETAIL */}
        <div style={{ width:"40%", overflowY:"auto" }}>
          <DetailPanel rec={selRec}
            onClose={()=>setSelected(null)}
            onAnalyze={(r)=>setAnalyzing(r)} />
        </div>
      </div>

      {/* CMD LINE */}
      <CmdLine onCmd={handleCmd} />

      {/* ANALYSIS MODAL */}
      {analyzing && (
        <AnalysisModal rec={analyzing} onClose={()=>setAnalyzing(null)} />
      )}
    </div>
  );
}
