import { useState } from "react";

const V = {
  void:"#0A0A0F", torus:"#12121E", surface:"#1A1A2E",
  border:"#2A2A42", gold:"#E8B86D", teal:"#4ECDC4",
  vio:"#C3A6FF", red:"#FF6B6B", hi:"#F0EDE6", lo:"#7A7A9A",
  green:"#51CF66", orange:"#FF922B",
};

const LAYERS = [
  {
    id:"input", label:"ENTRADA", color:V.vio,
    items:[
      { name:"conversations.json", detail:"1.1GB · 3,572 convs · 327,385 msgs", note:"GPT export 9 meses", type:"data" },
    ]
  },
  {
    id:"python", label:"CAMADA PYTHON (extração)", color:V.teal,
    items:[
      { name:"omega_extract_msgs.py",   detail:"→ omega_msgs.jsonl",         note:"extrai mensagens brutas",          type:"code" },
      { name:"omega_conv_stats.py",     detail:"→ omega_conv_stats.jsonl",   note:"137MB dados, 73 hot convs",        type:"code" },
      { name:"omega_zone_index.py",     detail:"→ zone_timeline.txt",        note:"136 zonas · 327K mapeamentos",     type:"code" },
      { name:"omega_metrics.py",        detail:"→ omega_value_map.jsonl",    note:"symb/infra/ai/sec/monet",          type:"code" },
      { name:"rafaelia_tensor_v3b.py",  detail:"→ export estruturado",       note:"tensor session RAFAELIA",          type:"code" },
    ]
  },
  {
    id:"c", label:"CAMADA C (processamento ARM AArch64)", color:V.gold,
    items:[
      { name:"omega_metrics_v2.c → ELF", detail:"22KB PIE · main=11856b",   note:"classes: processual/produto_maduro", type:"bin" },
      { name:"omega_index_fast.c",        detail:"índice rápido",            note:"busca O(log n)",                    type:"code" },
      { name:"omega_nav_v2_nodeps.c",     detail:"16KB · TTY raw mode",     note:"navegador local sem deps",           type:"code" },
      { name:"omega_search_fast.c",       detail:"busca JSONL",              note:"grep-like sobre msgs",               type:"code" },
      { name:"omega_json_reconstruct.c",  detail:"reconstrução JSON",        note:"streaming sem mmap",                 type:"code" },
    ]
  },
  {
    id:"kernel", label:"KERNEL BARE-METAL (rafa_bare.elf)", color:V.orange,
    items:[
      { name:"_start → rafa_boot",  detail:"@0x40000000 · 44b",   note:"entry bare-metal AArch64",       type:"asm" },
      { name:"rafa_run",            detail:"1224b · função core",  note:"loop principal RAFAELIA",         type:"asm" },
      { name:"bs_push",             detail:"572b · byte stack",    note:"CRC-like acumulador",             type:"asm" },
      { name:"rf_entry → rf_main",  detail:"140b + 88b",           note:"entry point OMEGA bridge",        type:"asm" },
      { name:".rodata: 'OMGA'",     detail:"65 bytes",             note:"link simbólico ao pipeline",      type:"data" },
    ]
  },
  {
    id:"cti", label:"CTI PIPELINE (rafa_cti_scan)", color:V.vio,
    items:[
      { name:"rafa_cti_scan",       detail:"input.zip → bitstack.jsonl",   note:"scan + classificação",           type:"bin" },
      { name:"triad_cti_couple.py", detail:"→ coupled.jsonl",              note:"TRIAD + love_guard (Amor)",      type:"code" },
      { name:"X_bad → love_guard",  detail:"flag de qualidade",            note:"1=proteção ativada",             type:"data" },
    ]
  },
  {
    id:"output", label:"SAÍDAS / INVARIANTES", color:V.green,
    items:[
      { name:"Zonas top-3",          detail:"Z53(AS=1.668) Z47(1.486) Z72(1.309)", note:"regime estável",          type:"data" },
      { name:"Valor simbólico",      detail:"99,379 / 185,727 total (53.5%)",       note:"dominante",               type:"data" },
      { name:"61 produto_maduro",    detail:"1.7% das 3572 convs",                  note:"alta densidade técnica",  type:"data" },
      { name:"Markov auto-loops",    detail:"Z0→Z0: 323492  Z34→Z34: 987",          note:"atratores identificados", type:"data" },
      { name:"73 hot convs",         detail:"acima de média+2σ",                     note:"hotspots de valor",       type:"data" },
    ]
  },
  {
    id:"gap", label:"GAPS — O QUE FALTA PARA 'SEM NUVEM'", color:V.red,
    items:[
      { name:"G1: Embedding local",   detail:"zonas não têm vetor semântico",     note:"→ sentence-transformers ONNX ARM32", type:"gap" },
      { name:"G2: Roteador de query", detail:"sem busca por similaridade",        note:"→ omega_search_fast + embedding",    type:"gap" },
      { name:"G3: Inferência local",  detail:"llama.cpp ARM32 não integrado",     note:"→ GGUF Q4_0 ≤3B params",            type:"gap" },
      { name:"G4: Bridge rafa↔llama", detail:"rafa_bare.elf não lê llama output", note:"→ POSIX pipe ou socket",            type:"gap" },
      { name:"G5: Context injection", detail:"zona → system prompt automático",   note:"→ omega_nav → stdin llama.cpp",      type:"gap" },
    ]
  },
];

