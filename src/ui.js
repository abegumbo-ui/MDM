const STYLE = `
:root{--bg:#f6f7f9;--card:#fff;--fg:#1c2330;--mute:#667085;--line:#e4e7ec;--acc:#2457d6;--bad:#c0392b;--ok:#1e8e5a}
@media(prefers-color-scheme:dark){:root{--bg:#12151b;--card:#1b2029;--fg:#e8ebf1;--mute:#98a2b3;--line:#2a313d;--acc:#6b93f5;--bad:#ef7b6e;--ok:#4cc38a}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif}
main{max-width:900px;margin:0 auto;padding:16px}h1{font-size:20px;margin:8px 0 16px}h2{font-size:16px;margin:0 0 10px}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px;margin-bottom:14px}
button,select,input,textarea{font:inherit;color:inherit;background:var(--card);border:1px solid var(--line);border-radius:8px;padding:7px 10px}
button{cursor:pointer}button.p{background:var(--acc);border-color:var(--acc);color:#fff}button.d{color:var(--bad)}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.grow{flex:1;min-width:140px}.mute{color:var(--mute);font-size:13px}
table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:7px 4px;border-top:1px solid var(--line);vertical-align:middle}th{border:0;color:var(--mute);font-weight:500;font-size:13px}
.tag{font-size:11px;border:1px solid var(--line);border-radius:99px;padding:1px 7px;color:var(--mute);margin-left:4px}
#msg{position:fixed;bottom:12px;left:12px;right:12px;max-width:600px;margin:auto;padding:10px 14px;border-radius:8px;background:var(--fg);color:var(--bg);display:none}
textarea{width:100%;min-height:90px;font-family:ui-monospace,monospace;font-size:13px}.wrap{overflow-x:auto}
`;

