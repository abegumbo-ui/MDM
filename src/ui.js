// Dashboard pages. Material 3 styling with plain CSS and vanilla JS (no build step, no libraries).

const STYLE = String.raw`
:root{
  --primary:#6750a4;--on-primary:#fff;--primary-container:#eaddff;--on-primary-container:#21005d;
  --secondary-container:#e8def8;--on-secondary-container:#1d192b;
  --surface:#fef7ff;--surface-1:#f7f2fa;--surface-2:#f3edf7;--surface-3:#ece6f0;
  --on-surface:#1d1b20;--on-surface-variant:#49454f;--outline:#79747e;--outline-variant:#cac4d0;
  --error:#b3261e;--error-container:#f9dedc;--ok:#146c2e;--ok-container:#d8f5df;--warn:#8a5100;--warn-container:#ffe9c7;
}
@media(prefers-color-scheme:dark){:root{
  --primary:#d0bcff;--on-primary:#381e72;--primary-container:#4f378b;--on-primary-container:#eaddff;
  --secondary-container:#4a4458;--on-secondary-container:#e8def8;
  --surface:#141218;--surface-1:#1d1b20;--surface-2:#211f26;--surface-3:#2b2930;
  --on-surface:#e6e0e9;--on-surface-variant:#cac4d0;--outline:#938f99;--outline-variant:#49454f;
  --error:#f2b8b5;--error-container:#8c1d18;--ok:#7fd99a;--ok-container:#0f3d1c;--warn:#ffb95c;--warn-container:#4a2f00;
}}
*{box-sizing:border-box}
body{margin:0;background:var(--surface);color:var(--on-surface);font:14px/1.5 Roboto,system-ui,sans-serif}
header.bar{position:sticky;top:0;z-index:5;background:var(--surface-2);padding:12px 16px 0}
.bar .top{display:flex;align-items:center;gap:12px;max-width:960px;margin:0 auto}
.bar h1{font-size:22px;font-weight:500;margin:0;flex:1}
.bar a{color:var(--on-surface-variant);text-decoration:none;font-size:13px}
nav.tabs{display:flex;max-width:960px;margin:8px auto 0;overflow-x:auto}
nav.tabs button{flex:1;min-width:84px;background:none;border:0;border-bottom:3px solid transparent;color:var(--on-surface-variant);padding:12px 8px;font:500 14px Roboto,system-ui,sans-serif;cursor:pointer}
nav.tabs button.on{color:var(--primary);border-bottom-color:var(--primary)}
main{max-width:960px;margin:0 auto;padding:16px}
h2{font-size:16px;font-weight:500;margin:0 0 4px}
.card{background:var(--surface-1);border:1px solid var(--outline-variant);border-radius:16px;padding:16px;margin-bottom:12px}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.grow{flex:1;min-width:0}
.mute{color:var(--on-surface-variant);font-size:13px}.small{font-size:12px}
.mono{font-family:ui-monospace,Menlo,monospace}
button.btn{font:500 14px Roboto,system-ui,sans-serif;border:0;border-radius:20px;padding:9px 18px;cursor:pointer;background:var(--primary);color:var(--on-primary)}
button.btn.tonal{background:var(--secondary-container);color:var(--on-secondary-container)}
button.btn.outline{background:none;color:var(--primary);border:1px solid var(--outline)}
button.btn.danger{background:none;color:var(--error);border:1px solid var(--error)}
button.btn:disabled{opacity:.38;cursor:default}
input[type=text],input[type=time],input[type=password]{font:inherit;color:inherit;background:var(--surface);border:1px solid var(--outline);border-radius:8px;padding:10px 12px;min-width:0}
input:focus{outline:2px solid var(--primary);border-color:transparent}
.chip{display:inline-flex;align-items:center;gap:4px;border-radius:8px;padding:2px 8px;font-size:12px;background:var(--surface-3);color:var(--on-surface-variant);margin-right:4px}
.chip.ok{background:var(--ok-container);color:var(--ok)}.chip.bad{background:var(--error-container);color:var(--error)}.chip.warn{background:var(--warn-container);color:var(--warn)}
.seg{display:inline-flex;border:1px solid var(--outline);border-radius:20px;overflow:hidden}
.seg button{background:none;border:0;border-right:1px solid var(--outline);padding:6px 14px;color:var(--on-surface);font:500 13px Roboto,system-ui,sans-serif;cursor:pointer}
.seg button:last-child{border-right:0}
.seg button.on{background:var(--secondary-container);color:var(--on-secondary-container)}
.seg button.on.block{background:var(--error-container);color:var(--error)}
.app{display:flex;gap:12px;align-items:flex-start;padding:12px 0;border-top:1px solid var(--outline-variant)}
.app:first-child{border-top:0}
.app img,.app .ph{width:40px;height:40px;border-radius:10px;flex:none;background:var(--surface-3)}
.sw{position:relative;width:52px;height:32px;flex:none}
.sw input{opacity:0;position:absolute;inset:0;margin:0;cursor:pointer;z-index:1}
.sw i{position:absolute;inset:0;border-radius:16px;border:2px solid var(--outline);background:var(--surface-3);transition:.15s}
.sw i::after{content:"";position:absolute;top:6px;left:6px;width:16px;height:16px;border-radius:50%;background:var(--outline);transition:.15s}
.sw input:checked+i{background:var(--primary);border-color:var(--primary)}
.sw input:checked+i::after{left:26px;top:4px;width:24px;height:24px;background:var(--on-primary)}
.sw input:checked+i::after{width:20px;height:20px;left:26px;top:6px}
.setting{display:flex;gap:12px;align-items:center;padding:10px 0;border-top:1px solid var(--outline-variant)}
.setting:first-child{border-top:0}
.act{padding:6px 0;border-top:1px solid var(--outline-variant);font-size:13px}
.act:first-child{border-top:0}
.code{font:600 30px ui-monospace,Menlo,monospace;letter-spacing:4px;background:var(--surface-3);border-radius:12px;padding:10px 16px;display:inline-block;margin:8px 0}
pre.cmd{background:var(--surface-3);border-radius:12px;padding:12px;white-space:pre-wrap;word-break:break-all;font:12px ui-monospace,Menlo,monospace;margin:8px 0}
.days{display:flex;gap:4px;margin:8px 0}
.days button{width:34px;height:34px;border-radius:50%;border:1px solid var(--outline);background:none;color:var(--on-surface);cursor:pointer;font-weight:500}
.days button.on{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.sched{background:var(--surface-2);border-radius:12px;padding:10px 12px;margin-top:8px}
#snack{position:fixed;left:12px;right:12px;bottom:16px;max-width:520px;margin:auto;background:var(--on-surface);color:var(--surface);border-radius:8px;padding:12px 16px;display:none;z-index:9}
.m3dlg{border:0;border-radius:28px;background:var(--surface-3);color:var(--on-surface);padding:24px;max-width:420px;width:calc(100% - 32px)}
.m3dlg::backdrop{background:rgba(0,0,0,.45)}
.m3dlg h2{font-size:22px;font-weight:400;margin-bottom:12px}
.m3dlg label{display:block;margin:10px 0 4px;font-size:12px;color:var(--on-surface-variant)}
.m3dlg input,.m3dlg select{width:100%;font:inherit;color:inherit;background:var(--surface);border:1px solid var(--outline);border-radius:8px;padding:10px 12px}
.m3dlg .row{justify-content:flex-end;margin-top:20px}
.login{max-width:360px;margin:16vh auto 0;padding:0 16px}
`;

