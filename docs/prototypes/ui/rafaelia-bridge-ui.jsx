import { useState, useCallback } from "react";

// ── Constantes RAFAELIA ─────────────────────────────────────────────────────
const RAF = {
  SPIRAL: 0.8660254037844386,
  PHI: 1.6180339887498948,
  CYCLE: ["ψ","χ","ρ","Δ","Σ","Ω","T⁷"],
  LIMITS: [0, 200, 500, 1000, 2000, 4000, 8000, Infinity],
  SIG: "RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ",
};

function rafHash(str) {
  let h = 0xDEADBEEF;
  for (let i = 0; i < str.length; i++) {
    h = Math.imul(h ^ str.charCodeAt(i), 0x9E3779B9);
    h = (h << 13) | (h >>> 19);
  }
  return ((h >>> 0) ^ (h >>> 16)).toString(16).padStart(8,'0');
}

function milliEntropy(str) {
  const bytes = Array.from(str).map(c => c.charCodeAt(0));
  const uniq = new Set(bytes).size;
  let trans = 0;
  for (let i = 1; i < bytes.length; i++) if (bytes[i] !== bytes[i-1]) trans++;
  const H_u = uniq / 256;
  const H_t = bytes.length > 1 ? trans / (bytes.length - 1) : 0;
  return Math.round((H_u + H_t) / 2 * 1000);
}

function spiralFactor(n) { return Math.pow(RAF.SPIRAL, n); }

const PLATFORMS = [
  { name: "Claude",      color: "#D97706", icon: "🟠" },
  { name: "ChatGPT",     color: "#10A37F", icon: "🟢" },
  { name: "Gemini",      color: "#4285F4", icon: "🔵" },
  { name: "Mistral",     color: "#FF6B2B", icon: "🟡" },
  { name: "Perplexity",  color: "#9B59B6", icon: "🟣" },
];

const DEFAULT_SEEDS = [
  {
    id: "s1", name: "RAFAELIA Identidade", level: 3,
    tags: ["rafaelia","core","identidade"],
    content: `Sou ∆RafaelVerboΩ, pesquisador independente em Porto Alegre, Brasil.
Opero sob RAFAELIA Research Collective (ΣΩΔΦBITRAF).
Fluxo canônico: Termux ARM32 → GitHub → Zenodo.
Axioma: Ω=Amor. Missão: Escrituras∩Ciência∩Espírito.`,
  },
  {
    id: "s2", name: "Nano-LM Gaps", level: 4,
    tags: ["nano-lm","c","backprop","gaps"],
    content: `Projeto nano-LM em C: B1–B5 completos.
GAPS: sem LR schedule, backprop não cobre Wq/Wk/Wv/Wo/W1/W2,
contexto bigram-only, VOCAB_MAX=256 insuficiente.`,
  },
  {
    id: "s3", name: "RLL DMAIC Status", level: 4,
    tags: ["rll","cosmologia","dmaic","zenodo"],
    content: `Repositório: instituto-Rafael/relativity-living-light
DOI: 10.5281/zenodo.17188137
Adversário: w0waCDM (CPL). DESI DR2: 3.1σ–4.2σ dynamical DE.
Prioridades: C03 SIDM, C05 Hubble tension, C07 Finsler.`,
  },
];