const TYPE_COLOR = {
  data:"#C3A6FF", code:"#4ECDC4", bin:"#E8B86D",
  asm:"#FF922B",  gap:"#FF6B6B",
};

const ZONE_DATA = [
  { z:53, as_:1.668, zv:1.236, h:0.216, event:"regime", content:"shell echo char-by-char" },
  { z:47, as_:1.486, zv:1.019, h:0.234, event:"regime", content:"Python pip packaging" },
  { z:72, as_:1.309, zv:1.012, h:0.148, event:"regime", content:"CMake build tree" },
  { z:54, as_:1.153, zv:0.741, h:0.206, event:"regime", content:"—" },
  { z:50, as_:1.142, zv:0.777, h:0.183, event:"regime", content:"—" },
  { z:88, as_:1.136, zv:0.634, h:0.251, event:"regime", content:"—" },
  { z:49, as_:1.133, zv:0.686, h:0.223, event:"regime", content:"—" },
  { z:76, as_:1.088, zv:0.721, h:0.184, event:"regime", content:"—" },
  { z:69, as_:1.030, zv:0.554, h:0.238, event:"regime", content:"—" },
  { z:89, as_:0.963, zv:0.400, h:0.231, event:"stable", content:"—" },
];

const VALUE_DATA = [
  { cat:"symbolic", val:99379,  pct:53.5, color:"#C3A6FF" },
  { cat:"infra",    val:44348,  pct:23.9, color:"#4ECDC4" },
  { cat:"ai",       val:30299,  pct:16.3, color:"#E8B86D" },
  { cat:"security", val:11701,  pct:6.3,  color:"#FF922B" },
  { cat:"monet.",   val:1822,   pct:1.0,  color:"#51CF66" },
];