export const loginPage = (error = "") => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>MDM Login</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500&display=swap">
<style>${STYLE}</style></head>
<body><div class="login"><div class="card"><h2 style="font-size:22px;margin-bottom:12px">MDM Dashboard</h2>
<form method="post" action="/login"><div class="row"><input class="grow" type="password" name="password" placeholder="Admin password" autofocus required>
<button class="btn">Sign in</button></div>${error ? `<p class="mute" style="color:var(--error)">${error}</p>` : ""}</form></div></div></body></html>`;

export const dashboardPage = () => String.raw`<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>MDM Dashboard</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500&display=swap">
<style>${STYLE}</style></head>
<body>
<header class="bar"><div class="top"><h1>MDM Dashboard</h1><a href="/logout">Sign out</a></div>
<nav class="tabs" id="tabs"></nav></header>
<main id="main">Loading…</main>
<div id="snack"></div>
<script>
const TABS=[['devices','Devices'],['apps','Apps'],['codes','Codes'],['settings','Settings']];
const DAYS=['S','M','T','W','T','F','S'];
let state=null,devices=[],tab=(location.hash||'#devices').slice(1),search='';
const draft={},open={};

/* ---------- tiny DOM helpers (text only, never innerHTML) ---------- */
function h(tag,attrs){const e=document.createElement(tag);attrs=attrs||{};
 for(const k in attrs){if(k==='class')e.className=attrs[k];else if(k.startsWith('on'))e[k]=attrs[k];else if(attrs[k]!==false&&attrs[k]!=null)e.setAttribute(k,attrs[k])}
 for(let i=2;i<arguments.length;i++){const c=arguments[i];if(c==null||c===false)continue;e.append(c.nodeType?c:document.createTextNode(String(c)))}
 return e}
