import { useState, useCallback, useRef } from "react";

// ── Constantes ──────────────────────────────────────────────────────────────
const STATUS = {
  PENDING:    { id: "pending",    label: "Aguarda",  color: "#7A7A9A", glyph: "○" },
  PROMISED:   { id: "promised",   label: "Prometido",color: "#4ECDC4", glyph: "◎" },
  DELIVERED:  { id: "delivered",  label: "Entregue", color: "#51CF66", glyph: "●" },
  ERROR:      { id: "error",      label: "Erro",     color: "#FF6B6B", glyph: "✕" },
  REGRESSED:  { id: "regressed",  label: "Regrediu", color: "#FF3860", glyph: "↩" },
  SKIPPED:    { id: "skipped",    label: "Skip",     color: "#5A5A7A", glyph: "—" },
};

// Gera lista A-Z ou numérica
function genItems(type, count) {
  return Array.from({ length: count }, (_, i) => ({
    id:      `item_${Date.now()}_${i}`,
    key:     type === "alpha"
               ? String.fromCharCode(65 + i)
               : String(i + 1).padStart(2, "0"),
    label:   "",
    status:  STATUS.PENDING.id,
    history: [],  // rastreia mudanças de status
    note:    "",
    hadOK:   false,  // foi entregue com sucesso em algum momento?
  }));
}

function useItems(initial) {
  const [items, setItems] = useState(initial);

  const update = useCallback((id, patch) => {
    setItems(prev => prev.map(item => {
      if (item.id !== id) return item;
      const next = { ...item, ...patch };
      // Detecta regressão: era DELIVERED, agora voltou a ERROR
      if (item.hadOK && patch.status === STATUS.ERROR.id) {
        next.status = STATUS.REGRESSED.id;
      }
      // Marca que já foi entregue com sucesso
      if (patch.status === STATUS.DELIVERED.id) {
        next.hadOK = true;
      }
      // Registra histórico
      if (patch.status && patch.status !== item.status) {
        next.history = [
          ...item.history,
          { from: item.status, to: next.status, ts: new Date().toLocaleTimeString() }
        ];
      }
      return next;
    }));
  }, []);

  const remove = useCallback((id) => {
    setItems(prev => prev.filter(i => i.id !== id));
  }, []);

  const reorder = useCallback((fromIdx, toIdx) => {
    setItems(prev => {
      const next = [...prev];
      const [moved] = next.splice(fromIdx, 1);
      next.splice(toIdx, 0, moved);
      return next;
    });
  }, []);

  return [items, setItems, update, remove];
}