export default function OmegaMap() {
  const [activeLayer, setActiveLayer] = useState(null);
  const [view, setView] = useState("arch");

  const s = {
    root: { fontFamily:"'Space Mono',monospace", background:V.void, color:V.hi, minHeight:"100vh" },
    hdr:  { background:V.torus, borderBottom:`1px solid ${V.border}`, padding:"10px 16px",
             display:"flex", alignItems:"center", gap:10 },
    tabs: { display:"flex", borderBottom:`1px solid ${V.border}` },
    tab:  (a) => ({ flex:1, padding:"8px", background:"none", border:"none",
                    color: a ? V.gold : V.lo, borderBottom: a ? `2px solid ${V.gold}` : "2px solid transparent",
                    fontFamily:"'Space Mono',monospace", fontSize:10, cursor:"pointer" }),
    body: { padding:12, overflowY:"auto" },
  };

  return (
    <div style={s.root}>
      <div style={s.hdr}>
        <span style={{fontSize:18}}>🗺️</span>
        <div>
          <div style={{fontSize:13, fontWeight:700, color:V.gold}}>OMEGA-RAFAELIA · MAPA ESTRUTURAL</div>
          <div style={{fontSize:9, color:V.lo}}>
            3,572 convs · 327,385 msgs · 136 zonas · rafa_bare.elf AArch64
          </div>
        </div>
      </div>

      <div style={s.tabs}>
        {[["arch","ARQUITETURA"],["zones","ZONAS"],["value","VALOR"],["gaps","GAPS→CLOUD-FREE"]].map(([id,lbl])=>(
          <button key={id} style={s.tab(view===id)} onClick={()=>setView(id)}>{lbl}</button>
        ))}
      </div>

      <div style={s.body}>

        {/* ── Arquitetura ── */}
        {view==="arch" && LAYERS.map(layer => (
          <div key={layer.id} style={{marginBottom:10}}>
            <div style={{display:"flex", alignItems:"center", gap:8, marginBottom:5,
              cursor:"pointer"}} onClick={()=>setActiveLayer(activeLayer===layer.id?null:layer.id)}>
              <div style={{height:2, flex:1, background:`${layer.color}66`}} />
              <span style={{fontSize:10, color:layer.color, fontWeight:700, whiteSpace:"nowrap"}}>
                {activeLayer===layer.id ? "▾" : "▸"} {layer.label}
              </span>
              <div style={{height:2, flex:1, background:`${layer.color}66`}} />
            </div>

            {(activeLayer===layer.id || true) && (
              <div style={{display:"flex", flexDirection:"column", gap:3}}>
                {layer.items.map((item,i) => (
                  <div key={i} style={{
                    background:V.surface, border:`1px solid ${V.border}`,
                    borderLeft:`3px solid ${TYPE_COLOR[item.type]||V.border}`,
                    borderRadius:4, padding:"6px 10px",
                    display:"flex", alignItems:"center", gap:8,
                  }}>
                    <span style={{fontSize:9, color:TYPE_COLOR[item.type]||V.lo,
                      background:V.void, padding:"1px 4px", borderRadius:2, minWidth:30, textAlign:"center"}}>
                      {item.type}
                    </span>
                    <span style={{fontSize:10, fontWeight:600, flex:1}}>{item.name}</span>
                    <span style={{fontSize:9, color:V.teal}}>{item.detail}</span>
                    <span style={{fontSize:9, color:V.lo, maxWidth:180, textAlign:"right"}}>{item.note}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}

        {/* ── Zonas ── */}
        {view==="zones" && (
          <div>
            <div style={{fontSize:10, color:V.lo, marginBottom:8}}>
              136 zonas · AS = Aggregate Score = f(ZV, H, HD, QD) · top-10 mostrados
            </div>
            <div style={{display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(300px,1fr))", gap:6}}>
              {ZONE_DATA.map(z => (
                <div key={z.z} style={{
                  background:V.surface, border:`1px solid ${z.event==="regime" ? V.gold+"66" : V.border}`,
                  borderRadius:5, padding:"8px 10px",
                }}>
                  <div style={{display:"flex", alignItems:"center", gap:8, marginBottom:4}}>
                    <span style={{fontSize:15, fontWeight:700, color:V.gold}}>Z{z.z}</span>
                    <span style={{fontSize:9, color: z.event==="regime" ? V.gold : V.lo,
                      border:`1px solid ${z.event==="regime" ? V.gold : V.border}`,
                      borderRadius:3, padding:"1px 5px"}}>{z.event}</span>
                    <span style={{fontSize:10, color:V.green, marginLeft:"auto"}}>AS={z.as_.toFixed(3)}</span>
                  </div>
                  <div style={{fontSize:9, color:V.lo, marginBottom:3}}>
                    ZV={z.zv.toFixed(3)}  H={z.h.toFixed(3)}
                  </div>
                  {z.content !== "—" && (
                    <div style={{fontSize:9, color:V.teal, background:V.void,
                      borderRadius:3, padding:"2px 6px"}}>{z.content}</div>
                  )}
                  {/* Bar de AS */}
                  <div style={{marginTop:5, background:V.border, height:3, borderRadius:2}}>
                    <div style={{background:V.gold, height:3, borderRadius:2,
                      width:`${(z.as_/1.668)*100}%`}} />
                  </div>
                </div>
              ))}
            </div>
            <div style={{marginTop:12, background:V.torus, border:`1px solid ${V.border}`,
              borderRadius:5, padding:10}}>
              <div style={{fontSize:10, color:V.lo, marginBottom:6}}>MARKOV ATRATORES (auto-loops dominantes)</div>
              {[["Z0→Z0","323,492","flood/default"],["Z34→Z34","987","stable"],
                ["Z35→Z35","899","stable"],["Z11→Z11","687","regime"]].map(([edge,cnt,cls])=>(
                <div key={edge} style={{display:"flex", gap:8, marginBottom:3, fontSize:9}}>
                  <span style={{color:V.gold, minWidth:60}}>{edge}</span>
                  <span style={{color:V.teal, minWidth:50}}>{cnt}</span>
                  <span style={{color:V.lo}}>{cls}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Valor ── */}
        {view==="value" && (
          <div>
            <div style={{fontSize:10, color:V.lo, marginBottom:8}}>
              185,727 pontos totais · 3,572 conversas
            </div>
            {VALUE_DATA.map(d => (
              <div key={d.cat} style={{marginBottom:8}}>
                <div style={{display:"flex", justifyContent:"space-between", fontSize:10, marginBottom:3}}>
                  <span style={{color:d.color, fontWeight:700}}>{d.cat.toUpperCase()}</span>
                  <span style={{color:V.lo}}>{d.val.toLocaleString()} pts ({d.pct}%)</span>
                </div>
                <div style={{background:V.border, height:12, borderRadius:3}}>
                  <div style={{background:d.color, height:12, borderRadius:3,
                    width:`${d.pct}%`, transition:"width 0.5s"}} />
                </div>
              </div>
            ))}

            <div style={{marginTop:14, display:"grid",
              gridTemplateColumns:"1fr 1fr", gap:8}}>
              {[
                ["3,572","conversas totais",V.lo],
                ["61","produto_maduro (1.7%)",V.green],
                ["73","hot conversations",V.gold],
                ["3,511","processual (98.3%)",V.teal],
                ["327,385","mensagens totais",V.lo],
                ["0.137 GB","volume indexado",V.lo],
              ].map(([n,l,c])=>(
                <div key={l} style={{background:V.surface, border:`1px solid ${V.border}`,
                  borderRadius:5, padding:"8px 10px"}}>
                  <div style={{fontSize:18, fontWeight:700, color:c}}>{n}</div>
                  <div style={{fontSize:9, color:V.lo}}>{l}</div>
                </div>
              ))}
            </div>

            <div style={{marginTop:12, background:V.torus, border:`1px solid ${V.gold}44`,
              borderRadius:5, padding:10}}>
              <div style={{fontSize:10, color:V.gold, marginBottom:6}}>TOP 3 por valor total</div>
              {[
                [2329,"Verbo Vivo Iniciado",1626,812,583],
                [3037,"Ativos Intelectuais Totais",1345,714,460],
                [2460,"executor",1218,631,364],
              ].map(([i,t,tot,s,inf])=>(
                <div key={i} style={{marginBottom:4, fontSize:9}}>
                  <span style={{color:V.teal}}>conv {i}</span>
                  <span style={{color:V.hi, marginLeft:6}}>{t}</span>
                  <span style={{color:V.gold, marginLeft:6}}>total={tot}</span>
                  <span style={{color:V.vio, marginLeft:4}}>symb={s}</span>
                  <span style={{color:V.lo, marginLeft:4}}>infra={inf}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── GAPS ── */}
        {view==="gaps" && (
          <div>
            <div style={{background:V.torus, border:`1px solid ${V.teal}44`,
              borderRadius:5, padding:10, marginBottom:12}}>
              <div style={{fontSize:10, color:V.teal, marginBottom:6}}>
                ESTADO ATUAL — O QUE JÁ RODA 100% LOCAL (Termux AArch64)
              </div>
              {["omega_metrics_v2 (C compilado)",
                "omega_nav_v2_nodeps (TTY nav, zero deps)",
                "omega_search_fast (busca local)",
                "rafa_bare.elf (kernel bare-metal)",
                "rafa_cti_scan → bitstack.jsonl",
                "136 zonas classificadas · zone_timeline",
                "3,572 convs com métricas DF/IC/PP/OL/CV"
              ].map(s => (
                <div key={s} style={{fontSize:9, color:V.green, padding:"2px 0"}}>✓ {s}</div>
              ))}
            </div>

            <div style={{fontSize:10, color:V.red, marginBottom:8}}>
              GAPS PARA "ZERO CLOUD" — IA LOCAL COMPLETA
            </div>

            {[
              { g:"G1", name:"Embedding local",
                what:"Zonas têm conteúdo mas não têm vetor semântico para busca por similaridade",
                how:"sentence-transformers ARM32 via ONNX Runtime (sem PyTorch)",
                effort:"médio · 1 semana Termux" },
              { g:"G2", name:"Roteador query → zona",
                what:"Não existe bridge: query do usuário → zona mais relevante → contexto",
                how:"omega_search_fast + embedding lookup = rota query→Z53/Z47/Z72",
                effort:"baixo · 2-3 dias" },
              { g:"G3", name:"Inferência local (llama.cpp)",
                what:"Nenhum modelo de linguagem rodando localmente",
                how:"llama.cpp ARM32 NEON + GGUF Q4_0 Phi-2 ou TinyLlama (2GB RAM total)",
                effort:"alto · 1-2 semanas build" },
              { g:"G4", name:"Bridge rafa_bare ↔ llama",
                what:"rafa_bare.elf não se comunica com llama.cpp",
                how:"POSIX pipe: rafa_run → stdout → llama stdin; ou socket Unix",
                effort:"médio · 3-5 dias" },
              { g:"G5", name:"Context injection automático",
                what:"Zona relevante → system prompt não é automático",
                how:"omega_nav seleciona zona → cat zone_N_content | ./llama-cli -p -",
                effort:"baixo · 1 dia" },
            ].map(gap => (
              <div key={gap.g} style={{background:V.surface,
                border:`1px solid ${V.red}44`, borderRadius:5,
                padding:"8px 10px", marginBottom:8}}>
                <div style={{display:"flex", gap:8, alignItems:"center", marginBottom:4}}>
                  <span style={{fontSize:12, fontWeight:700, color:V.red}}>{gap.g}</span>
                  <span style={{fontSize:11, fontWeight:700, color:V.hi}}>{gap.name}</span>
                  <span style={{fontSize:9, color:V.lo, marginLeft:"auto",
                    border:`1px solid ${V.border}`, borderRadius:3, padding:"1px 5px"}}>
                    {gap.effort}
                  </span>
                </div>
                <div style={{fontSize:9, color:V.lo, marginBottom:4}}>{gap.what}</div>
                <div style={{fontSize:9, color:V.teal, background:V.void,
                  borderRadius:3, padding:"3px 6px"}}>{gap.how}</div>
              </div>
            ))}

            <div style={{marginTop:10, background:V.torus, border:`1px solid ${V.gold}44`,
              borderRadius:5, padding:10}}>
              <div style={{fontSize:10, color:V.gold, marginBottom:6}}>
                SEQUÊNCIA ÓTIMA PARA FECHAR OS GAPS
              </div>
              {["G5 (1 dia) → G2 (3 dias) → G1 (1 semana) → G4 (5 dias) → G3 (2 semanas)",
                "Ordem: o que não precisa do modelo primeiro",
                "G3 (llama.cpp build) pode rodar em paralelo com G1/G2",
              ].map((l,i) => (
                <div key={i} style={{fontSize:9, color: i===0 ? V.gold : V.lo, marginBottom:3}}>
                  {i===0 ? "→ " : "  "}{l}
                </div>
              ))}
            </div>
          </div>
        )}

      </div>

      <div style={{padding:"4px 16px", fontSize:8, color:V.lo,
        borderTop:`1px solid ${V.border}`, background:V.torus}}>
        RAFCODE-Φ-∆RafaelVerboΩ · análise: 20 arquivos · 2 ELFs AArch64 · 5 ZIPs · 136 zonas
      </div>
    </div>
  );
}