function snack(t,bad){const s=document.getElementById('snack');s.textContent=t;s.style.background=bad?'var(--error)':'';s.style.color=bad?'#fff':'';s.style.display='block';clearTimeout(snack.t);snack.t=setTimeout(function(){s.style.display='none'},4500)}
async function call(method,path,body){
 const r=await fetch(path,{method:method,headers:{'content-type':'application/json'},body:body&&JSON.stringify(body)});
 const d=await r.json().catch(function(){return{}});
 if(r.status===401)location.reload();
 if(!r.ok)throw new Error(d.error||r.statusText);return d}
function btn(label,cls,fn){const b=h('button',{class:'btn '+(cls||'')},label);
 b.onclick=async function(){b.disabled=true;try{await fn()}catch(e){snack(e.message,1)}b.disabled=false};return b}
function ago(t){if(!t)return 'never';const m=Math.round((Date.now()-t)/60000);return m<1?'just now':m<60?m+' min ago':m<1440?Math.round(m/60)+' h ago':Math.round(m/1440)+' d ago'}
function sw(checked,onchange){const i=h('input',{type:'checkbox'});i.checked=!!checked;i.onchange=function(){onchange(i.checked)};return h('label',{class:'sw'},i,h('i'))}

/* ---------- dialog (resolves to the typed values, or null if cancelled) ---------- */
function ask(title,fields,okLabel,note){
 return new Promise(function(resolve){
  const dlg=h('dialog',{class:'m3dlg'});const inputs={};
  dlg.append(h('h2',null,title));if(note)dlg.append(h('div',{class:'mute small'},note));
  for(const f of fields){
   dlg.append(h('label',null,f.label));let el;
   if(f.options){el=h('select');for(const o of f.options)el.append(new Option(o[1],o[0]));el.value=String(f.value==null?f.options[0][0]:f.value)}
   else{el=h('input',{type:f.type||'text',placeholder:f.placeholder||'',maxlength:f.max||null});el.value=f.value||''}
   inputs[f.key]=el;dlg.append(el)}
  let done=false;const finish=function(v){if(done)return;done=true;dlg.close();dlg.remove();resolve(v)};
  const ok=h('button',{class:'btn'},okLabel||'OK');
  ok.onclick=function(){const v={};for(const k in inputs)v[k]=inputs[k].value;finish(v)};
  const cancel=h('button',{class:'btn outline'},'Cancel');cancel.onclick=function(){finish(null)};
  dlg.addEventListener('cancel',function(e){e.preventDefault();finish(null)});
  dlg.append(h('div',{class:'row'},cancel,ok));document.body.append(dlg);dlg.showModal();
  const first=dlg.querySelector('input,select');if(first)first.focus()})}

/* ---------- data ---------- */
async function load(){
 state=await call('GET','/api/state');devices=await call('GET','/api/devices');render()}
async function saveConfig(msg){await call('PUT','/api/config',state.config);snack(msg||'Saved. Phones update within about a minute.')}

/* ---------- shell ---------- */
function render(){
 const tabs=document.getElementById('tabs');tabs.textContent='';
 for(const t of TABS){const b=h('button',{class:tab===t[0]?'on':''},t[1]);b.onclick=function(){tab=t[0];location.hash=tab;render()};tabs.append(b)}
 const m=document.getElementById('main');m.textContent='';
 ({devices:renderDevices,apps:renderApps,codes:renderCodes,settings:renderSettings}[tab]||renderDevices)(m)}