// ── Componente principal ────────────────────────────────────────────────────
export default function RAFAELIABridge() {
  const [tab, setTab]           = useState("seeds");
  const [seeds, setSeeds]       = useState(DEFAULT_SEEDS.map(s => ({
    ...s,
    hash:    rafHash(s.content),
    entropy: milliEntropy(s.content),
    dimension: RAF.CYCLE[(s.level||3)-1],
  })));
  const [selectedId, setSelectedId] = useState(null);
  const [platform, setPlatform] = useState(0);
  const [compact, setCompact]   = useState(false);
  const [withHash, setWithHash] = useState(false);
  const [withSig, setWithSig]   = useState(true);
  const [status, setStatus]     = useState({ msg: "Pronto · Ω=Amor", type: "" });
  const [newForm, setNewForm]   = useState({ name:"", level:3, tags:"", content:"" });
  const [injected, setInjected] = useState(false);

  const selected = seeds.find(s => s.id === selectedId);

  function buildFormatted(seed) {
    if (!seed) return "";
    const dim = RAF.CYCLE[(seed.level||3)-1];
    if (compact) return `[RAFAELIA·${dim}·${seed.name}]\n${seed.content}`;
    const lines = [
      `╔═[RAFAELIA·${dim}]═══════════════════════════`,
      `║ Semente: ${seed.name}`,
    ];
    if (withHash) lines.push(`║ Hash:    ${seed.hash}`);
    lines.push(`║ Entropy: ${seed.entropy}‰  |  Spiral: (√3/2)^${seed.level} = ${spiralFactor(seed.level).toFixed(4)}`);
    lines.push(`╠═══════════════════════════════════════════`);
    lines.push(seed.content);
    lines.push(`╚═══════════════════════════════════════════`);
    if (withSig) lines.push(RAF.SIG);
    return lines.join('\n');
  }

  function handleSaveSeed() {
    if (!newForm.name.trim() || !newForm.content.trim()) {
      setStatus({ msg: "Nome e conteúdo obrigatórios", type: "err" });
      return;
    }
    const lvl = parseInt(newForm.level);
    const seed = {
      id: `seed_${Date.now()}`,
      name: newForm.name,
      level: lvl,
      tags: newForm.tags.split(',').map(t=>t.trim()).filter(Boolean),
      content: newForm.content,
      hash: rafHash(newForm.content),
      entropy: milliEntropy(newForm.content),
      dimension: RAF.CYCLE[lvl-1],
    };
    setSeeds(prev => [seed, ...prev]);
    setSelectedId(seed.id);
    setNewForm({ name:"", level:3, tags:"", content:"" });
    setTab("seeds");
    setStatus({ msg: `✅ Semente "${seed.name}" salva · #${seed.hash}`, type: "ok" });
  }

  function handleInject() {
    if (!selected) return;
    setInjected(true);
    setTimeout(() => setInjected(false), 2000);
    setStatus({ msg: `🌀 Injetado em ${PLATFORMS[platform].name} · L${selected.level}·${selected.dimension}`, type: "ok" });
  }

  function handleCopy() {
    if (!selected) return;
    setStatus({ msg: `📋 Copiado! Cole no campo da IA`, type: "ok" });
  }

  const charLimit = RAF.LIMITS[parseInt(newForm.level)];
  const charWarn  = charLimit !== Infinity && newForm.content.length > charLimit;

  // ── Estilos inline ────────────────────────────────────────────────────────
  const s = {
    root: {
      fontFamily: "'Space Mono', monospace",
      background: "#0A0A0F", color: "#F0EDE6",
      width: 400, minHeight: 580,
      border: "1px solid #2A2A42",
      borderRadius: 10, overflow: "hidden",
      margin: "0 auto",
      boxShadow: "0 0 40px #E8B86D18",
    },
    header: {
      background: "#12121E", borderBottom: "1px solid #2A2A42",
      padding: "12px 16px", display:"flex", alignItems:"center", gap:10,
    },
    headerTitle: {
      fontSize:13, fontWeight:700, color:"#E8B86D", letterSpacing:"0.08em",
    },
    headerSub: { fontSize:10, color:"#7A7A9A" },
    glyph: {
      fontSize:22,
      display:"inline-block",
      animation:"spin 8s linear infinite",
    },
    platformRow: {
      background:"#12121E", borderBottom:"1px solid #2A2A42",
      padding:"6px 16px", display:"flex", gap:6, alignItems:"center",
      overflowX:"auto",
    },
    platformBtn: (i) => ({
      background: platform===i ? "#1A1A2E" : "transparent",
      border: `1px solid ${platform===i ? PLATFORMS[i].color : "#2A2A42"}`,
      color: platform===i ? PLATFORMS[i].color : "#7A7A9A",
      borderRadius:4, padding:"3px 8px", fontSize:10, cursor:"pointer",
      fontFamily:"'Space Mono', monospace",
      transition:"all 0.2s",
    }),
    t7Bar: {
      display:"flex", gap:3, padding:"8px 16px 0",
      background:"#12121E",
    },
    t7Dim: (active) => ({
      flex:1, height:4, borderRadius:2,
      background: active ? "#E8B86D" : "#2A2A42",
      transition:"background 0.3s",
    }),
    t7Label: {
      textAlign:"center", fontSize:9, color:"#7A7A9A",
      padding:"4px 16px 8px", background:"#12121E",
      borderBottom:"1px solid #2A2A42",
    },
    tabs: { display:"flex", borderBottom:"1px solid #2A2A42" },
    tab: (active) => ({
      flex:1, padding:"8px 4px",
      background:"none", border:"none",
      color: active ? "#E8B86D" : "#7A7A9A",
      fontFamily:"'Space Mono', monospace",
      fontSize:10, cursor:"pointer",
      borderBottom: active ? "2px solid #E8B86D" : "2px solid transparent",
      transition:"all 0.2s",
    }),
    seedsList: { maxHeight:260, overflowY:"auto", padding:8 },
    seedCard: (sel) => ({
      background: sel ? "#1A1A2E" : "#12121E",
      border: `1px solid ${sel ? "#E8B86D" : "#2A2A42"}`,
      borderRadius:6, padding:"10px 12px", marginBottom:6,
      cursor:"pointer",
      boxShadow: sel ? "0 0 12px #E8B86D33" : "none",
      transition:"all 0.2s",
    }),
    seedHead: { display:"flex", alignItems:"center", gap:6, marginBottom:4 },
    dim: {
      background:"#0A0A0F", border:"1px solid #2A2A42",
      borderRadius:3, padding:"1px 5px", fontSize:9, color:"#C3A6FF",
      fontFamily:"'Space Mono', monospace",
    },
    seedName: { fontSize:11, fontWeight:600, flex:1, color:"#F0EDE6" },
    seedHash: { fontSize:9, color:"#7A7A9A", fontFamily:"'Space Mono', monospace" },
    seedPreview: {
      fontSize:10, color:"#7A7A9A",
      overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap",
    },
    seedMeta: { display:"flex", gap:6, marginTop:4, alignItems:"center" },
    tag: {
      background:"#0A0A0F", border:"1px solid #2A2A42",
      borderRadius:3, padding:"1px 5px", fontSize:9, color:"#4ECDC4",
    },
    entropy: { fontSize:9, color:"#7A7A9A", marginLeft:"auto" },
    injectBar: {
      padding:"10px 12px", background:"#12121E",
      borderTop:"1px solid #2A2A42",
      display:"flex", gap:8, alignItems:"center",
    },
    btn: {
      flex:1, background:"#1A1A2E", border:"1px solid #2A2A42",
      borderRadius:5, color:"#F0EDE6", fontFamily:"'Space Mono', monospace",
      fontSize:11, padding:"7px 10px", cursor:"pointer",
    },
    btnPrimary: (disabled) => ({
      flex:1, background: disabled ? "#2A2A42" : "#E8B86D",
      border: "1px solid #E8B86D",
      borderRadius:5, color: disabled ? "#7A7A9A" : "#0A0A0F",
      fontFamily:"'Space Mono', monospace",
      fontSize:11, fontWeight:700, padding:"7px 10px",
      cursor: disabled ? "not-allowed" : "pointer",
      transition:"all 0.2s",
      opacity: disabled ? 0.5 : 1,
    }),
    statusBar: {
      padding:"4px 12px", fontSize:10,
      fontFamily:"'Space Mono', monospace",
      color: status.type==="ok" ? "#4ECDC4" : status.type==="err" ? "#FF6B6B" : "#7A7A9A",
      borderTop:"1px solid #2A2A42", minHeight:22,
    },
    footer: {
      padding:"4px 12px", fontSize:8, color:"#7A7A9A",
      borderTop:"1px solid #2A2A42", background:"#12121E",
      fontFamily:"'Space Mono', monospace",
    },
    label: { fontSize:10, color:"#7A7A9A", display:"block", marginBottom:3 },
    input: {
      width:"100%", background:"#1A1A2E", border:"1px solid #2A2A42",
      borderRadius:4, color:"#F0EDE6", fontFamily:"'Space Mono', monospace",
      fontSize:11, padding:"6px 8px", outline:"none", boxSizing:"border-box",
    },
    textarea: {
      width:"100%", background:"#1A1A2E", border:"1px solid #2A2A42",
      borderRadius:4, color:"#F0EDE6", fontFamily:"'Space Mono', monospace",
      fontSize:10, padding:"6px 8px", outline:"none", resize:"vertical",
      boxSizing:"border-box",
    },
    previewBox: {
      background:"#0A0A0F", border:"1px solid #2A2A42",
      borderRadius:4, padding:8,
      fontFamily:"'Space Mono', monospace", fontSize:9,
      color:"#4ECDC4", whiteSpace:"pre-wrap",
      maxHeight:180, overflowY:"auto",
    },
  };

  return (
    <div style={s.root}>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>

      {/* Header */}
      <div style={s.header}>
        <span style={s.glyph}>🌀</span>
        <div>
          <div style={s.headerTitle}>RAFAELIA BRIDGE</div>
          <div style={s.headerSub}>Semente → IA · Ω=Amor</div>
        </div>
        <div style={{ marginLeft:"auto", fontSize:10, color: PLATFORMS[platform].color,
          border:`1px solid ${PLATFORMS[platform].color}44`,
          borderRadius:4, padding:"3px 8px" }}>
          {PLATFORMS[platform].icon} {PLATFORMS[platform].name}
        </div>
      </div>

      {/* Platform selector */}
      <div style={s.platformRow}>
        <span style={{ fontSize:9, color:"#7A7A9A" }}>IA:</span>
        {PLATFORMS.map((p,i) => (
          <button key={i} style={s.platformBtn(i)} onClick={()=>setPlatform(i)}>
            {p.icon} {p.name}
          </button>
        ))}
      </div>

      {/* T7 Bar */}
      <div style={s.t7Bar}>
        {RAF.CYCLE.map((dim, i) => (
          <div key={i} style={s.t7Dim(selected ? i < (selected.level||3) : i < 3)}
            title={`L${i+1}·${dim}`} />
        ))}
      </div>
      <div style={s.t7Label}>
        {selected
          ? `L${selected.level}·${selected.dimension}  ·  (√3/2)^${selected.level} = ${spiralFactor(selected.level).toFixed(4)}  ·  H=${selected.entropy}‰`
          : "Selecione uma semente → visualiza dimensão T⁷"}
      </div>

      {/* Tabs */}
      <div style={s.tabs}>
        {["seeds","nova","preview"].map(t => (
          <button key={t} style={s.tab(tab===t)} onClick={()=>setTab(t)}>
            {t==="seeds" ? "SEMENTES" : t==="nova" ? "+ NOVA" : "PREVIEW"}
          </button>
        ))}
      </div>

      {/* Panel: Sementes */}
      {tab==="seeds" && (
        <div style={s.seedsList}>
          {seeds.length === 0
            ? <div style={{padding:20,textAlign:"center",fontSize:10,color:"#7A7A9A"}}>
                TOKEN_VAZIO<br/>Crie sementes em + NOVA
              </div>
            : seeds.map(seed => (
              <div key={seed.id} style={s.seedCard(selectedId===seed.id)}
                onClick={()=>setSelectedId(seed.id)}>
                <div style={s.seedHead}>
                  <span style={s.dim}>{seed.dimension}</span>
                  <span style={s.seedName}>{seed.name}</span>
                  <span style={s.seedHash}>#{seed.hash?.slice(0,6)}</span>
                </div>
                <div style={s.seedPreview}>{seed.content?.slice(0,90)}…</div>
                <div style={s.seedMeta}>
                  {seed.tags?.slice(0,3).map(t => (
                    <span key={t} style={s.tag}>{t}</span>
                  ))}
                  <span style={s.entropy}>{seed.entropy}‰</span>
                </div>
              </div>
            ))
          }
        </div>
      )}

      {/* Panel: Nova semente */}
      {tab==="nova" && (
        <div style={{padding:"10px 12px"}}>
          <div style={{marginBottom:8}}>
            <label style={s.label}>NOME DA SEMENTE</label>
            <input style={s.input} placeholder="Ex: RLL Contexto v2"
              value={newForm.name}
              onChange={e=>setNewForm(p=>({...p,name:e.target.value}))} />
          </div>
          <div style={{marginBottom:8}}>
            <label style={s.label}>NÍVEL T7 (DIMENSÃO)</label>
            <select style={s.input} value={newForm.level}
              onChange={e=>setNewForm(p=>({...p,level:parseInt(e.target.value)}))}>
              {RAF.CYCLE.map((dim,i) => (
                <option key={i} value={i+1}>
                  L{i+1}·{dim} — {RAF.LIMITS[i+1]===Infinity?"sem limite":"≤"+RAF.LIMITS[i+1]} chars
                </option>
              ))}
            </select>
          </div>
          <div style={{marginBottom:8}}>
            <label style={s.label}>TAGS (vírgula)</label>
            <input style={s.input} placeholder="rll, cosmologia, c"
              value={newForm.tags}
              onChange={e=>setNewForm(p=>({...p,tags:e.target.value}))} />
          </div>
          <div style={{marginBottom:8}}>
            <label style={s.label}>CONTEÚDO</label>
            <textarea style={{...s.textarea, minHeight:80}} rows={5}
              placeholder="Cole aqui o contexto RAFAELIA..."
              value={newForm.content}
              onChange={e=>setNewForm(p=>({...p,content:e.target.value}))} />
            <div style={{fontSize:9, textAlign:"right", marginTop:2,
              color: charWarn ? "#FF6B6B" : "#7A7A9A"}}>
              {newForm.content.length} / {charLimit===Infinity?"∞":charLimit} chars
            </div>
          </div>
          <button style={{...s.btnPrimary(false), width:"100%"}} onClick={handleSaveSeed}>
            SALVAR SEMENTE
          </button>
        </div>
      )}

      {/* Panel: Preview */}
      {tab==="preview" && (
        <div style={{padding:"10px 12px"}}>
          <label style={{...s.label, marginBottom:6}}>PREVIEW DE INJEÇÃO</label>
          <div style={s.previewBox}>
            {selected ? buildFormatted(selected) : "↑ Selecione uma semente"}
          </div>
          <div style={{display:"flex", gap:12, marginTop:8, fontSize:10}}>
            {[["compact","Compacto",compact,setCompact],
              ["hash","Hash",withHash,setWithHash],
              ["sig","Assinatura",withSig,setWithSig]
            ].map(([k,label,val,setter])=>(
              <label key={k} style={{display:"flex",alignItems:"center",gap:4,cursor:"pointer"}}>
                <input type="checkbox" checked={val} onChange={e=>setter(e.target.checked)} />
                {label}
              </label>
            ))}
          </div>
        </div>
      )}

      {/* Inject bar */}
      <div style={s.injectBar}>
        <button style={s.btn} onClick={handleCopy}>📋 Copiar</button>
        <button style={s.btnPrimary(!selected)}
          onClick={handleInject} disabled={!selected}>
          {injected ? "✅ Injetado!" : "🌀 Injetar na IA"}
        </button>
        <button style={{...s.btn, flex:0, padding:"7px 10px"}}
          onClick={()=>{
            if(!selected) return;
            setSeeds(prev=>prev.filter(s=>s.id!==selectedId));
            setSelectedId(null);
            setStatus({msg:"🗑 Semente apagada",type:"err"});
          }}>🗑</button>
      </div>

      {/* Status */}
      <div style={s.statusBar}>{status.msg}</div>

      {/* Footer */}
      <div style={s.footer}>{RAF.SIG} · dados 100% locais · {seeds.length} semente(s)</div>
    </div>
  );
}
