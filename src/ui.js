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

<div class="card" id="setup" hidden><h2>1. Connect to Google</h2>
<p class="mute">One-time: links this dashboard to a managed Google enterprise (no Google Workspace needed).</p>
<button class="p" id="signup">Start setup</button></div>

<div id="main" hidden>
<div class="card"><h2>Add a device</h2>
<p class="mute">Factory-reset phone → on the first welcome screen tap the same spot 6 times → scan this code. Do <b>not</b> sign into any Google account first. Code is valid for 1 hour, one device.</p>
<button class="p" id="enroll">Generate QR code</button>
<div id="qr" style="margin-top:12px"></div></div>

<div class="card"><h2>Apps</h2>
<p class="mute">Choose what the device may use. “Default” = hidden/blocked unless it's a protected system component. Protected items are kept on so the phone stays usable; you can still block them explicitly.</p>
<div class="row" style="margin-bottom:10px">
<input id="newpkg" class="grow" placeholder="Package name, e.g. com.google.android.apps.maps">
<button id="addpkg">Add</button>
<select id="preset"><option value="">Quick add…</option>
<option value="com.google.android.apps.maps">Google Maps</option><option value="com.waze">Waze</option>
<option value="com.google.android.calculator">Calculator</option><option value="com.google.android.deskclock">Clock</option>
<option value="com.google.android.calendar">Calendar</option><option value="com.google.android.apps.messaging">Messages</option>
<option value="com.google.android.dialer">Phone</option><option value="com.google.android.contacts">Contacts</option></select></div>
<label class="row mute"><input type="checkbox" id="blockUnlisted"> Block every app on the device that isn't allowed below</label>
<div class="wrap"><table id="apps"><thead><tr><th>App</th><th>Access</th></tr></thead><tbody></tbody></table></div>
<div class="row" style="margin-top:12px"><button class="p" id="save">Save &amp; push to devices</button>
<span class="mute">Devices pick this up within minutes when online.</span></div></div>

<div class="card"><h2>Devices</h2><div id="devices" class="mute">Loading…</div>
<button id="refresh" style="margin-top:8px">Refresh</button></div>

<details class="card"><summary>Advanced policy overrides (JSON)</summary>
<p class="mute">Raw Android Management API policy fields merged last, e.g. lock a filtering DNS or Wi-Fi settings.</p>
<textarea id="extra">{}</textarea></details>
</div>
<div id="msg"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<script>
const $=s=>document.querySelector(s);let state,devices=[];
function toast(t,bad){const m=$('#msg');m.textContent=t;m.style.background=bad?'var(--bad)':'';m.style.display='block';clearTimeout(toast.t);toast.t=setTimeout(()=>m.style.display='none',5000)}
async function call(method,path,body){const r=await fetch(path,{method,headers:{'content-type':'application/json'},body:body&&JSON.stringify(body)});
 const d=await r.json().catch(()=>({}));if(r.status===401)location.reload();if(!r.ok)throw new Error(d.error||r.statusText);return d}
const act=(btn,fn)=>btn.addEventListener('click',async()=>{btn.disabled=true;try{await fn()}catch(e){toast(e.message,1)}btn.disabled=false});

function renderApps(){
 const cfg=state.config.apps,seen=new Map();
 for(const d of devices)for(const a of d.apps)seen.set(a.packageName,a);
 for(const p of Object.keys(cfg))if(!seen.has(p))seen.set(p,{packageName:p,label:cfg[p].label});
 const rows=[...seen.values()].sort((a,b)=>(a.label||a.packageName).localeCompare(b.label||b.packageName));
 const tb=$('#apps tbody');tb.innerHTML='';
 for(const a of rows){
  const tr=document.createElement('tr'),td=document.createElement('td'),sel=document.createElement('select');
  td.textContent=a.label||a.packageName;
  const sub=document.createElement('div');sub.className='mute';sub.textContent=a.packageName;td.append(sub);
  if(a.protected){const t=document.createElement('span');t.className='tag';t.textContent='protected';td.firstChild.after(t)}
  for(const [v,l] of [['','Default'],['allow','Allow'],['force','Force install'],['block','Block']]){const o=new Option(l,v);sel.add(o)}
  sel.value=cfg[a.packageName]?.mode||'';
  sel.onchange=()=>{if(sel.value)cfg[a.packageName]={mode:sel.value,label:a.label};else delete cfg[a.packageName]};
  const t2=document.createElement('td');t2.append(sel);tr.append(td,t2);tb.append(tr)}
}
function renderDevices(){
 const el=$('#devices');el.textContent='';
 if(!devices.length){el.textContent='No devices enrolled yet.';return}
 for(const d of devices){
  const row=document.createElement('div');row.className='row';row.style.cssText='padding:8px 0;border-top:1px solid var(--line)';
  const info=document.createElement('div');info.className='grow';
  info.textContent=[d.manufacturer,d.model].filter(Boolean).join(' ')||d.id;
  const sub=document.createElement('div');sub.className='mute';
  sub.textContent=d.state+' · Android '+(d.androidVersion||'?')+' · last seen '+(d.lastSync?new Date(d.lastSync).toLocaleString():'never')+' · '+(d.policyCompliant===false?'policy pending':'policy OK')+' · '+d.apps.length+' apps';
  info.append(sub);row.append(info);
  for(const [a,l] of [['lock','Lock'],['reboot','Reboot'],['wipe','Remove & wipe']]){
   const b=document.createElement('button');b.textContent=l;if(a==='wipe')b.className='d';
   act(b,async()=>{if(a==='wipe'&&!confirm('Erase this device and remove it from management?'))return;await call('POST','/api/devices/'+d.id+'/'+a);toast(l+' sent');if(a==='wipe')load()});row.append(b)}
  el.append(row)}
}
async function loadDevices(){devices=await call('GET','/api/devices');renderDevices();renderApps()}
async function load(){
 state=await call('GET','/api/state');
 $('#setup').hidden=!!state.enterprise;$('#main').hidden=!state.enterprise;
 if(!state.enterprise)return;
 $('#blockUnlisted').checked=state.config.blockUnlisted;$('#extra').value=JSON.stringify(state.config.extraPolicy,null,2);
 await loadDevices()}
act($('#signup'),async()=>{const r=await call('POST','/api/enterprise/signup-url');location.href=r.url});
act($('#enroll'),async()=>{const r=await call('POST','/api/enrollment');const q=$('#qr');q.textContent='';
 const box=document.createElement('div');q.append(box);new QRCode(box,{text:r.qrCode,width:280,height:280,correctLevel:QRCode.CorrectLevel.L});
 const p=document.createElement('p');p.className='mute';p.textContent='Expires '+new Date(r.expires).toLocaleTimeString();q.append(p)});
act($('#refresh'),loadDevices);
$('#addpkg').onclick=()=>{const v=$('#newpkg').value.trim();if(!v)return;state.config.apps[v]={mode:'allow'};$('#newpkg').value='';renderApps()};
$('#preset').onchange=e=>{if(e.target.value){state.config.apps[e.target.value]={mode:'allow'};e.target.value='';renderApps()}};
act($('#save'),async()=>{let extra;try{extra=JSON.parse($('#extra').value||'{}')}catch{throw new Error('Advanced JSON is invalid')}
 state.config.extraPolicy=extra;state.config.blockUnlisted=$('#blockUnlisted').checked;
 await call('PUT','/api/config',state.config);const r=await call('POST','/api/policy/apply');toast('Saved. '+r.applied+' app rules pushed.');loadDevices()});
load().catch(e=>toast(e.message,1));
</script></body></html>`;