/* ---------- devices ---------- */
const NAMES={lock:'Lock screen',reboot:'Reboot',wipe:'Wipe',release:'Release device',install:'Install APK',uninstall:'Uninstall app',sync:'Sync','install-result':'Install result','code:install':'Install code used','code:uninstall':'Removal code used',setPin:'Set screen PIN',clearPin:'Remove screen PIN',unlock:'Unlock',addWifi:'Add Wi-Fi'};
function renderDevices(m){
 if(!devices.length){m.append(h('div',{class:'card'},h('h2',null,'No devices yet'),h('p',{class:'mute'},'Go to Codes → Enrollment code for the steps to add a phone.')));return}
 for(const d of devices){
  const online=d.lastSeen&&Date.now()-d.lastSeen<12*60000;
  const hidden=d.packages.filter(function(p){return p.h}).length;
  const want=new Set(d.applied.hide);
  const pending=d.packages.filter(function(p){return want.has(p.p)!==p.h}).length;
  const card=h('div',{class:'card'});
  card.append(h('div',{class:'row'},h('div',{class:'grow'},h('h2',null,d.name),
    h('div',{class:'mute'},'Android '+(d.info.android||'?')+' · last check-in '+ago(d.lastSeen))),
    h('span',{class:'chip '+(online?'ok':'bad')},online?'Online':'Offline')));
  const chips=h('div',{style:'margin:10px 0'});
  chips.append(h('span',{class:'chip'},d.packages.length+' apps'),h('span',{class:'chip'},hidden+' hidden'),
   h('span',{class:'chip '+(pending?'warn':'ok')},pending?pending+' changes pending':'In sync'));
  if(d.info.deviceOwner===false)chips.append(h('span',{class:'chip bad'},'Not device owner!'));
  const bat=d.info.battery;
  if(bat)chips.append(h('span',{class:'chip '+(bat.pct<=15&&!bat.charging?'bad':'')},'🔋 '+bat.pct+'%'+(bat.charging?' charging':'')));
  const wf=d.info.wifi;
  if(wf)chips.append(h('span',{class:'chip'},wf.transport==='wifi'?'📶 '+(wf.ssid||'Wi-Fi')+(wf.rssi?' ('+wf.rssi+' dBm)':''):wf.transport==='mobile'?'📱 Mobile data':'No connection'));
  const lk=d.info.lock;const locked=lk&&lk.until>Date.now();
  if(locked)chips.append(h('span',{class:'chip warn'},'🔒 Locked until '+new Date(lk.until).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})));
  if(d.info.screenLock!==undefined)chips.append(h('span',{class:'chip '+(d.info.screenLock?'ok':'warn')},d.info.screenLock?'Screen lock on':'No screen lock'));
  if(d.info.masterSet===false)chips.append(h('span',{class:'chip warn'},'No master code on phone'));
  const nOv=Object.keys(d.overrides||{}).length;
  if(nOv)chips.append(h('span',{class:'chip warn'},nOv+' app change(s) made on the phone'));
  card.append(chips);

  const acts=h('div');
  if(d.pending)acts.append(h('div',{class:'act'},'⏳ '+d.pending+' command(s) waiting for the phone\'s next check-in'));
  for(const c of d.inflight)acts.append(h('div',{class:'act'},'⏳ '+(NAMES[c.type]||c.type)+' — sent '+ago(c.at)+', waiting for the phone to confirm'));
  for(const r of d.results.slice().reverse().slice(0,6))
   acts.append(h('div',{class:'act'},(r.ok?'✅ ':'❌ ')+(NAMES[r.type]||r.type)+(r.msg?' — '+r.msg:'')+' · '+ago(r.at)));
  if(!acts.children.length)acts.append(h('div',{class:'mute'},'No activity yet.'));
  card.append(h('div',{class:'mute small',style:'margin-top:4px'},'Activity'),acts);

  const row=h('div',{class:'row',style:'margin-top:12px'});
  const cmd=function(label,type,args,confirmMsg,cls){row.append(btn(label,cls||'tonal',async function(){
    if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}
    await call('POST','/api/devices/'+d.id+'/command',{type:type,args:a});snack(label+' queued. The phone runs it at its next check-in (within about a minute).');load()}))};
  cmd('Sync now','sync');
  row.append(btn('Lock…','tonal',async function(){
   const v=await ask('Lock this phone',[
    {key:'minutes',label:'How long',options:[['0','Just lock the screen'],['5','5 minutes'],['15','15 minutes'],['30','30 minutes'],['60','1 hour'],['120','2 hours'],['240','4 hours'],['480','8 hours']]},
    {key:'message',label:'Message shown on the phone (optional)',placeholder:'e.g. Back at 3 PM. Call me if urgent.',max:140}],'Lock',
    'For a timed lock the phone shows your message and a countdown, and nothing else can be opened until time is up (or you unlock it). Emergency calls stay available.');
   if(!v)return;
   await call('POST','/api/devices/'+d.id+'/command',{type:'lock',args:{minutes:parseInt(v.minutes,10),message:v.message}});
   snack('Lock queued. The phone runs it at its next check-in (within about a minute).');load()}));
  if(locked)cmd('Unlock now','unlock',{});
  cmd('Reboot','reboot');
  cmd('Set PIN','setPin',function(){const pin=prompt('New screen lock PIN (4 to 16 digits). Lock only really locks once a PIN is set.');return pin?{pin:pin}:null},null,'tonal');
  cmd('Remove PIN','clearPin',{},'Remove the screen lock PIN?','tonal');
  if(nOv)cmd('Clear phone-side changes','clearOverrides',{},'Forget the app changes made on the phone with the master code?','outline');
  row.append(btn('Add Wi-Fi','tonal',async function(){
   const v=await ask('Add a Wi-Fi network',[{key:'ssid',label:'Network name',max:32},{key:'password',label:'Password (leave empty for an open network)',type:'text',max:63}],'Add to phone',
    'Android does not let apps read saved Wi-Fi passwords, so the dashboard remembers the ones you add here.');
   if(!v||!v.ssid)return;
   await call('POST','/api/devices/'+d.id+'/command',{type:'addWifi',args:{ssid:v.ssid,password:v.password}});
   snack('Wi-Fi network queued.');load()}));
  cmd('Install APK','install',function(){const url=prompt('Direct https:// link to an APK file');return url?{url:url}:null});
  cmd('Uninstall app','uninstall',function(){const p=prompt('Package name to uninstall');return p?{packageName:p}:null});
  cmd('Release device','release',{uninstall:false},'Release this device? It stops being managed and every restriction is removed.','outline');
  cmd('Release & remove app','release',{uninstall:true},'Release the device AND start removing the agent app? The phone will ask to confirm.','outline');
  cmd('Wipe','wipe',null,'ERASE this device completely?','danger');
  row.append(btn('Remove record','outline',async function(){if(!confirm('Delete this device from the dashboard? The phone stays managed; use Release first.'))return;await call('DELETE','/api/devices/'+d.id);load()}));
  card.append(row);
  const log=h('details',{style:'margin-top:12px'},h('summary',{class:'mute'},'Phone log ('+d.events.length+')'));
  const ICON={hide:'🙈',show:'👁️',app:'📦',error:'⚠️',command:'▶️',restriction:'🔒',local:'🔑',security:'🛡️'};
  if(!d.events.length)log.append(h('div',{class:'mute small'},'Nothing logged yet.'));
  for(const e of d.events.slice().reverse())log.append(h('div',{class:'act'},(ICON[e.k]||'•')+' '+e.m+' · '+ago(e.at)));
  card.append(log);
  if(d.wifiNetworks.length){
   const wl=h('details',{style:'margin-top:8px'},h('summary',{class:'mute'},'Wi-Fi networks you added ('+d.wifiNetworks.length+')'));
   for(const n of d.wifiNetworks){const pw=h('span',{class:'mono'},n.password?'••••••••':'(open)');
    const show=h('button',{class:'btn outline',style:'padding:2px 10px;margin-left:8px'},'Show');
    show.onclick=function(){pw.textContent=n.password||'(open)'};
    wl.append(h('div',{class:'act'},n.ssid+' · ',pw,n.password?show:null))}
   card.append(wl)}
  m.append(card)}
 m.append(btn('Refresh','tonal',load));
}