// ── Componente principal ────────────────────────────────────────────────────
export default function RAFAELIALedger() {
  const [sessions, setSessions] = useState([{
    id:      "s1",
    name:    "Sessão RLL DMAIC",
    platform:"Claude",
    promise: "Implementar G01–G10 com backprop completo",
    created: new Date().toLocaleString(),
    items:   genItems("alpha", 10),
  }]);
  const [activeSessionId, setActiveSessionId] = useState("s1");
  const [showNewSession, setShowNewSession] = useState(false);
  const [newSess, setNewSess] = useState({ name:"", platform:"Claude", promise:"", type:"alpha", count:10 });
  const [selectedItem, setSelectedItem] = useState(null);
  const [note, setNote]     = useState("");
  const [filter, setFilter] = useState("all");
  const [showHistory, setShowHistory] = useState(false);

  const activeSession = sessions.find(s => s.id === activeSessionId);

  // Injeta items com update/remove dentro da sessão ativa
  const [, , updateItem, removeItem] = useItems([]);  // bootstrap

  function updateSessionItem(sessionId, itemId, patch) {
    setSessions(prev => prev.map(sess => {
      if (sess.id !== sessionId) return sess;
      const nextItems = sess.items.map(item => {
        if (item.id !== itemId) return item;
        const next = { ...item, ...patch };
        if (item.hadOK && patch.status === STATUS.ERROR.id) {
          next.status = STATUS.REGRESSED.id;
        }
        if (patch.status === STATUS.DELIVERED.id) next.hadOK = true;
        if (patch.status && patch.status !== item.status) {
          next.history = [...(item.history||[]),
            { from: item.status, to: next.status, ts: new Date().toLocaleTimeString() }];
        }
        return next;
      });
      return { ...sess, items: nextItems };
    }));
  }

  function createSession() {
    if (!newSess.name.trim()) return;
    const id = `s_${Date.now()}`;
    setSessions(prev => [...prev, {
      id, name: newSess.name, platform: newSess.platform,
      promise: newSess.promise,
      created: new Date().toLocaleString(),
      items:   genItems(newSess.type, Math.min(Math.max(+newSess.count, 1), 26)),
    }]);
    setActiveSessionId(id);
    setShowNewSession(false);
    setNewSess({ name:"", platform:"Claude", promise:"", type:"alpha", count:10 });
  }

  // Stats
  const stats = activeSession ? {
    total:     activeSession.items.length,
    delivered: activeSession.items.filter(i => i.status === "delivered").length,
    errors:    activeSession.items.filter(i => i.status === "error").length,
    regressed: activeSession.items.filter(i => i.status === "regressed").length,
    promised:  activeSession.items.filter(i => i.status === "promised").length,
    progress:  activeSession.items.length
      ? Math.round(activeSession.items.filter(i => i.status === "delivered").length
          / activeSession.items.length * 100) : 0,
  } : {};

  const filteredItems = activeSession?.items.filter(item => {
    if (filter === "all") return true;
    return item.status === filter;
  }) ?? [];

  // Gera seed de contexto para re-injeção
  function buildContextSeed() {
    if (!activeSession) return "";
    const lines = [
      `╔═[RAFAELIA·Ledger]═══════════════════`,
      `║ Sessão: ${activeSession.name}`,
      `║ Plataforma: ${activeSession.platform}`,
      `║ Promessa: ${activeSession.promise}`,
      `╠══ Status ══════════════════════════`,
    ];
    activeSession.items.forEach(item => {
      const s = STATUS[item.status?.toUpperCase()] ?? STATUS.PENDING;
      lines.push(`║ [${item.key}] ${s.glyph} ${item.label || "(sem rótulo)"} — ${s.label}${item.status==="regressed"?" ⚠️REGRESSÃO":""}`);
    });
    if (stats.regressed > 0) {
      lines.push(`╠══ ATENÇÃO: ${stats.regressed} item(s) regrediram após correção ══`);
    }
    lines.push(`╠══════════════════════════════════`);
    lines.push(`║ Progresso: ${stats.progress}% (${stats.delivered}/${stats.total})`);
    lines.push(`╚══════════════════════════════════`);
    lines.push("RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ");
    return lines.join("\n");
  }

  // ── Estilos ────────────────────────────────────────────────────────────────
  const V = { void:"#0A0A0F", torus:"#12121E", surface:"#1A1A2E",
    border:"#2A2A42", gold:"#E8B86D", teal:"#4ECDC4",
    vio:"#C3A6FF", red:"#FF6B6B", hi:"#F0EDE6", lo:"#7A7A9A",
    green:"#51CF66", regress:"#FF3860" };

  const root = {
    fontFamily:"'Space Mono',monospace", background:V.void, color:V.hi,
    minHeight:"100vh", display:"flex", flexDirection:"column",
  };
  const hdr = {
    background:V.torus, borderBottom:`1px solid ${V.border}`,
    padding:"10px 16px", display:"flex", alignItems:"center", gap:10, flexWrap:"wrap",
  };
  const pill = (c="") => ({
    background:V.surface, border:`1px solid ${c || V.border}`,
    borderRadius:4, padding:"3px 8px", fontSize:10,
    color: c || V.lo, cursor:"pointer",
  });
  const body = { display:"flex", flex:1, overflow:"hidden" };
  const sidebar = {
    width:200, background:V.torus, borderRight:`1px solid ${V.border}`,
    padding:8, overflowY:"auto", flexShrink:0,
  };
  const main = { flex:1, padding:12, overflowY:"auto" };
  const btn = (primary=false, danger=false) => ({
    background: primary ? V.gold : danger ? "#3A1A1A" : V.surface,
    border:`1px solid ${primary ? V.gold : danger ? V.red : V.border}`,
    borderRadius:5, padding:"5px 10px", fontSize:10,
    color: primary ? V.void : danger ? V.red : V.hi,
    fontFamily:"'Space Mono',monospace", cursor:"pointer", fontWeight: primary?"700":"400",
  });
  const inp = { background:V.surface, border:`1px solid ${V.border}`,
    borderRadius:4, color:V.hi, fontFamily:"'Space Mono',monospace",
    fontSize:11, padding:"5px 7px", outline:"none", width:"100%", boxSizing:"border-box" };

  return (
    <div style={root}>
      {/* ── Header ── */}
      <div style={hdr}>
        <span style={{fontSize:18}}>📒</span>
        <div>
          <div style={{fontSize:13, fontWeight:700, color:V.gold, letterSpacing:"0.08em"}}>
            RAFAELIA LEDGER
          </div>
          <div style={{fontSize:9, color:V.lo}}>Rastreador de Promessas · Detector de Regressão</div>
        </div>

        {/* Stats globais */}
        {activeSession && (
          <div style={{display:"flex", gap:8, marginLeft:"auto", flexWrap:"wrap", alignItems:"center"}}>
            {stats.regressed > 0 && (
              <span style={{...pill(V.regress), animation:"pulse 1s infinite", fontWeight:700}}>
                ↩ {stats.regressed} REGRESSÃO
              </span>
            )}
            <span style={pill(V.green)}>✓ {stats.delivered}</span>
            <span style={pill(V.red)}>✕ {stats.errors}</span>
            <span style={pill(V.teal)}>◎ {stats.promised}</span>
            <span style={{...pill(), minWidth:60}}>
              <div style={{background:V.border, height:4, borderRadius:2, width:"100%"}}>
                <div style={{background:V.green, height:4, borderRadius:2,
                  width:`${stats.progress}%`, transition:"width 0.3s"}} />
              </div>
              <span style={{fontSize:9, color:V.lo}}>{stats.progress}%</span>
            </span>
          </div>
        )}
      </div>

      <div style={body}>
        {/* ── Sidebar: sessões ── */}
        <div style={sidebar}>
          <div style={{fontSize:10, color:V.lo, marginBottom:6}}>SESSÕES</div>

          {sessions.map(sess => (
            <div key={sess.id}
              onClick={() => setActiveSessionId(sess.id)}
              style={{
                padding:"7px 8px", marginBottom:4, borderRadius:4, cursor:"pointer",
                background: activeSessionId===sess.id ? V.surface : "transparent",
                border:`1px solid ${activeSessionId===sess.id ? V.gold+"66" : "transparent"}`,
              }}>
              <div style={{fontSize:10, color: activeSessionId===sess.id ? V.gold : V.hi,
                whiteSpace:"nowrap", overflow:"hidden", textOverflow:"ellipsis"}}>
                {sess.name}
              </div>
              <div style={{fontSize:9, color:V.lo}}>{sess.platform}</div>
              <div style={{fontSize:9, color: sess.items.some(i=>i.status==="regressed") ? V.regress : V.lo}}>
                {sess.items.filter(i=>i.status==="delivered").length}/{sess.items.length}
                {sess.items.some(i=>i.status==="regressed") ? " ↩" : ""}
              </div>
            </div>
          ))}

          <button style={{...btn(), width:"100%", marginTop:8, fontSize:10}}
            onClick={() => setShowNewSession(v => !v)}>
            + Nova Sessão
          </button>

          {showNewSession && (
            <div style={{marginTop:8, display:"flex", flexDirection:"column", gap:6}}>
              <input style={inp} placeholder="Nome da sessão"
                value={newSess.name} onChange={e=>setNewSess(p=>({...p,name:e.target.value}))} />
              <select style={inp} value={newSess.platform}
                onChange={e=>setNewSess(p=>({...p,platform:e.target.value}))}>
                {["Claude","ChatGPT","Gemini","Mistral","Outro"].map(p =>
                  <option key={p}>{p}</option>
                )}
              </select>
              <textarea style={{...inp, minHeight:50, resize:"vertical"}}
                placeholder="O que a IA prometeu?"
                value={newSess.promise} onChange={e=>setNewSess(p=>({...p,promise:e.target.value}))} />
              <div style={{display:"flex", gap:4}}>
                <select style={{...inp, flex:1}} value={newSess.type}
                  onChange={e=>setNewSess(p=>({...p,type:e.target.value}))}>
                  <option value="alpha">A–Z</option>
                  <option value="num">1–N</option>
                </select>
                <input style={{...inp, width:50}} type="number" min="1" max="26"
                  value={newSess.count} onChange={e=>setNewSess(p=>({...p,count:e.target.value}))} />
              </div>
              <button style={btn(true)} onClick={createSession}>Criar</button>
            </div>
          )}
        </div>

        {/* ── Main: itens ── */}
        <div style={main}>
          {!activeSession
            ? <div style={{color:V.lo, fontSize:11, textAlign:"center", padding:40}}>
                TOKEN_VAZIO · Crie ou selecione uma sessão
              </div>
            : <>
              {/* Promessa da sessão */}
              <div style={{background:V.torus, border:`1px solid ${V.border}`,
                borderRadius:6, padding:"8px 12px", marginBottom:10}}>
                <div style={{fontSize:9, color:V.lo}}>PROMESSA REGISTRADA · {activeSession.platform}</div>
                <div style={{fontSize:11, color:V.teal, marginTop:3}}>{activeSession.promise || "—"}</div>
              </div>

              {/* Filtros */}
              <div style={{display:"flex", gap:6, marginBottom:8, flexWrap:"wrap"}}>
                {["all","pending","promised","delivered","error","regressed"].map(f => (
                  <button key={f} style={{...pill(filter===f ? V.gold : ""),
                    color: filter===f ? V.gold : V.lo}}
                    onClick={()=>setFilter(f)}>
                    {f === "all" ? "Todos" : STATUS[f?.toUpperCase()]?.label ?? f}
                    {f !== "all" && ` (${activeSession.items.filter(i=>i.status===f).length})`}
                  </button>
                ))}
              </div>

              {/* Lista de itens */}
              <div style={{display:"flex", flexDirection:"column", gap:4}}>
                {filteredItems.map((item) => {
                  const st = STATUS[item.status?.toUpperCase()] ?? STATUS.PENDING;
                  const isReg = item.status === "regressed";
                  return (
                    <div key={item.id}
                      style={{
                        background: selectedItem===item.id ? V.surface : V.torus,
                        border:`1px solid ${isReg ? V.regress : selectedItem===item.id ? V.gold+"66" : V.border}`,
                        borderRadius:5, padding:"8px 10px",
                        display:"flex", alignItems:"center", gap:8, cursor:"pointer",
                        boxShadow: isReg ? `0 0 8px ${V.regress}44` : "none",
                        transition:"all 0.15s",
                      }}
                      onClick={() => setSelectedItem(selectedItem===item.id ? null : item.id)}>

                      {/* Key */}
                      <span style={{fontWeight:700, fontSize:13, color:V.gold,
                        minWidth:22, textAlign:"center"}}>{item.key}</span>

                      {/* Status glyph */}
                      <span style={{fontSize:14, color:st.color}}>{st.glyph}</span>

                      {/* Label editável */}
                      <input
                        style={{...inp, flex:1, background:"transparent", border:"none",
                          borderBottom:`1px solid ${V.border}`, borderRadius:0, padding:"2px 0"}}
                        placeholder={`Item ${item.key}…`}
                        value={item.label}
                        onClick={e => e.stopPropagation()}
                        onChange={e => updateSessionItem(activeSessionId, item.id, {label:e.target.value})}
                      />

                      {/* Status badge */}
                      <span style={{fontSize:9, color:st.color, minWidth:68, textAlign:"right"}}>
                        {st.label}{isReg ? " ⚠️" : ""}
                      </span>

                      {/* Ações rápidas */}
                      <div style={{display:"flex", gap:3}} onClick={e=>e.stopPropagation()}>
                        {["promised","delivered","error","skipped"].map(s => {
                          const S = STATUS[s.toUpperCase()];
                          return (
                            <button key={s}
                              title={S.label}
                              style={{
                                background: item.status===s ? S.color+"33" : "transparent",
                                border:`1px solid ${item.status===s ? S.color : V.border}`,
                                borderRadius:3, padding:"2px 5px", fontSize:11,
                                color: item.status===s ? S.color : V.lo, cursor:"pointer",
                              }}
                              onClick={()=>updateSessionItem(activeSessionId, item.id, {status:s})}>
                              {S.glyph}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Painel de detalhe do item selecionado */}
              {selectedItem && (() => {
                const item = activeSession.items.find(i => i.id === selectedItem);
                if (!item) return null;
                return (
                  <div style={{marginTop:12, background:V.torus,
                    border:`1px solid ${V.border}`, borderRadius:6, padding:12}}>
                    <div style={{fontSize:10, color:V.lo, marginBottom:6}}>
                      DETALHE · Item [{item.key}]
                      {item.hadOK && item.status==="regressed" && (
                        <span style={{color:V.regress, marginLeft:8}}>
                          ⚠️ REGRESSÃO: estava entregue, voltou ao erro
                        </span>
                      )}
                    </div>

                    <textarea style={{...inp, minHeight:60, resize:"vertical"}}
                      placeholder="Nota / contexto do erro..."
                      value={item.note}
                      onChange={e=>updateSessionItem(activeSessionId,item.id,{note:e.target.value})}
                    />

                    {item.history?.length > 0 && (
                      <div style={{marginTop:8}}>
                        <div style={{fontSize:9, color:V.lo, marginBottom:4}}>HISTÓRICO</div>
                        {item.history.map((h, i) => (
                          <div key={i} style={{fontSize:9, color:V.lo, padding:"2px 0",
                            borderBottom:`1px solid ${V.border}22`}}>
                            {h.ts} · {STATUS[h.from?.toUpperCase()]?.glyph} → {STATUS[h.to?.toUpperCase()]?.glyph}
                            {" "}{STATUS[h.from?.toUpperCase()]?.label} → {STATUS[h.to?.toUpperCase()]?.label}
                            {h.to==="regressed" && <span style={{color:V.regress}}> ↩ REGREDIU</span>}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })()}

              {/* Ações globais */}
              <div style={{display:"flex", gap:8, marginTop:14, flexWrap:"wrap"}}>
                <button style={btn(true)} onClick={() => {
                  const seed = buildContextSeed();
                  // Copia para clipboard
                  navigator.clipboard?.writeText(seed).catch(()=>{});
                  alert("Seed copiada! Cole no campo da IA para re-injetar o contexto completo.");
                }}>
                  📋 Copiar Seed de Contexto
                </button>

                <button style={btn()} onClick={() => {
                  // Marca todos como "promised" (início de nova tentativa)
                  const regressedItems = activeSession.items.filter(i => i.status==="regressed");
                  regressedItems.forEach(item =>
                    updateSessionItem(activeSessionId, item.id, {status:"promised"}));
                }}>
                  ↩ Re-prometer Regressões ({stats.regressed})
                </button>

                <button style={btn()} onClick={() => {
                  setSessions(prev => prev.map(s => {
                    if (s.id !== activeSessionId) return s;
                    return { ...s, items: s.items.map(i =>
                      i.status==="delivered" ? i :
                      {...i, status:"promised"}
                    )};
                  }));
                }}>
                  ◎ Marcar Pendentes como Prometidos
                </button>

                <button style={btn(false, true)} onClick={() => {
                  if (window.confirm("Remover esta sessão?")) {
                    setSessions(prev => prev.filter(s => s.id !== activeSessionId));
                    setActiveSessionId(sessions[0]?.id || null);
                  }
                }}>
                  🗑 Remover Sessão
                </button>
              </div>

              {/* Preview da Seed de contexto */}
              <div style={{marginTop:14, background:V.void,
                border:`1px solid ${V.border}`, borderRadius:4, padding:8}}>
                <div style={{fontSize:9, color:V.lo, marginBottom:4}}>
                  SEED DE CONTEXTO PARA RE-INJEÇÃO NA IA
                </div>
                <pre style={{fontSize:8, color:V.teal, margin:0,
                  whiteSpace:"pre-wrap", maxHeight:150, overflowY:"auto"}}>
                  {buildContextSeed()}
                </pre>
              </div>
            </>
          }
        </div>
      </div>

      {/* Footer */}
      <div style={{padding:"4px 16px", fontSize:8, color:V.lo,
        borderTop:`1px solid ${V.border}`, background:V.torus}}>
        RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ · dados 100% locais · moral &gt; contrato &gt; legalidade
      </div>

      <style>{`@keyframes pulse{0%,100%{opacity:1}50%{opacity:0.5}}`}</style>
    </div>
  );
}