export const loginPage = (error = "") => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>MDM Login</title><style>${STYLE}</style></head>
<body><main style="max-width:360px;margin-top:15vh"><div class="card"><h1>MDM Dashboard</h1>
<form method="post" action="/login"><div class="row"><input class="grow" type="password" name="password" placeholder="Admin password" autofocus required>
<button class="p">Sign in</button></div>${error ? `<p class="mute" style="color:var(--bad)">${error}</p>` : ""}</form></div></main></body></html>`;

export const dashboardPage = () => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>MDM Dashboard</title><style>${STYLE}</style></head>
<body><main>
<div class="row"><h1 class="grow">MDM Dashboard</h1><a href="/logout" class="mute">Sign out</a></div>

<div class="card"><h2>Add a device</h2>
<p class="mute">1. Factory-reset the phone, skip Google sign-in, and finish setup with <b>no accounts</b>. 2. Turn on USB debugging. 3. Install the agent and make it device owner (commands below). 4. Open the agent and enter the server and code.</p>
<button class="p" id="newcode">Generate enrollment code</button>
<pre id="codebox" class="mute" hidden></pre></div>

<div class="card"><h2>Devices</h2><div id="devices" class="mute">Loading…</div>
<button id="refresh" style="margin-top:8px">Refresh</button></div>

<div class="card"><h2>Apps</h2>
<p class="mute">Applies to every device. “Default” = hidden unless it's a protected system component. Changes reach the phone within about a minute.</p>
<div class="row" style="margin-bottom:10px">
<input id="newpkg" class="grow" placeholder="Package name, e.g. com.google.android.apps.maps">
<button id="addpkg">Add</button></div>
<label class="row mute"><input type="checkbox" id="blockUnlisted"> Hide every launcher app that isn\'t allowed below</label>
<div class="wrap"><table id="apps"><thead><tr><th>App</th><th>Access</th></tr></thead><tbody></tbody></table></div></div>

<div class="card"><h2>Restrictions</h2><div id="restr"></div>
<div class="row" style="margin-top:12px"><button class="p" id="save">Save</button>
<span class="mute">Saved settings go to phones on their next check-in.</span></div></div>
</div>
<div id="msg"></div>
<script>
const $=s=>document.querySelector(s);let state,devices=[];
function toast(t,bad){const m=$('#msg');m.textContent=t;m.style.background=bad?'var(--bad)':'';m.style.display='block';clearTimeout(toast.t);toast.t=setTimeout(()=>m.style.display='none',5000)}
async function call(method,path,body){const r=await fetch(path,{method,headers:{'content-type':'application/json'},body:body&&JSON.stringify(body)});
 const d=await r.json().catch(()=>({}));if(r.status===401)location.reload();if(!r.ok)throw new Error(d.error||r.statusText);return d}
const act=(btn,fn)=>btn.addEventListener('click',async()=>{btn.disabled=true;try{await fn()}catch(e){toast(e.message,1)}btn.disabled=false});
const ago=t=>{if(!t)return 'never';const m=Math.round((Date.now()-t)/60000);return m<1?'just now':m<60?m+' min ago':Math.round(m/60)+' h ago'};

function renderApps(){
 const cfg=state.config.apps,seen=new Map();
 for(const d of devices)for(const a of d.packages){const o=seen.get(a.p)||{p:a.p,l:a.l,s:a.s,prot:a.protected};seen.set(a.p,o)}
 for(const p of Object.keys(cfg))if(!seen.has(p))seen.set(p,{p,l:cfg[p].label||p});
 const rows=[...seen.values()].sort((a,b)=>(a.l||a.p).localeCompare(b.l||b.p));
 const tb=$('#apps tbody');tb.innerHTML='';
 for(const a of rows){
  const tr=document.createElement('tr'),td=document.createElement('td'),sel=document.createElement('select');
  td.textContent=a.l||a.p;
  const sub=document.createElement('div');sub.className='mute';sub.textContent=a.p;td.append(sub);
  for(const t of [a.s&&'system',a.prot&&'protected']){if(t){const g=document.createElement('span');g.className='tag';g.textContent=t;td.firstChild.after(g)}}
  for(const [v,l] of [['','Default'],['allow','Allow'],['block','Block']])sel.add(new Option(l,v));
  sel.value=cfg[a.p]?.mode==='block'?'block':cfg[a.p]?'allow':'';
  sel.onchange=()=>{if(sel.value)cfg[a.p]={mode:sel.value,label:a.l};else delete cfg[a.p]};
  const t2=document.createElement('td');t2.append(sel);tr.append(td,t2);tb.append(tr)}
}
function renderRestr(){
 const el=$('#restr');el.textContent='';
 for(const [k,v] of Object.entries(state.restrictions)){
  const l=document.createElement('label');l.className='row';l.style.padding='4px 0';
  const c=document.createElement('input');c.type='checkbox';c.checked=!!state.config.restrictions[k];c.onchange=()=>state.config.restrictions[k]=c.checked;
  l.append(c,document.createTextNode(' '+v.label));el.append(l)}
}
function renderDevices(){
 const el=$('#devices');el.textContent='';
 if(!devices.length){el.textContent='No devices enrolled yet.';return}
 for(const d of devices){
  const row=document.createElement('div');row.style.cssText='padding:10px 0;border-top:1px solid var(--line)';
  const info=document.createElement('div');info.textContent=d.name;
  const sub=document.createElement('div');sub.className='mute';
  const hidden=d.packages.filter(p=>p.h).length;
  sub.textContent='Android '+(d.info.android||'?')+' · last seen '+ago(d.lastSeen)+' · '+d.packages.length+' apps ('+hidden+' hidden) · '+d.pending+' queued'+(d.info.deviceOwner===false?' · NOT device owner!':'');
  info.append(sub);
  const last=d.results[d.results.length-1];
  if(last){const r=document.createElement('div');r.className='mute';r.textContent='Last command: '+last.type+' → '+(last.ok?'ok':'failed')+(last.msg?' ('+last.msg+')':'');info.append(r)}
  const btns=document.createElement('div');btns.className='row';btns.style.marginTop='6px';
  const mk=(label,type,args,confirmMsg,cls)=>{const b=document.createElement('button');b.textContent=label;if(cls)b.className=cls;
   act(b,async()=>{if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}
    await call('POST','/api/devices/'+d.id+'/command',{type,args:a});toast(label+' queued');load()});btns.append(b)};
  mk('Sync now','sync');mk('Lock','lock');mk('Reboot','reboot');
  mk('Install APK','install',()=>{const url=prompt('Direct https:// link to an APK file');return url?{url}:null});
  mk('Uninstall app','uninstall',()=>{const packageName=prompt('Package name to uninstall');return packageName?{packageName}:null});
  mk('Release device','release',null,'Release this device? It stops being managed and all restrictions are removed.');
  mk('Wipe','wipe',null,'ERASE this device completely?','d');
  const rm=document.createElement('button');rm.textContent='Remove record';
  act(rm,async()=>{if(!confirm('Delete this device from the dashboard? The phone stays managed; use Release first.'))return;await call('DELETE','/api/devices/'+d.id);load()});btns.append(rm);
  row.append(info,btns);el.append(row)}
}
async function load(){
 state=await call('GET','/api/state');devices=await call('GET','/api/devices');
 $('#blockUnlisted').checked=state.config.blockUnlisted;renderDevices();renderApps();renderRestr()}
act($('#newcode'),async()=>{const r=await call('POST','/api/enrollment-code');const b=$('#codebox');b.hidden=false;
 b.textContent='Enrollment code (valid 1 hour): '+r.code+'\\nServer: '+r.server+
 '\\n\\nOn your computer, with the phone connected and USB debugging on:\\n  adb install mdm-agent.apk\\n  adb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver\\n  adb shell am start -n com.familymdm.agent/.MainActivity --es server '+r.server+' --es code '+r.code});
act($('#refresh'),load);
$('#addpkg').onclick=()=>{const v=$('#newpkg').value.trim();if(!v)return;state.config.apps[v]={mode:'allow'};$('#newpkg').value='';renderApps()};
act($('#save'),async()=>{state.config.blockUnlisted=$('#blockUnlisted').checked;await call('PUT','/api/config',state.config);toast('Saved')});
load().catch(e=>toast(e.message,1));
setInterval(()=>load().catch(()=>{}),60000);
</script></body></html>`;