/* ---------- apps ---------- */
function allApps(){
 const seen=new Map();
 for(const d of devices)for(const a of d.packages){
  const o=seen.get(a.p)||{p:a.p,l:a.l,s:a.s,prot:a.protected,hiddenOn:0};if(a.h)o.hiddenOn++;seen.set(a.p,o)}
 for(const p in state.config.apps)if(!seen.has(p))seen.set(p,{p:p,l:state.config.apps[p].label||p,s:false,prot:false,hiddenOn:0});
 return [...seen.values()].sort(function(a,b){return (a.l||a.p).localeCompare(b.l||b.p)})}
function cur(pkg){const c=state.config.apps[pkg];return c?{mode:c.mode,schedule:c.schedule||null}:{mode:'',schedule:null}}
function renderApps(m){
 const pend=new Map();
 for(const d of devices)for(const p of d.applied.pending||[]){const a=d.packages.find(function(x){return x.p===p});pend.set(p,(a&&a.l)||p)}
 if(pend.size){
  const pc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Waiting for your approval ('+pend.size+')'),h('div',{class:'mute'},'New apps stay hidden on the phone until you approve them.'));
  for(const [pkg,label] of pend){
   const img=h('img',{src:'/api/icon/'+pkg,alt:'',style:'width:40px;height:40px;border-radius:10px'});img.onerror=function(){img.replaceWith(h('div',{class:'ph',style:'width:40px;height:40px;border-radius:10px;background:var(--surface-3)'}))};
   pc.append(h('div',{class:'app'},img,h('div',{class:'grow'},h('div',{style:'font-weight:500'},label),h('div',{class:'mute small mono'},pkg),
    h('div',{class:'row',style:'margin-top:8px'},
     btn('Approve','',async function(){state.config.apps[pkg]={mode:'allow',label:label};await saveConfig('Approved '+label+'. It appears on the phone within about a minute.');render()}),
     btn('Block','danger',async function(){state.config.apps[pkg]={mode:'block',label:label};await saveConfig('Blocked '+label+'.');render()})))))}
  m.append(pc)}
 const top=h('div',{class:'card'});
 top.append(h('div',{class:'setting'},h('div',{class:'grow'},h('h2',null,'Hide apps that aren\'t allowed'),
  h('div',{class:'mute'},'When on, every launcher app that isn\'t set to Allow is hidden. Protected system parts are never hidden. Allow the apps you need (phone, messages, maps…) first.')),
  sw(state.config.blockUnlisted,async function(on){
   if(on&&!confirm('Hide every app that is not set to Allow? Make sure phone, messages and maps are allowed first.')){render();return}
   state.config.blockUnlisted=on;try{await saveConfig(on?'Hiding unlisted apps.':'Unlisted apps stay visible.')}catch(e){snack(e.message,1)}render()})));
 const add=h('input',{type:'text',placeholder:'Add by package name, e.g. com.google.android.apps.maps',class:'grow'});
 top.append(h('div',{class:'row',style:'margin-top:8px'},add,btn('Add','tonal',async function(){
  const v=add.value.trim();if(!v)return;state.config.apps[v]={mode:'allow'};await saveConfig('Added '+v);render()})));
 const q=h('input',{type:'text',placeholder:'Search apps',value:search,style:'width:100%;margin-top:8px'});
 q.oninput=function(){search=q.value;const pos=q.selectionStart;render();const n=document.querySelector('#main input[placeholder="Search apps"]');if(n){n.focus();n.setSelectionRange(pos,pos)}};
 top.append(q);m.append(top);

 const list=h('div',{class:'card'});
 const apps=allApps().filter(function(a){const s=search.toLowerCase();return !s||(a.l||'').toLowerCase().includes(s)||a.p.toLowerCase().includes(s)});
 if(!apps.length)list.append(h('div',{class:'mute'},devices.length?'No apps match.':'Apps appear here after a phone checks in.'));
 for(const a of apps)list.append(appRow(a));
 m.append(list)}
function appRow(a){
 const saved=cur(a.p);const d=draft[a.p]||(draft[a.p]={mode:saved.mode,schedule:saved.schedule});
 const dirty=function(){return JSON.stringify(d)!==JSON.stringify({mode:saved.mode,schedule:saved.schedule})};
 const row=h('div',{class:'app'});
 const img=h('img',{src:'/api/icon/'+a.p,alt:'',loading:'lazy'});img.onerror=function(){img.replaceWith(h('div',{class:'ph'}))};
 const body=h('div',{class:'grow'});
 const tags=h('div');
 if(a.s)tags.append(h('span',{class:'chip'},'system'));
 if(a.prot)tags.append(h('span',{class:'chip'},'protected'));
 if(a.hiddenOn)tags.append(h('span',{class:'chip warn'},'hidden on '+a.hiddenOn));
 if(!saved.mode&&state.config.blockUnlisted&&!a.prot)tags.append(h('span',{class:'chip bad'},'will be hidden (default)'));
 body.append(h('div',{style:'font-weight:500'},a.l||a.p),h('div',{class:'mute small mono',style:'word-break:break-all'},a.p),tags);

 const saveBtn=btn('Save','',async function(){
  if(d.mode)state.config.apps[a.p]={mode:d.mode,label:a.l,schedule:d.schedule||undefined};else delete state.config.apps[a.p];
  await saveConfig('Saved '+(a.l||a.p)+'. The phone applies it within about a minute.');delete draft[a.p];render()});
 saveBtn.disabled=!dirty();
 const refresh=function(){saveBtn.disabled=!dirty()};
 const seg=h('div',{class:'seg'});
 for(const o of [['','Default',''],['allow','Allow',''],['block','Block','block']]){
  const b=h('button',{class:(d.mode===o[0]?'on ':'')+o[2]},o[1]);
  b.onclick=function(){d.mode=o[0];if(d.mode==='block')d.schedule=null;render2()};seg.append(b)}
 const render2=function(){render()};
 const ctl=h('div',{class:'row',style:'margin-top:8px'},seg);
 const schedBtn=h('button',{class:'btn tonal'},d.schedule?'Schedule on':'Schedule');
 schedBtn.onclick=function(){open[a.p]=!open[a.p];render()};
 if(d.mode!=='block')ctl.append(schedBtn);
 ctl.append(saveBtn);body.append(ctl);

 if(open[a.p]&&d.mode!=='block'){
  const s=d.schedule||{days:[1,2,3,4,5],from:'08:00',to:'17:00'};
  const box=h('div',{class:'sched'},h('div',{class:'mute'},'Allowed only during this window (phone\'s local time); hidden otherwise.'));
  const days=h('div',{class:'days'});
  DAYS.forEach(function(L,i){const b=h('button',{class:s.days.includes(i)?'on':''},L);
   b.onclick=function(){const set=new Set(s.days);set.has(i)?set.delete(i):set.add(i);s.days=[...set].sort();d.schedule=s;if(!d.mode)d.mode='allow';render()};days.append(b)});
  const from=h('input',{type:'time',value:s.from}),to=h('input',{type:'time',value:s.to});
  from.onchange=function(){s.from=from.value;d.schedule=s;if(!d.mode)d.mode='allow';refresh()};
  to.onchange=function(){s.to=to.value;d.schedule=s;if(!d.mode)d.mode='allow';refresh()};
  box.append(days,h('div',{class:'row'},'From',from,'to',to,btn('Always allowed','outline',async function(){d.schedule=null;render()})));
  if(!d.schedule)box.append(h('div',{class:'mute small'},'Pick days or times to turn the schedule on, then Save.'));
  body.append(box)}
 row.append(img,body);return row}

/* ---------- codes ---------- */
const CODE_INFO={
 enroll:['Enrollment code','Connects a new phone to this dashboard. Used in the adb command or typed into the agent app. Valid 1 hour, one use.'],
 install:['Install-app code','Give this to the person using the phone. In the agent app they tap "Install an app", type the code, and pick an APK file. No "unknown sources" setting needed. One use.'],
 uninstall:['Removal code','Lets the person remove the agent from inside the app. This ends all management of the phone. One use.']};
function renderCodes(m){
 for(const type of ['enroll','install','uninstall']){
  const card=h('div',{class:'card'},h('h2',null,CODE_INFO[type][0]),h('div',{class:'mute'},CODE_INFO[type][1]));
  const out=h('div');
  card.append(h('div',{style:'margin-top:10px'},btn('Generate new code','',async function(){
   const r=await call('POST','/api/codes',{type:type});out.textContent='';
   out.append(h('div',{class:'code'},r.code),h('div',{class:'mute small'},'Valid for 1 hour. Shown once; generate another any time.'));
   if(type==='enroll'){
    out.append(h('div',{class:'mute',style:'margin-top:8px'},'On your computer, with the phone connected and USB debugging on:'),
     h('pre',{class:'cmd'},'adb install mdm-agent.apk\nadb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver\nadb shell am start -n com.familymdm.agent/.MainActivity --es server '+r.server+' --es code '+r.code),
     h('div',{class:'mute small'},'Get mdm-agent.apk from GitHub → Releases → "Latest agent build". The phone needs no Google account when you run the second command.'))}
  })),out);m.append(card)}
}

/* ---------- settings ---------- */
function renderSettings(m){
 const appr=h('div',{class:'card'},h('h2',null,'New apps'));
 appr.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Hold newly installed apps until I approve them'),
  h('div',{class:'mute'},'Lets the person keep the Play Store: anything they install afterwards stays hidden (it cannot be opened) until you approve it on the Apps tab. Apps already on the phone when you switch this on are treated as approved.')),
  sw(state.config.approveNew,async function(on){state.config.approveNew=on;try{await saveConfig(on?'New apps will wait for your approval.':'New apps are no longer held.')}catch(e){snack(e.message,1)}})));
 m.append(appr);
 m.append(h('div',{class:'card'},h('h2',null,'Phone info'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Show which Wi-Fi the phone is on'),
  h('div',{class:'mute'},'Android only reveals the network name when its Location setting is on, so this switch turns that on for the phone. The dashboard shows the network name and signal, never where the phone is. Battery level is always shown.')),
  sw(state.config.reportWifi,async function(on){state.config.reportWifi=on;try{await saveConfig('Saved.')}catch(e){snack(e.message,1)}}))));
 const mc=h('div',{class:'card'},h('h2',null,'Master code'),
  h('div',{class:'mute'},'Works on the phone with no internet (Agent → Administrator): lock, set PIN, install APKs, show/hide apps, release, erase. The phone only stores a scrambled version. Status: '+(state.masterSet?'set':'not set')+'.'));
 const code=h('input',{type:'password',placeholder:'New master code (6+ characters, letters/numbers/symbols)',style:'width:100%;margin-top:8px'});
 mc.append(code,h('div',{class:'row',style:'margin-top:8px'},
  btn(state.masterSet?'Change master code':'Set master code','',async function(){
   const v=code.value;if(!/^[\x20-\x7e]{6,64}$/.test(v)){snack('Use 6 to 64 normal keyboard characters.',1);return}
   const salt=crypto.getRandomValues(new Uint8Array(16));
   const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(v),'PBKDF2',false,['deriveBits']);
   const bits=new Uint8Array(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:salt,iterations:state.masterIterations},key,256));
   const hex=function(a){return [...a].map(function(b){return b.toString(16).padStart(2,'0')}).join('')};
   await call('PUT','/api/master',{salt:hex(salt),hash:hex(bits)});code.value='';state.masterSet=true;snack('Master code saved. Phones receive it at their next check-in.');render()}),
  state.masterSet?btn('Remove','outline',async function(){if(!confirm('Remove the master code? The on-phone admin panel stops working.'))return;await call('DELETE','/api/master');state.masterSet=false;snack('Master code removed.');render()}):null));
 m.append(mc);
 const card=h('div',{class:'card'},h('h2',null,'Restrictions'),h('div',{class:'mute'},'Each switch saves immediately. Phones pick changes up within about a minute.'));
 for(const k in state.restrictions){
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},state.restrictions[k].label),
   sw(state.config.restrictions[k],async function(on){state.config.restrictions[k]=on;try{await saveConfig('Saved.')}catch(e){snack(e.message,1)}})))}
 m.append(card,h('div',{class:'card'},h('h2',null,'Good to know'),
  h('div',{class:'mute'},'Blocking Developer options also turns off USB debugging, so adb stops working. If you need to get back in, send "Release device" from the Devices tab (or use recovery mode).')));
}

load().catch(function(e){snack(e.message,1);document.getElementById('main').textContent='Could not load: '+e.message});
setInterval(function(){if(document.hidden)return;call('GET','/api/devices').then(function(d){devices=d;if(tab==='devices')render()}).catch(function(){})},60000);
</script></body></html>`;
