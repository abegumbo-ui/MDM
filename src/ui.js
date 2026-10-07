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
html{background:var(--surface)}
body{margin:0;background:var(--surface);color:var(--on-surface);font:14px/1.5 Roboto,system-ui,sans-serif;overscroll-behavior-y:none}
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
.dev{display:flex;align-items:center;gap:12px;padding:12px 16px;cursor:pointer}
.dev:hover{background:var(--surface-2)}
.dev .ico{width:44px;height:44px;border-radius:12px;background:var(--primary-container);display:flex;align-items:center;justify-content:center;font-size:22px;flex:none}
.dev .name{font-weight:500;font-size:16px}
.pagetabs{display:flex;gap:8px;overflow-x:auto;margin:12px 0 8px;padding-bottom:4px}
.pagetabs button{white-space:nowrap;border:1px solid var(--outline);background:none;color:var(--on-surface);border-radius:8px;padding:6px 14px;font:500 13px Roboto,system-ui,sans-serif;cursor:pointer}
.pagetabs button.on{background:var(--secondary-container);color:var(--on-secondary-container);border-color:transparent}
.pager{display:flex;gap:12px;overflow-x:auto;scroll-snap-type:x mandatory;align-items:flex-start;scrollbar-width:none}
.pager::-webkit-scrollbar{display:none}
.pager>section{flex:0 0 100%;scroll-snap-align:start;min-width:0}
.kv{display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-top:1px solid var(--outline-variant)}
.kv:first-child{border-top:0}.kv b{font-weight:500;text-align:right;word-break:break-word}
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
<script src="https://cdn.jsdelivr.net/npm/qrcode-generator@1.4.4/qrcode.min.js"></script>
<style>${STYLE}</style></head>
<body>
<header class="bar"><div class="top"><h1>MDM Dashboard</h1><a href="/logout">Sign out</a></div>
<nav class="tabs" id="tabs"></nav></header>
<main id="main">Loading…</main>
<div id="snack"></div>
<script>
const DAYS=['S','M','T','W','T','F','S'];
// Which pager page a device's detail view is on, kept across re-renders (e.g. typing in a search
// box) -- and in sessionStorage too, because a phone browser backgrounded for a minute or so often
// throws this whole tab away and reloads it fresh when you switch back, which looked like "it just
// goes back to Overview on its own" with nothing actually wrong.
let deviceTab=parseInt(sessionStorage.getItem('deviceTab')||'0',10)||0,lastOpenId=sessionStorage.getItem('lastOpenId')||null;
function setDeviceTab(i){deviceTab=i;try{sessionStorage.setItem('deviceTab',i)}catch(e){}}
let state=null,devices=[],browsers=[],windevices=[],latest=null,tab='devices',openId=null,openBrowserId=null,openWinId=null,search='',sysAppSearch='';
function route(){const x=(location.hash||'#devices').slice(1);
 if(x.indexOf('device/')===0){tab='devices';openId=x.slice(7);openBrowserId=null;openWinId=null}
 else if(x.indexOf('browser/')===0){tab='devices';openBrowserId=x.slice(8);openId=null;openWinId=null}
 else if(x.indexOf('win/')===0){tab='devices';openWinId=x.slice(4);openId=null;openBrowserId=null}
 else{tab=x||'devices';openId=null;openBrowserId=null;openWinId=null}}
route();
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

/* ---------- pick an image and shrink it to a small PNG (so uploads stay tiny) ---------- */
const iconVer={};
function pickImage(maxSide,square){
 return new Promise(function(resolve){
  const inp=h('input',{type:'file',accept:'image/*',style:'display:none'});document.body.append(inp);
  inp.onchange=async function(){
   const f=inp.files[0];inp.remove();if(!f){resolve(null);return}
   try{
    const bmp=await createImageBitmap(f);
    let w=bmp.width,hh=bmp.height;const k=Math.min(1,maxSide/Math.max(w,hh));
    const c=document.createElement('canvas');
    if(square){c.width=maxSide;c.height=maxSide;const g=c.getContext('2d');const sc=Math.min(maxSide/w,maxSide/hh);const dw=w*sc,dh=hh*sc;g.drawImage(bmp,(maxSide-dw)/2,(maxSide-dh)/2,dw,dh)}
    else{c.width=Math.max(1,Math.round(w*k));c.height=Math.max(1,Math.round(hh*k));c.getContext('2d').drawImage(bmp,0,0,c.width,c.height)}
    c.toBlob(function(b){resolve(b)},'image/png')
   }catch(e){snack('Could not read that image.',1);resolve(null)}};
  inp.click()})}
async function putImage(path,blob){const r=await fetch(path,{method:'PUT',body:blob});const j=await r.json().catch(function(){return{}});if(!r.ok)throw new Error(j.error||'Upload failed')}

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
 state=await call('GET','/api/state');devices=await call('GET','/api/devices');browsers=await call('GET','/api/browsers');windevices=await call('GET','/api/windevices');
 for(const b of browsers)b.isBrowser=true;
 render();
 call('GET','/api/latest-agent').then(function(r){if(JSON.stringify(r.latest)!==JSON.stringify(latest)){latest=r.latest;render()}}).catch(function(){})}
async function saveConfigFor(d,msg){await call('PUT',(d.isBrowser?'/api/browsers/':'/api/devices/')+d.id+'/config',d.config);snack(msg||'Saved. Phones update within about 15 seconds.')}

/* ---------- shell ---------- */
// Everything is sandboxed to its own thing: the top bar only ever says "Devices". Apps and Sites
// live inside each device's (or Browser's) own page, scoped to it. Codes and Settings are the only
// truly account-wide things with nowhere specific to live, so they're reached from a button on the
// bare list instead of a permanent tab.
function render(){
 const tabs=document.getElementById('tabs');tabs.textContent='';
 const home=h('button',{class:(tab==='devices'&&!openId&&!openBrowserId)?'on':''},'Devices');
 home.onclick=function(){location.hash='devices'};
 tabs.append(home);
 const m=document.getElementById('main');m.textContent='';
 if(tab==='devices'&&openId){const d=devices.find(function(x){return x.id===openId});if(d){renderDeviceDetail(m,d);return}openId=null}
 if(tab==='devices'&&openBrowserId){const b=browsers.find(function(x){return x.id===openBrowserId});if(b){renderBrowserDetail(m,b);return}openBrowserId=null}
 if(tab==='devices'&&openWinId){const w=windevices.find(function(x){return x.id===openWinId});if(w){renderWinDetail(m,w);return}openWinId=null}
 if(tab==='codes'||tab==='settings'){
  const back=h('button',{class:'btn outline'},'‹ Devices');back.onclick=function(){location.hash='devices'};
  m.append(h('div',{class:'row'},back));
  (tab==='codes'?renderCodes:renderSettings)(m);
  return}
 renderDevices(m)}
window.addEventListener('hashchange',function(){route();window.scrollTo(0,0);render()});

/* ---------- devices ---------- */
const NAMES={lock:'Lock screen',reboot:'Reboot',wipe:'Wipe',release:'Release device',install:'Install APK',uninstall:'Uninstall app',sync:'Sync','install-result':'Install result','code:install':'Install code used','code:uninstall':'Removal code used',setPin:'Set screen PIN',clearPin:'Remove screen lock',unlock:'Unlock',addWifi:'Add Wi-Fi',resetAppCode:'Reset app code',updateAgent:'Update agent','uninstall-result':'Uninstall result',listSystemApps:'Scan for hidden system apps',locate:'Find location'};
const ICON={hide:'🙈',show:'👁️',app:'📦',error:'⚠️',command:'▶️',restriction:'🔒',local:'🔑',security:'🛡️',update:'⬆️',lock:'🔒',watchdog:'👀'};
function isOnline(d){return d.lastSeen&&Date.now()-d.lastSeen<12*60000}
function lockedNow(d){const lk=d.info.lock;return lk&&lk.until>Date.now()}
function browsingFreely(d){return d.info.browseUntil&&d.info.browseUntil>Date.now()}
function needsUpdate(d){return latest&&d.info.versionCode&&d.info.versionCode<latest.versionCode}
function pendingApps(d){const want=new Set(d.applied.hide);return d.packages.filter(function(p){return want.has(p.p)!==p.h})}
function pendingCount(d){return pendingApps(d).length}
function chipsFor(d,full){
 const c=h('div',{style:'margin-top:6px'});
 if(d.syncPaused)c.append(h('span',{class:'chip warn'},'⏸️ Not connected (paused on the phone)'));
 else c.append(h('span',{class:'chip '+(isOnline(d)?'ok':'bad')},isOnline(d)?'Online':'Offline'));
 const bat=d.info.battery;
 if(bat)c.append(h('span',{class:'chip '+(bat.pct<=15&&!bat.charging?'bad':'')},'🔋 '+bat.pct+'%'+(bat.charging?' ⚡':'')));
 const wf=d.info.wifi;
 if(wf)c.append(h('span',{class:'chip'},wf.transport==='wifi'?'📶 '+(wf.ssid||'Wi-Fi'):wf.transport==='mobile'?'📱 Mobile':'No connection'));
 if(d.inflight.some(function(x){return x.type==='lock'})||d.pending)c.append(h('span',{class:'chip warn'},'⏳ Command on its way'));
 if(lockedNow(d))c.append(h('span',{class:'chip warn'},'🔒 Locked until '+new Date(d.info.lock.until).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
 if(browsingFreely(d))c.append(h('span',{class:'chip warn'},'🌐 Free browsing until '+new Date(d.info.browseUntil).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
 if(d.info.kiosk)c.append(h('span',{class:'chip ok'},'🏠 Home screen mode'));
 if(d.info.kioskPaused)c.append(h('span',{class:'chip warn'},'Home screen mode paused'));
 if(d.info.deviceOwner===false)c.append(h('span',{class:'chip bad'},'Not device owner!'));
 if(needsUpdate(d))c.append(h('span',{class:'chip warn'},'⬆️ Update available'));
 const n=pendingCount(d);if(n&&full)c.append(h('span',{class:'chip warn',title:pendingApps(d).map(function(p){return p.l||p.p}).join(', ')},n+' changes pending'));
 return c}
function renderDevices(m){
 if(!devices.length&&!browsers.length&&!windevices.length){
  m.append(h('div',{class:'card'},h('h2',null,'No devices yet'),h('p',{class:'mute'},'Get a code below to add a phone or a Browser.')));
 }else{
  for(const d of devices){
   const card=h('div',{class:'card dev'});
   card.onclick=function(){location.hash='device/'+d.id};
   card.append(h('div',{class:'ico'},'📱'),h('div',{class:'grow'},h('div',{class:'name'},d.name)),h('div',{class:'mute',style:'font-size:22px'},'›'));
   m.append(card)}
  for(const b of browsers){
   const card=h('div',{class:'card dev'});
   card.onclick=function(){location.hash='browser/'+b.id};
   card.append(h('div',{class:'ico'},'🌐'),h('div',{class:'grow'},h('div',{class:'name'},'Browser'),h('div',{class:'mute small'},b.name+' · no MDM on this phone')),h('div',{class:'mute',style:'font-size:22px'},'›'));
   m.append(card)}
  for(const w of windevices){
   const card=h('div',{class:'card dev'});
   card.onclick=function(){location.hash='win/'+w.id};
   card.append(h('div',{class:'ico'},'💻'),h('div',{class:'grow'},h('div',{class:'name'},w.name),h('div',{class:'mute small'},(w.info.hostname||'LockGuard')+' · '+(w.enabled?'Locked down':'Not locked down'))),h('div',{class:'mute',style:'font-size:22px'},'›'));
   m.append(card)}
  m.append(h('div',{class:'mute small',style:'margin:4px 4px 12px'},'Tap one to see everything and control it — apps, sites, and all its own controls live there.'));
 }
 const row=h('div',{class:'row'});
 row.append(btn('Refresh','tonal',load));
 const codes=h('button',{class:'btn outline'},'Codes');codes.onclick=function(){location.hash='codes'};
 const settings=h('button',{class:'btn outline'},'Settings');settings.onclick=function(){location.hash='settings'};
 row.append(codes,settings);
 m.append(row);
}
function kv(k,v){return h('div',{class:'kv'},h('span',{class:'mute'},k),h('b',null,v))}
function resetCard(d){
 const i=d.info,rs=i.restrictions||[];
 const rows=[];
 rows.push(i.bootloader==='locked'?[1,'Bootloader locked','Nobody can flash or erase the phone from a computer.']
  :i.bootloader==='unlocked'?[0,'Bootloader is UNLOCKED','With an unlocked bootloader the protection can be erased from a computer. Use a phone whose bootloader stays locked.']
  :[2,'Bootloader state unknown','The phone did not report it.']);
 rows.push(i.frpSupported===false?[0,'Android is too old for reset protection','Android 11 or newer is needed.']
  :i.frpAccounts>0?[1,'Reset protection is set','After a reset from recovery mode, setup demands '+i.frpAccounts+' Google account(s) you chose.']
  :[0,'Reset protection is not set','Add your Google account ID under Settings → Factory Reset Protection.']);
 rows.push(rs.indexOf('no_factory_reset')>=0?[1,'Reset from Settings is blocked','']:[0,'Reset from Settings is not blocked','Turn on "Block factory reset from Settings".']);
 rows.push(rs.indexOf('no_safe_boot')>=0?[1,'Safe Mode is blocked','']:[0,'Safe Mode is not blocked','Turn on "Block Safe Mode".']);
 rows.push(rs.indexOf('no_debugging_features')>=0?[1,'Developer options and USB debugging are blocked','']:[0,'Developer options / USB debugging are not blocked','Turn on "Block Developer options and USB debugging" when you are done setting up.']);
 if(i.securityPatch){const months=(Date.now()-new Date(i.securityPatch).getTime())/2629800000;
  rows.push(months<=12?[1,'Security patch '+i.securityPatch,'Recent enough.']:[2,'Security patch '+i.securityPatch+' is old','Old patches have known ways around reset protection. Install system updates.'])}
 const strong=rows.every(function(r){return r[0]===1});
 const card=h('div',{class:'card'},h('div',{class:'row'},h('h2',{class:'grow'},'Reset protection'),h('span',{class:'chip '+(strong?'ok':'warn')},strong?'Strong':'Not yet strong')));
 for(const r of rows)card.append(h('div',{class:'kv'},h('span',null,(r[0]===1?'✅ ':r[0]===0?'❌ ':'⚠️ ')+r[1]),h('span',{class:'mute small',style:'text-align:right;max-width:55%'},r[2])));
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},'No phone protection is unbreakable. This checks the known ways around a reset: resetting from Settings, Safe Mode, USB debugging, an unlocked bootloader, an old system, and simply setting the phone up again.'));
 return card}
function renderDeviceDetail(m,d){
 if(d.id!==lastOpenId){lastOpenId=d.id;try{sessionStorage.setItem('lastOpenId',d.id)}catch(e){}setDeviceTab(0)}
 const back=h('button',{class:'btn outline'},'‹ All phones');back.onclick=function(){location.hash='devices'};
 m.append(h('div',{class:'row'},back,h('div',{class:'grow'}),btn('Refresh','tonal',load)));
 m.append(h('div',{class:'row',style:'margin-top:12px'},h('div',{class:'ico dev',style:'width:44px;height:44px;border-radius:12px;background:var(--primary-container);display:flex;align-items:center;justify-content:center;font-size:22px;flex:none;padding:0'},'📱'),
  h('div',{class:'grow'},h('h2',{style:'font-size:20px'},d.name),chipsFor(d,true))));

 const queue=async function(label,type,args){await call('POST','/api/devices/'+d.id+'/command',{type:type,args:args||{}});snack(label+' queued. The phone runs it at its next check-in (within about 15 seconds).');load()};
 const cmd=function(box,label,type,args,confirmMsg,cls){box.append(btn(label,cls||'tonal',async function(){
   if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}await queue(label,type,a)}))};

 // ----- Overview -----
 const ov=h('section');
 if(d.syncPaused)ov.append(h('div',{class:'card',style:'border-color:var(--warn)'},h('h2',null,'Not connected'),h('div',{class:'mute'},'This device is not connected to the dashboard because it was turned off (from this phone\'s own Admin screen, to save battery). It keeps enforcing whatever it had last. It reconnects only when someone turns it back on there — nothing here can reach it in the meantime.')));
 const info=h('div',{class:'card'},h('h2',null,'Phone'));
 info.append(kv('Recovery code',d.fallbackCode||'not seen yet (needs a check-in on a current build)'),
  kv('Android',d.info.android||'?'),kv('Agent build',(d.info.versionCode||'?')+(latest?' (latest '+latest.versionCode+')':'')),
  kv('Last check-in',ago(d.lastSeen)),kv('Battery',d.info.battery?d.info.battery.pct+'%'+(d.info.battery.charging?' (charging)':''):'unknown'),
  kv('Connection',d.info.wifi?(d.info.wifi.transport==='wifi'?'Wi-Fi '+(d.info.wifi.ssid||'(name hidden)')+(d.info.wifi.rssi?' · '+d.info.wifi.rssi+' dBm':''):d.info.wifi.transport==='mobile'?'Mobile data':'None'):'unknown'),
  kv('Screen lock',d.info.screenLock===undefined?'unknown':d.info.screenLock?'On':'Off'),
  kv('PIN control',d.info.pinControl===undefined?'unknown':d.info.pinControl?'Ready: you can set or remove the lock':'Not active yet (see Controls)'),
  kv('Apps',d.packages.length+' ('+d.packages.filter(function(p){return p.h}).length+' hidden)'),
  kv('Sync',pendingCount(d)?pendingApps(d).map(function(p){const want=new Set(d.applied.hide);return (p.l||p.p)+(want.has(p.p)?' (hiding)':' (showing)')}).join(', ')+' — applies within about 15 seconds':'In sync'));
 if(d.adminPin&&d.adminPin.ok){
  const pinV=h('b',null,'••••');const showPin=h('button',{class:'btn outline',style:'padding:2px 10px;margin-left:8px'},'Show');showPin.onclick=function(){pinV.textContent=d.adminPin.pin};
  info.append(h('div',{class:'kv'},h('span',{class:'mute'},'PIN you set'),h('span',null,pinV,showPin)))}
 const nOv=Object.keys(d.overrides||{}).length;
 if(nOv)info.append(kv('Changed on the phone',nOv+' app(s)'));
 if((d.messages||[]).length){
  const mc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Messages from the phone ('+d.messages.length+')'));
  for(const msg of d.messages.slice().reverse()){
   mc.append(h('div',{class:'app'},h('div',{class:'grow'},h('div',null,msg.msg),h('div',{class:'mute small'},ago(msg.at))),
    btn('Dismiss','outline',async function(){await call('DELETE','/api/devices/'+d.id+'/messages?id='+encodeURIComponent(msg.id));load()})))}
  ov.append(mc)}
 ov.append(info);
 ov.append(resetCard(d));
 const acts=h('div',{class:'card'},h('h2',null,'Activity'));
 if(d.pending)acts.append(h('div',{class:'act'},'⏳ '+d.pending+' command(s) waiting for the phone\'s next check-in'));
 for(const c of d.inflight)acts.append(h('div',{class:'act'},'⏳ '+(NAMES[c.type]||c.type)+' — sent '+ago(c.at)+', waiting for the phone to confirm'));
 for(const r of d.results.slice().reverse().slice(0,6).filter(function(r){return r.type!=='listSystemApps'&&r.type!=='locate'}))acts.append(h('div',{class:'act'},(r.ok?'✅ ':'❌ ')+(NAMES[r.type]||r.type)+(r.msg?' — '+r.msg:'')+' · '+ago(r.at)));
 if(acts.children.length===1)acts.append(h('div',{class:'mute'},'No activity yet.'));
 ov.append(acts);

 // ----- Controls -----
 const ct=h('section');
 const lockBox=h('div',{class:'card'},h('h2',null,'Lock'));const lr=h('div',{class:'row',style:'margin-top:8px'});
 lr.append(btn('Lock…','',async function(){
   const v=await ask('Lock this phone',[
    {key:'minutes',label:'How long',options:[['0','Just lock the screen'],['5','5 minutes'],['15','15 minutes'],['30','30 minutes'],['60','1 hour'],['120','2 hours'],['240','4 hours'],['480','8 hours']]},
    {key:'message',label:'Message shown on the phone (optional)',placeholder:'e.g. Back at 3 PM. Call me if urgent.',max:140}],'Lock',
    'For a timed lock the phone shows your message and a countdown, and nothing else can be opened until time is up (or you unlock it). Emergency calls stay available.');
   if(!v)return;await queue('Lock','lock',{minutes:parseInt(v.minutes,10),message:v.message})}));
 cmd(lr,'Unlock now','unlock',{},null,lockedNow(d)?'':'outline');
 cmd(lr,'Set PIN','setPin',function(){const pin=prompt('New screen lock PIN (4 to 16 digits). Lock only really locks once a PIN is set.');return pin?{pin:pin}:null});
 cmd(lr,'Remove screen lock','clearPin',{},'Take the screen lock (PIN, pattern or password) off this phone?','outline');
 lockBox.append(lr);
 lockBox.append(h('div',{class:'mute small',style:'margin-top:10px'},'Nobody can read a PIN or pattern the person chose themselves, not even the phone\'s maker. What you can do: take it off (Remove screen lock) or replace it (Set PIN), which needs PIN control to be ready (Overview). A PIN you set here is shown on the Overview page. To make sure every lock is one you set, switch on \'Only the administrator can set the screen lock\' in Settings.'));
 ct.append(lockBox);

 const appBox=h('div',{class:'card'},h('h2',null,'Apps'));const ar=h('div',{class:'row',style:'margin-top:8px'});
 const file=h('input',{type:'file',accept:'.apk,application/vnd.android.package-archive',style:'display:none'});
 file.onchange=async function(){const f=file.files[0];file.value='';if(!f)return;
  if(f.size>24*1024*1024){snack('That file is '+Math.round(f.size/1048576)+' MB. The limit is 24 MB. Use a download link for bigger apps.',1);return}
  snack('Uploading '+f.name+'…');
  try{const r=await fetch('/api/apk?name='+encodeURIComponent(f.name),{method:'PUT',body:f});const j=await r.json();if(!r.ok)throw new Error(j.error||'Upload failed');
   await queue('Install '+f.name,'install',{apkId:j.id})}catch(e){snack(e.message,1)}};
 ar.append(file,btn('Upload APK from this computer…','',async function(){file.click()}));
 cmd(ar,'Install from link…','install',function(){const url=prompt('Direct https:// link to an APK file');return url?{url:url}:null});
 cmd(ar,'Uninstall by package…','uninstall',function(){const p=prompt('Package name to uninstall');return p?{packageName:p}:null});
 appBox.append(ar,h('div',{class:'mute small',style:'margin-top:8px'},'Uploads are kept for a week and limited to 24 MB. For bigger apps use a link, or let the person install from the Play Store with approval mode (Settings).'));
 ct.append(appBox);

 const codeBox=h('div',{class:'card'},h('h2',null,'Codes for this phone'),
  h('div',{class:'mute'},'Locked to this phone — giving the code to a different phone does nothing. Valid 1 hour, one use.'));
 const genCode=async function(type,minutes){
  const r=await call('POST','/api/codes',{type:type,deviceId:d.id,minutes:minutes});
  out.textContent='';out.append(h('div',{class:'code'},r.code),
   h('div',{class:'mute small'},type==='freebrowse'?'Opens any site on this phone\'s Browser for '+r.minutes+' minutes.':'Type on this phone: '+(type==='install'?'Install an app.':'Remove agent.')))};
 const out=h('div',{style:'margin-top:10px'});
 codeBox.append(h('div',{class:'row',style:'margin-top:8px'},
  btn('Install code','',function(){genCode('install')}),
  btn('Removal code','outline',function(){genCode('uninstall')}),
  btn('Browse-freely code','outline',async function(){
   const v=await ask('Browse freely for how long?',[{key:'minutes',label:'Duration',options:[['15','15 minutes'],['30','30 minutes'],['60','1 hour'],['120','2 hours'],['240','4 hours']],value:'60'}],'Generate');
   if(!v)return;genCode('freebrowse',parseInt(v.minutes,10))})),out);
 ct.append(codeBox);

 const devBox=h('div',{class:'card'},h('h2',null,'Phone'));const dr=h('div',{class:'row',style:'margin-top:8px'});
 cmd(dr,'Sync now','sync');cmd(dr,'Reboot','reboot');
 if(needsUpdate(d))cmd(dr,'Update agent to build '+latest.versionCode,'updateAgent',{});else cmd(dr,'Update agent','updateAgent',{},null,'outline');
 if(nOv)cmd(dr,'Clear phone-side changes','clearOverrides',{},'Forget the app changes made on the phone with the master code?','outline');
 devBox.append(dr);
 const dd=h('div',{class:'row',style:'margin-top:12px'});
 cmd(dd,'Release device','release',{uninstall:false},'Release this device? It stops being managed and every restriction is removed.','outline');
 cmd(dd,'Release & remove app','release',{uninstall:true},'Release the device AND start removing the agent app? The phone will ask to confirm.','outline');
 cmd(dd,'Wipe','wipe',null,'ERASE this device completely?','danger');
 dd.append(btn('Delete device','outline',async function(){if(!confirm('Delete this device from the dashboard? The phone stays managed; use Release first if you want to actually free the phone.'))return;await call('DELETE','/api/devices/'+d.id);location.hash='devices'}));
 devBox.append(dd);ct.append(devBox);

 // ----- Location (on demand -- a single fix per tap, never continuous tracking) -----
 const loc=h('section');const locCard=h('div',{class:'card'},h('h2',null,'Location'),
  h('div',{class:'mute'},'One fix at a time, only when you ask -- nothing here tracks the phone continuously or stores a history of where it\'s been.'));
 locCard.append(h('div',{style:'margin-top:8px'},btn('Find now','tonal',async function(){
  await call('POST','/api/devices/'+d.id+'/command',{type:'locate',args:{}});
  snack('Asked the phone to find itself. It reports back at its next check-in (within about 15 seconds) -- tap Refresh after a moment.');load()})));
 const lastFix=d.results.slice().reverse().find(function(r){return r.type==='locate'});
 if(!lastFix)locCard.append(h('div',{class:'mute small',style:'margin-top:8px'},'Not found yet.'));
 else if(!lastFix.ok)locCard.append(h('div',{class:'mute small',style:'margin-top:8px'},'Last attempt failed: '+lastFix.msg));
 else{
  let fix=null;try{fix=JSON.parse(lastFix.msg)}catch(e){}
  if(fix)locCard.append(h('div',{style:'margin-top:8px'},
   h('div',null,fix.lat.toFixed(6)+', '+fix.lon.toFixed(6)+' (accurate to about '+Math.round(fix.accuracy)+'m)'),
   h('div',{class:'mute small'},'As of '+ago(lastFix.at)+'.'),
   h('a',{href:'https://maps.google.com/?q='+fix.lat+','+fix.lon,target:'_blank',rel:'noopener',style:'display:inline-block;margin-top:4px'},'Open in Google Maps ↗')));
  else locCard.append(h('div',{class:'mute small',style:'margin-top:8px'},'Could not read the last result.'))}
 loc.append(locCard);

 // ----- Apps on this phone -----
 const ap=h('section');const al=h('div',{class:'card'},h('h2',null,'Apps on this phone'),h('div',{class:'mute'},'Every app actually installed here, with the same Default / Allow / Block / Schedule controls as App rules.'));
 for(const a of d.packages)al.append(appRow({p:a.p,l:a.l,s:a.s,prot:a.protected,hiddenOn:a.h?1:0},d));
 if(!d.packages.length)al.append(h('div',{class:'mute'},'The phone has not reported its apps yet.'));
 ap.append(al);

 // ----- System apps (hidden from the launcher, e.g. a lock-screen component) -----
 const sa=h('section');const sal=h('div',{class:'card'},h('h2',null,'System apps'),
  h('div',{class:'mute'},'Apps built into the phone with no icon of their own -- not what shows in "On this phone". Scanning asks the phone directly; it is not kept in sync automatically. Blocking one of these needs extra confirmation: it can break a part of the phone.'));
 sal.append(h('div',{style:'margin-top:8px'},btn('Scan for hidden system apps','tonal',async function(){
  await call('POST','/api/devices/'+d.id+'/command',{type:'listSystemApps',args:{}});
  snack('Scanning. The phone reports back at its next check-in (within about 15 seconds) -- tap Refresh after a moment.');load()})));
 const lastScan=d.results.slice().reverse().find(function(r){return r.type==='listSystemApps'});
 if(!lastScan)sal.append(h('div',{class:'mute small',style:'margin-top:8px'},'Not scanned yet.'));
 else if(!lastScan.ok)sal.append(h('div',{class:'mute small',style:'margin-top:8px'},'Last scan failed: '+lastScan.msg));
 else{
  let sysApps=[];try{sysApps=JSON.parse(lastScan.msg)}catch(e){}
  sal.append(h('div',{class:'mute small',style:'margin-top:8px'},sysApps.length+' found, as of '+ago(lastScan.at)+'.'));
  const sq=h('input',{type:'text',placeholder:'Search system apps',value:sysAppSearch,style:'width:100%;margin-top:8px'});
  sq.oninput=function(){sysAppSearch=sq.value;const pos=sq.selectionStart;render();const n=document.querySelector('#main input[placeholder="Search system apps"]');if(n){n.focus();n.setSelectionRange(pos,pos)}};
  sal.append(sq);
  const shown=sysApps.filter(function(a){const s=sysAppSearch.toLowerCase();return !s||(a.l||'').toLowerCase().includes(s)||a.p.toLowerCase().includes(s)});
  for(const a of shown)sal.append(appRow({p:a.p,l:a.l,s:true,prot:false,hiddenOn:a.h?1:0},d,{triple:true}));
  if(!sysApps.length)sal.append(h('div',{class:'mute'},'No hidden system apps found.'));
  else if(!shown.length)sal.append(h('div',{class:'mute'},'No system apps match.'))}
 sa.append(sal);

 // ----- Log -----
 const lg=h('section');
 const pl=h('div',{class:'card'},h('h2',null,'Dashboard log'),h('div',{class:'mute'},'What was sent from here. Compare against Phone log below to see whether it actually arrived -- it should, within about 15 seconds.'));
 if(!d.pushLog.length)pl.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing sent yet.'));
 for(const p of d.pushLog.slice().reverse()){
  const row=h('div',{class:'act'},'📤 '+ago(p.at));
  for(const c of p.changes)row.append(h('div',{class:'mute small',style:'margin-left:20px'},c));
  pl.append(row)}
 lg.append(pl);
 const ll=h('div',{class:'card'},h('h2',null,'Phone log'),h('div',{class:'mute'},'What the phone itself did and noticed.'));
 if(!d.events.length)ll.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing logged yet.'));
 for(const e of d.events.slice().reverse())ll.append(h('div',{class:'act'},(ICON[e.k]||'•')+' '+e.m+' · '+ago(e.at)));
 lg.append(ll);

 // ----- Network -----
 const nw=h('section');const wl=h('div',{class:'card'},h('h2',null,'Wi-Fi'));
 wl.append(kv('Now',d.info.wifi?(d.info.wifi.transport==='wifi'?(d.info.wifi.ssid||'(name hidden: Location is off)'):d.info.wifi.transport==='mobile'?'Mobile data':'No connection'):'unknown'));
 for(const n of d.wifiNetworks){const pw=h('span',{class:'mono'},n.password?'••••••••':'(open)');
  const show=h('button',{class:'btn outline',style:'padding:2px 10px;margin-left:8px'},'Show');show.onclick=function(){pw.textContent=n.password||'(open)'};
  wl.append(h('div',{class:'kv'},h('span',null,n.ssid),h('span',null,pw,n.password?show:null)))}
 wl.append(h('div',{class:'mute small',style:'margin-top:8px'},'Android does not let apps read saved Wi-Fi passwords, so networks you add here are remembered for you.'));
 wl.append(h('div',{style:'margin-top:8px'},btn('Add Wi-Fi network…','',async function(){
   const v=await ask('Add a Wi-Fi network',[{key:'ssid',label:'Network name',max:32},{key:'password',label:'Password (leave empty for an open network)',max:63}],'Add to phone');
   if(!v||!v.ssid)return;await queue('Add Wi-Fi','addWifi',{ssid:v.ssid,password:v.password})})));
 nw.append(wl);

 // ----- App rules, Sites, Settings: this device's own, independent of every other device -----
 const arules=h('section');renderApps(arules,d);
 const st=h('section');renderSites(st,d);
 const se=h('section');renderDeviceSettings(se,d);

 // ----- pager -----
 const parts=[['Overview',ov],['Controls',ct],['Location',loc],['On this phone',ap],['App rules',arules],['System apps',sa],['Sites',st],['Settings',se],['Log',lg],['Network',nw]];
 const tabsRow=h('div',{class:'pagetabs'});const pager=h('div',{class:'pager'});
 parts.forEach(function(p,i){const b=h('button',{class:i===deviceTab?'on':''},p[0]);b.onclick=function(){setDeviceTab(i);pager.scrollTo({left:i*pager.clientWidth,behavior:'smooth'})};tabsRow.append(b);pager.append(p[1])});
 pager.onscroll=function(){const i=Math.round(pager.scrollLeft/Math.max(pager.clientWidth,1));setDeviceTab(i);[...tabsRow.children].forEach(function(b,j){b.className=j===i?'on':''})};
 m.append(tabsRow,pager,h('div',{class:'mute small',style:'margin-top:8px;text-align:center'},'Swipe sideways or tap a tab'));
 // A re-render (e.g. typing in a search box inside a pager page) rebuilds this whole pager from
 // scratch, which would otherwise always snap back to the first tab — jump straight back instead.
 if(deviceTab)requestAnimationFrame(function(){pager.scrollTo({left:deviceTab*pager.clientWidth})});
}

/* ---------- apps ---------- */
function allApps(dv){
 const seen=new Map();
 for(const a of dv.packages){
  const o=seen.get(a.p)||{p:a.p,l:a.l,s:a.s,prot:a.protected,hiddenOn:0};if(a.h)o.hiddenOn++;seen.set(a.p,o)}
 for(const p in dv.config.apps)if(!seen.has(p))seen.set(p,{p:p,l:dv.config.apps[p].label||p,s:false,prot:false,hiddenOn:0});
 return [...seen.values()].sort(function(a,b){return (a.l||a.p).localeCompare(b.l||b.p)})}
function cur(dv,pkg){const c=dv.config.apps[pkg];return c?{mode:c.mode,schedule:c.schedule||null}:{mode:'',schedule:null}}
function renderApps(m,dv){
 const pend=new Map();
 for(const p of dv.applied.pending||[]){const a=dv.packages.find(function(x){return x.p===p});pend.set(p,(a&&a.l)||p)}
 if(pend.size){
  const pc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Waiting for your approval ('+pend.size+')'),h('div',{class:'mute'},'New apps stay hidden on the phone until you approve them.'));
  for(const [pkg,label] of pend){
   const img=h('img',{src:'/api/icon/'+pkg,alt:'',style:'width:40px;height:40px;border-radius:10px'});img.onerror=function(){img.replaceWith(h('div',{class:'ph',style:'width:40px;height:40px;border-radius:10px;background:var(--surface-3)'}))};
   pc.append(h('div',{class:'app'},img,h('div',{class:'grow'},h('div',{style:'font-weight:500'},label),h('div',{class:'mute small mono'},pkg),
    h('div',{class:'row',style:'margin-top:8px'},
     btn('Approve','',async function(){dv.config.apps[pkg]={mode:'allow',label:label};await saveConfigFor(dv,'Approved '+label+'. It appears on the phone within about 15 seconds.');render()}),
     btn('Block','danger',async function(){dv.config.apps[pkg]={mode:'block',label:label};await saveConfigFor(dv,'Blocked '+label+'.');render()})))))}
  m.append(pc)}
 const top=h('div',{class:'card'});
 top.append(h('div',{class:'setting'},h('div',{class:'grow'},h('h2',null,'Hide apps that aren\'t allowed'),
  h('div',{class:'mute'},'Block switches an app off completely, as if it were uninstalled for the person.'+(dv.config.homeScreen?' Soft block only shows up while Home screen mode is on (Settings box): the app has no icon there but keeps running.':'')+' Protected core parts are never hidden. When this switch is on, every app that isn\'t set to Allow is Blocked. Allow the apps you need (phone, messages, maps…) first.')),
  sw(dv.config.blockUnlisted,async function(on){
   if(on&&!confirm('Hide every app that is not set to Allow? Make sure phone, messages and maps are allowed first.')){render();return}
   dv.config.blockUnlisted=on;try{await saveConfigFor(dv,on?'Hiding unlisted apps.':'Unlisted apps stay visible.')}catch(e){snack(e.message,1)}render()})));
 const add=h('input',{type:'text',placeholder:'Add by package name, e.g. com.google.android.apps.maps',class:'grow'});
 top.append(h('div',{class:'row',style:'margin-top:8px'},add,btn('Add','tonal',async function(){
  const v=add.value.trim();if(!v)return;dv.config.apps[v]={mode:'allow'};await saveConfigFor(dv,'Added '+v);render()})));
 const q=h('input',{type:'text',placeholder:'Search apps',value:search,style:'width:100%;margin-top:8px'});
 q.oninput=function(){search=q.value;const pos=q.selectionStart;render();const n=document.querySelector('#main input[placeholder="Search apps"]');if(n){n.focus();n.setSelectionRange(pos,pos)}};
 top.append(q);m.append(top);

 const list=h('div',{class:'card'});
 const apps=allApps(dv).filter(function(a){const s=search.toLowerCase();return !s||(a.l||'').toLowerCase().includes(s)||a.p.toLowerCase().includes(s)});
 if(!apps.length)list.append(h('div',{class:'mute'},devices.length?'No apps match.':'Apps appear here after a phone checks in.'));
 for(const a of apps)list.append(appRow(a,dv));
 m.append(list)}
function appRow(a,dv,opts){
 opts=opts||{};
 const saved=cur(dv,a.p);const d=draft[a.p]||(draft[a.p]={mode:saved.mode,schedule:saved.schedule});
 const dirty=function(){return JSON.stringify(d)!==JSON.stringify({mode:saved.mode,schedule:saved.schedule})};
 const row=h('div',{class:'app'});
 const img=h('img',{src:'/api/icon/'+a.p+'?v='+(iconVer[a.p]||0),alt:'',loading:'lazy'});img.onerror=function(){img.replaceWith(h('div',{class:'ph'}))};
 const body=h('div',{class:'grow'});
 const tags=h('div');
 if(a.s)tags.append(h('span',{class:'chip'},'system app · cannot be uninstalled, use Block to switch it off'));else tags.append(h('span',{class:'chip ok'},'can be uninstalled'));
 if(a.prot)tags.append(h('span',{class:'chip'},'protected'));
 if(a.hiddenOn)tags.append(h('span',{class:'chip warn'},'currently blocked on this phone'));
 if(!saved.mode&&dv.config.blockUnlisted&&!a.prot)tags.append(h('span',{class:'chip bad'},'will be hidden (default)'));
 const ov=(dv.overrides||{})[a.p];
 if(ov)tags.append(h('span',{class:'chip bad'},'set to "'+ov+'" on the phone itself — overrides this box until cleared in Controls'));
 body.append(h('div',{style:'font-weight:500'},a.l||a.p),h('div',{class:'mute small mono',style:'word-break:break-all'},a.p),tags);

 const saveBtn=btn('Save','',async function(){
  if(opts.triple&&d.mode&&d.mode!=='allow'){
   const name=a.l||a.p;
   if(!confirm('"'+name+'" is a hidden system app with no icon of its own -- it may be something the phone itself depends on. Blocking or hiding it can break a part of the phone (a crash, a broken Settings screen, even an unusable phone). Keep going?'))return;
   if(!confirm('Second check: if this breaks something, undoing it may need "Release device" or a factory reset. Still want to do this?'))return;
   if(!confirm('Final check: block "'+name+'" on '+dv.name+' now?'))return;}
  if(d.mode)dv.config.apps[a.p]={mode:d.mode,label:a.l,schedule:d.schedule||undefined};else delete dv.config.apps[a.p];
  await saveConfigFor(dv,'Saved '+(a.l||a.p)+'. The phone applies it within about 15 seconds.');delete draft[a.p];render()});
 saveBtn.disabled=!dirty();
 const refresh=function(){saveBtn.disabled=!dirty()};
 const seg=h('div',{class:'seg'});
 const options=[['','Default',''],['allow','Allow','']];
 if(dv.config.homeScreen)options.push(['soft','Soft block','']); // only means anything while this phone's Home screen mode is on
 options.push(['block','Block','block']);
 for(const o of options){
  const b=h('button',{class:(d.mode===o[0]?'on ':'')+o[2]},o[1]);
  b.onclick=function(){
   d.mode=o[0];if(d.mode==='block'||d.mode==='soft')d.schedule=null;render2()};seg.append(b)}
 const render2=function(){render()};
 const ctl=h('div',{class:'row',style:'margin-top:8px'},seg);
 const installedHere=dv.packages.some(function(x){return x.p===a.p});
 if(!a.s&&installedHere)ctl.append(btn('Uninstall','danger',async function(){
  if(!confirm('Uninstall '+(a.l||a.p)+' from '+dv.name+'? This removes the app and its data from the phone.'))return;
  await call('POST','/api/devices/'+dv.id+'/command',{type:'uninstall',args:{packageName:a.p}});
  snack('Uninstall queued. The phone does it at its next check-in (within about 15 seconds).');load()}));
 const schedBtn=h('button',{class:'btn tonal'},d.schedule?'Schedule on':'Schedule');
 schedBtn.onclick=function(){open[a.p]=!open[a.p];render()};
 if(d.mode!=='block')ctl.append(schedBtn);
 ctl.append(saveBtn);
 body.append(ctl);

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

/* ---------- sites (browser allowlist) ---------- */
function hostOfUrl(u){try{return new URL(u.includes('://')?u:'https://'+u).hostname.replace(/^www\./,'')}catch(e){return u}}
function renderRequestsCard(m,reqs,dv){
 if(!reqs.length)return;
 const rc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Site requests ('+reqs.length+')'),
  h('div',{class:'mute'},'Pages tried on this device that weren\'t on its allowlist.'));
 for(const r of reqs){
  const dismiss=async function(){await call('DELETE',r.path+'?url='+encodeURIComponent(r.url));load()};
  rc.append(h('div',{class:'app'},h('div',{class:'grow'},h('div',{style:'font-weight:500;word-break:break-all'},r.url),
   h('div',{class:'mute small'},r.deviceName+' · '+ago(r.at)),
   h('div',{class:'row',style:'margin-top:8px'},
    btn('Allow this page only','',async function(){
     await addSite(dv,{type:'exact',url:r.url,label:hostOfUrl(r.url)});await dismiss()}),
    btn('Allow whole site ('+hostOfUrl(r.url)+')','',async function(){
     await addSite(dv,{type:'domain',url:hostOfUrl(r.url),label:hostOfUrl(r.url)});await dismiss()}),
    btn('Dismiss','outline',dismiss)))))}
 m.append(rc)}
function renderSitesEditor(m,dv){
 const top=h('div',{class:'card'},h('h2',null,'Allowed sites'),
  h('div',{class:'mute'},'This device\'s own list — other phones and browsers are unaffected. Anything not here shows a "Request access" prompt instead. A domain covers its subpages and subdomains; an exact page covers only that one link.'));
 const url=h('input',{type:'text',placeholder:'Site or link, e.g. khanacademy.org or https://example.com/page',class:'grow'});
 const type=h('select',null,new Option('Whole site','domain'),new Option('Exact page only','exact'));
 top.append(h('div',{class:'row',style:'margin-top:8px'},url,type,btn('Add','tonal',async function(){
  if(!url.value.trim())return;await addSite(dv,{type:type.value,url:url.value.trim(),label:hostOfUrl(url.value.trim())});url.value=''})));
 m.append(top);

 const list=h('div',{class:'card'});
 const sites=dv.config.sites||{};
 const keys=Object.keys(sites);
 if(!keys.length)list.append(h('div',{class:'mute'},'No sites allowed yet. Add one above, or approve a request.'));
 for(const key of keys){
  const s=sites[key];
  const row=h('div',{class:'app'});
  row.append(h('div',{class:'grow'},h('div',{style:'font-weight:500'},s.label||s.url),
   h('div',{class:'mute small',style:'word-break:break-all'},(s.type==='domain'?'Whole site: ':'Exact page: ')+s.url),
   h('div',null,h('span',{class:'chip'},s.type==='domain'?'subpages + subdomains':'this page only'),
    s.blockImages?h('span',{class:'chip warn'},'images blocked'):null,
    s.installable===false?h('span',{class:'chip'},'no home-screen shortcut'):null)));
  const col=h('div',{style:'display:flex;flex-direction:column;gap:6px;align-items:flex-end'});
  col.append(h('label',{class:'row small'},h('input',{type:'checkbox',checked:s.blockImages?true:false,onchange:async function(e){s.blockImages=e.target.checked;await saveConfigFor(dv,'Saved.')}}),'Block images'));
  col.append(h('label',{class:'row small'},h('input',{type:'checkbox',checked:s.installable===false?false:true,onchange:async function(e){s.installable=e.target.checked;await saveConfigFor(dv,'Saved.')}}),'Allow home-screen shortcut'));
  col.append(btn('Remove','danger',async function(){if(!confirm('Remove '+(s.label||s.url)+' from the allowlist?'))return;delete dv.config.sites[key];await saveConfigFor(dv,'Removed.')}));
  row.append(col);list.append(row)}
 m.append(list);
}
function renderSites(m,dv){
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Make this the only browser'),
  h('div',{class:'mute'},'Replaces Chrome (and any other browser) as the phone\'s handler for links, so every web link opens the agent\'s own browser instead — the one that only opens sites from the list below. You still need to Block Chrome itself on the App rules box so it can\'t be opened directly.')),
  sw(dv.config.restrictBrowsing,async function(on){dv.config.restrictBrowsing=on;try{await saveConfigFor(dv,on?'This is now the only browser. Phones apply it within about 15 seconds.':'Chrome and other browsers can be used again.')}catch(e){snack(e.message,1)}}))));
 renderRequestsCard(m,(dv.siteRequests||[]).map(function(r){return{path:'/api/devices/'+dv.id+'/site-requests',deviceName:dv.name,url:r.url,at:r.at}}),dv);
 renderCloneCard(m,dv);
 renderSitesEditor(m,dv);
}
async function addSite(dv,entry){
 const key=(entry.type)+':'+hostOfUrl(entry.url)+(entry.type==='exact'?':'+Date.now():'');
 dv.config.sites=dv.config.sites||{};
 dv.config.sites[key]={type:entry.type,url:entry.url,label:entry.label,blockImages:false,installable:true};
 await saveConfigFor(dv,'Added '+(entry.label||entry.url)+'. Phones pick it up within about 15 seconds.');
}
/** Copies another device's (or browser's) whole config -- or just its sites, into a browser -- over this one's. */
function renderCloneCard(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'Clone settings'),
  h('div',{class:'mute'},dv.isBrowser?'Copy another phone\'s or browser\'s allowed sites into this browser, replacing its own.':'Copy another phone\'s or browser\'s settings into this one, replacing everything here: apps, sites, and every Settings toggle below (a browser only has sites to give).'));
 const options=[...devices.filter(function(x){return x.id!==dv.id}).map(function(x){return [x.id,x.name]}),
  ...browsers.filter(function(x){return x.id!==dv.id}).map(function(x){return [x.id+'|browser',x.name+' (Browser)']})];
 if(!options.length){card.append(h('div',{class:'mute small',style:'margin-top:8px'},'No other device or browser to clone from yet.'));m.append(card);return}
 const sel=h('select',{style:'width:100%;margin-top:8px'},...options.map(function(o){return new Option(o[1],o[0])}));
 card.append(sel,h('div',{style:'margin-top:8px'},btn('Clone from this','outline',async function(){
  if(!confirm('Replace this '+(dv.isBrowser?'browser\'s sites':'device\'s settings')+' with a copy of the selected one\'s? This cannot be undone.'))return;
  const [rawId,kind]=sel.value.split('|');
  await call('POST',(dv.isBrowser?'/api/browsers/':'/api/devices/')+dv.id+'/clone-from',{sourceId:rawId,browser:kind==='browser'});
  snack('Cloned. Phones pick up the change within about 15 seconds.');load()})));
 m.append(card);
}
function renderBrowserDetail(m,b){
 const back=h('button',{class:'btn outline'},'‹ All phones');back.onclick=function(){location.hash='devices'};
 m.append(h('div',{class:'row'},back,h('div',{class:'grow'}),btn('Refresh','tonal',load)));
 m.append(h('div',{class:'row',style:'margin-top:12px'},h('div',{class:'ico',style:'width:44px;height:44px;border-radius:12px;background:var(--primary-container);display:flex;align-items:center;justify-content:center;font-size:22px;flex:none'},'🌐'),
  h('div',{class:'grow'},h('h2',{style:'font-size:20px'},b.name),h('div',{class:'mute small'},'Connected directly — no MDM on this phone · last check-in '+ago(b.lastSeen)))));

 const controls=h('div',{class:'card'},h('h2',null,'This browser'));
 controls.append(h('div',{class:'row',style:'margin-top:8px'},
  btn('Rename','outline',async function(){const name=prompt('Name for this browser',b.name);if(!name)return;await call('PUT','/api/browsers/'+b.id,{name:name});load()}),
  btn('Disconnect','danger',async function(){if(!confirm('Disconnect '+b.name+'? It goes back to needing to connect again, and allows nothing until then.'))return;await call('DELETE','/api/browsers/'+b.id);location.hash='devices'})));
 m.append(controls);

 renderRequestsCard(m,(b.siteRequests||[]).map(function(r){return{path:'/api/browsers/'+b.id+'/site-requests',deviceName:b.name,url:r.url,at:r.at}}),b);
 renderCloneCard(m,b);
 renderSitesEditor(m,b);
}

function renderWinDetail(m,w){
 const back=h('button',{class:'btn outline'},'‹ All phones');back.onclick=function(){location.hash='devices'};
 m.append(h('div',{class:'row'},back,h('div',{class:'grow'}),btn('Refresh','tonal',load)));
 m.append(h('div',{class:'row',style:'margin-top:12px'},h('div',{class:'ico',style:'width:44px;height:44px;border-radius:12px;background:var(--primary-container);display:flex;align-items:center;justify-content:center;font-size:22px;flex:none'},'💻'),
  h('div',{class:'grow'},h('h2',{style:'font-size:20px'},w.name),h('div',{class:'mute small'},(w.info.hostname||'Windows computer')+' · last check-in '+ago(w.lastSeen)))));

 const controls=h('div',{class:'card'},h('h2',null,'This computer'));
 controls.append(h('div',{style:'margin-top:4px'},sw(w.enabled,async function(on){
  await call('PUT','/api/windevices/'+w.id+'/config',{enabled:on,allowedPrograms:w.allowedPrograms});
  snack(on?'Locking down. The computer applies this within about 15 seconds.':'Lockdown turned off.');load()}),
  ' Lock down internet access (everything except the allowed programs below)'));
 if(w.enabled!==w.reportedEnabled)controls.append(h('div',{class:'mute small',style:'margin-top:4px'},'⏳ Waiting for the computer to apply this.'));
 controls.append(h('div',{class:'row',style:'margin-top:12px'},
  btn('Rename','outline',async function(){const name=prompt('Name for this computer',w.name);if(!name)return;await call('PUT','/api/windevices/'+w.id,{name:name});load()}),
  btn('Uninstall from this computer','danger',async function(){if(!confirm('Remove LockGuard from '+w.name+' entirely? It will take internet access and the firewall rules off automatically, next time it checks in.'))return;await call('POST','/api/windevices/'+w.id+'/command',{type:'uninstall'});snack('Queued. It\'ll remove itself within about 15 seconds.')}),
  btn('Remove from this list','outline',async function(){if(!confirm('Remove '+w.name+' from the dashboard? Only do this if it\'s already gone — otherwise use "Uninstall from this computer" instead, or it stays locked down with no way to control it from here.'))return;await call('DELETE','/api/windevices/'+w.id);location.hash='devices'})));
 m.append(controls);

 const progCard=h('div',{class:'card'},h('h2',null,'Allowed programs'),
  h('div',{class:'mute'},'Everything else on this computer stays installed and opens normally — it just can\'t reach the internet. Full path to the .exe, e.g. C:\\Program Files\\Outlook\\outlook.exe'));
 const list=h('div',{style:'margin-top:8px'});
 (w.allowedPrograms||[]).forEach(function(p,i){
  list.append(h('div',{class:'row',style:'align-items:center'},h('div',{class:'grow mono small'},p),
   btn('Remove','outline',async function(){const next=w.allowedPrograms.filter(function(_,j){return j!==i});await call('PUT','/api/windevices/'+w.id+'/config',{enabled:w.enabled,allowedPrograms:next});load()})))});
 if(!(w.allowedPrograms||[]).length)list.append(h('div',{class:'mute small'},'Nothing allowed yet.'));
 progCard.append(list,h('div',{style:'margin-top:10px'},btn('Add a program','',async function(){
  const path=prompt('Full path to the program\'s .exe on that computer');if(!path)return;
  const next=[...(w.allowedPrograms||[]),path];await call('PUT','/api/windevices/'+w.id+'/config',{enabled:w.enabled,allowedPrograms:next});load()})));
 m.append(progCard);
}

/* ---------- codes ---------- */
const CODE_INFO={
 enroll:['Enrollment code','Connects a new phone to this dashboard. Used in the adb command or typed into the agent app. Valid 1 hour, one use.'],
 browser:['Browser connect code','Connects a standalone Browser app directly to this dashboard — no agent, no device owner, nothing else installed. Until it\'s connected (or set up on its own with a master code), that Browser allows nothing at all. Valid 1 hour, one use.']};
function renderCodes(m){
 m.append(h('div',{class:'card'},h('div',{class:'mute'},'Install, removal, and "browse freely" codes are generated from inside each phone\'s own page now, so they only ever work on that one phone. These two can\'t be tied to a phone — an enrollment code is how a phone gets an identity in the first place, and a Browser connect code is for a device with no agent at all.')));
 for(const type of ['enroll','browser']){
  const card=h('div',{class:'card'},h('h2',null,CODE_INFO[type][0]),h('div',{class:'mute'},CODE_INFO[type][1]));
  const out=h('div');
  card.append(h('div',{style:'margin-top:10px'},btn('Generate new code','',async function(){
   const r=await call('POST','/api/codes',{type:type});out.textContent='';
   out.append(h('div',{class:'code'},r.code),h('div',{class:'mute small'},'Valid for 1 hour. Shown once; generate another any time.'));
   if(type==='enroll'){
    out.append(h('div',{class:'mute',style:'margin-top:8px'},'On your computer, with the phone connected and USB debugging on:'),
     h('pre',{class:'cmd'},'adb install mdm-agent-enroll.apk\nadb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver\nadb shell am start -n com.familymdm.agent/.MainActivity --es code '+r.code),
     h('div',{class:'mute small'},'Get mdm-agent-enroll.apk from GitHub → Releases → "Latest agent build" (it\'s the same app with a couple of permissions it doesn\'t need yet left out, so it looks less suspicious to Play Protect on a first install — it switches itself to the full build right after enrolling). If this build has the dashboard address baked in, that\'s all you need to type — otherwise tap "Use a different dashboard" on the phone once and enter '+r.server+'. No Google account needed for either command.'),
     h('div',{class:'mute small',style:'margin-top:8px'},'Prefer no computer at all? Use the QR code below instead — scan it on a brand-new or freshly reset phone, nothing to type.'))}
   if(type==='browser'){
    out.append(h('div',{class:'mute',style:'margin-top:8px'},'In the Browser app (no agent needed), tap "Connect to a dashboard" and enter:'),
     h('pre',{class:'cmd'},'Dashboard address: '+r.server+'\nCode: '+r.code),
     h('div',{class:'mute small'},'Get mdm-browser.apk from GitHub → Releases → "Latest agent build", same place as the agent. It shows up on the Devices list once connected.'))}
  })),out);m.append(card);
  if(type==='enroll'){
   const qrCard=h('div',{class:'card'},h('h2',null,'Set up with a QR code (no computer)'),
    h('div',{class:'mute'},'Works on a brand-new or freshly factory-reset phone, before it finishes its own setup wizard. Tap the Welcome screen 6 times, scan this, and the phone downloads the agent, becomes managed, and enrolls itself — nothing to type.'));
   const ssid=h('input',{type:'text',placeholder:'Wi-Fi network name (optional, so the phone can get online to download the app)',style:'width:100%;margin-top:10px'});
   const pass=h('input',{type:'text',placeholder:'Wi-Fi password (leave empty for an open network)',style:'width:100%;margin-top:8px'});
   const qrOut=h('div',{style:'margin-top:10px'});
   qrCard.append(ssid,pass,qrOut,h('div',{style:'margin-top:10px'},btn('Generate enrollment QR','',async function(){
    qrOut.textContent='';
    const r=await call('POST','/api/codes',{type:'enroll'});
    if(!r.apkUrl||!r.sha256){qrOut.append(h('div',{class:'mute',style:'color:var(--error)'},'The latest agent build has not finished publishing its checksum yet. Wait a minute for the build to finish, or use the adb command above instead.'));return}
    const payload={
     'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME':'com.familymdm.agent/.AdminReceiver',
     'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION':r.apkUrl,
     'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM':r.sha256,
     'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED':true,
     'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE':{server:r.server,code:r.code}};
    if(ssid.value.trim()){
     payload['android.app.extra.PROVISIONING_WIFI_SSID']=ssid.value.trim();
     if(pass.value){payload['android.app.extra.PROVISIONING_WIFI_PASSWORD']=pass.value;payload['android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE']='WPA'}}
    const qr=qrcode(0,'M');qr.addData(JSON.stringify(payload));qr.make();
    qrOut.append(h('img',{src:qr.createDataURL(6,8),alt:'Enrollment QR code',style:'background:#fff;padding:4px;border-radius:12px;display:block'}),
     h('div',{class:'mute small',style:'margin-top:8px'},'Valid for 1 hour, one use. On the new phone: tap the Welcome screen 6 times, then scan.'))})));
   m.append(qrCard)}}
}

/* ---------- settings ---------- */
/** The only things left that aren't a specific phone's own: branding shown on every phone. */
function renderSettings(m){
 const logoCard=h('div',{class:'card'},h('h2',null,'Logo on the phones'),
  h('div',{class:'mute'},'Shown on the timed-lock screen and at the top of the agent app, on every phone. A square or wide PNG/JPG works; it is shrunk automatically.'));
 const prev=h('img',{src:'/api/logo?v='+(iconVer.__logo||0),alt:'',style:'max-height:80px;max-width:100%;margin-top:10px;border-radius:8px;display:block'});prev.onerror=function(){prev.replaceWith(h('div',{class:'mute small',style:'margin-top:10px'},'No logo set.'))};
 logoCard.append(prev,h('div',{class:'row',style:'margin-top:10px'},
  btn('Upload logo…','',async function(){const blob=await pickImage(256,false);if(!blob)return;await putImage('/api/logo',blob);iconVer.__logo=Date.now();snack('Logo saved. Phones pick it up within about 15 seconds.');render()}),
  btn('Remove logo','outline',async function(){await call('DELETE','/api/logo');iconVer.__logo=Date.now();snack('Logo removed.');render()})));
 m.append(logoCard);
 m.append(h('div',{class:'card'},h('h2',null,'Everything else moved'),
  h('div',{class:'mute'},'New-apps approval, Factory Reset Protection, Home screen mode, auto-update, Wi-Fi reporting, the master code, and every restriction switch are now each phone\'s own — open a phone and look for its Settings box.')));
}
/** Everything that used to be shared by every phone, now that specific phone's own. */
function renderDeviceSettings(m,dv){
 const appr=h('div',{class:'card'},h('h2',null,'New apps'));
 appr.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Hold newly installed apps until I approve them'),
  h('div',{class:'mute'},'Lets the person keep the Play Store: anything they install afterwards stays hidden (it cannot be opened) until you approve it on the App rules box above. Apps already on the phone when you switch this on are treated as approved.')),
  sw(dv.config.approveNew,async function(on){dv.config.approveNew=on;try{await saveConfigFor(dv,on?'New apps will wait for your approval.':'New apps are no longer held.')}catch(e){snack(e.message,1)}})));
 m.append(appr);
 const frp=h('div',{class:'card'},h('h2',null,'Factory Reset Protection'),
  h('div',{class:'mute'},'If someone resets this phone from recovery mode, setup will demand the Google account(s) you enter here, so the phone is useless to them. No account has to be signed in on the phone. Needs Android 11 or newer. For this to hold, the phone\'s bootloader must be locked (the Overview box shows a Reset protection check).'));
 const ids=h('input',{type:'text',placeholder:'Google account ID (about 21 digits)',value:(dv.config.frpAccounts||[]).join(', '),style:'width:100%;margin-top:8px'});
 frp.append(ids,h('div',{class:'row',style:'margin-top:8px'},
  btn('Save','',async function(){
   const list=ids.value.split(/[ ,\n]+/).filter(Boolean);
   if(list.some(function(x){return !/^(people\/)?[0-9]{15,25}$/.test(x)})){snack('That is not a Google account ID. It is a number of about 21 digits (see the steps below), not an email address.',1);return}
   dv.config.frpAccounts=list;await saveConfigFor(dv,'Saved. The phone applies it within about 15 seconds.');await load()}),
  btn('Turn off','outline',async function(){dv.config.frpAccounts=[];await saveConfigFor(dv,'Reset protection removed.');await load()})));
 frp.append(h('div',{class:'mute small',style:'margin-top:10px'},'How to get your Google account ID:'),
  h('ol',{class:'mute small',style:'margin:4px 0 0 18px;padding:0'},
   h('li',null,'Open ',h('a',{href:'https://developers.google.com/people/api/rest/v1/people/get',target:'_blank',rel:'noopener'},'the Google People API page'),' and click "Try it".'),
   h('li',null,'Set resourceName to people/me and personFields to metadata, then click Execute and sign in with the Google account you control.'),
   h('li',null,'In the result, copy the long number (about 21 digits) next to "id" or after "people/". Paste it above.')),
  h('div',{class:'mute small',style:'margin-top:8px'},'Keep that Google account safe: whoever can sign in to it can set the phone up again after a reset.'));
 m.append(frp);
 const hsCard=h('div',{class:'card'},h('h2',null,'Home screen mode'));
 if(dv.config.homeScreen&&dv.info.kioskPaused)hsCard.append(h('div',{class:'row',style:'margin-bottom:8px'},h('span',{class:'chip warn'},'Currently paused on the phone itself')));
 hsCard.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Only allowed apps can be opened'),
  h('div',{class:'mute'},'The agent becomes this phone\'s home screen and shows only the apps you set to Allow, with your logo and your custom icons. An app set to Block is fully switched off, same as always; one left at Default or set to Soft block just has no icon here and stays installed and running in the background. Calls and texts still work. Settings is not available unless you Allow it, so add Wi-Fi from the dashboard. The master code on the phone (Administrator) can pause this mode without changing this switch — that\'s what the "paused" note above means, when it is showing — and turning this switch off gives the phone back its normal home screen for good.')),
  sw(dv.config.homeScreen,async function(on){
   if(on&&!confirm('Turn on Home screen mode on this phone? First make sure the apps the person needs (phone, messages, maps…) are set to Allow on the App rules box above, because only those will appear.')){render();return}
   dv.config.homeScreen=on;
   // Soft block only means anything while this switch is on; off a leftover "soft" app would be
   // stuck invisible in App rules (no button shows it as selected) instead of back at Default.
   if(!on)for(const pkg in dv.config.apps)if(dv.config.apps[pkg].mode==='soft')delete dv.config.apps[pkg];
   try{await saveConfigFor(dv,on?'Home screen mode on. The phone switches within about 15 seconds.':'Home screen mode off.')}catch(e){snack(e.message,1)}render()})));
 m.append(hsCard);
 m.append(h('div',{class:'card'},h('h2',null,'Agent updates'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Update the agent automatically'),
  h('div',{class:'mute'},'The phone checks GitHub for a newer build every 6 hours and installs it itself. You can always update it yourself from the Controls box, or from the phone\'s admin panel.')),
  sw(dv.config.autoUpdate,async function(on){dv.config.autoUpdate=on;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}}))));
 m.append(h('div',{class:'card'},h('h2',null,'System updates'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Freeze Android system updates'),
  h('div',{class:'mute'},'Android will not let any app block OTA updates completely -- a freeze can only last up to 90 days at a time, with a mandatory 60-day gap before the next one. This sets the most freeze Android allows, back to back, which covers most of the year but leaves about two months of it open to an update landing. There is no setting that closes that gap; this is a limit of Android itself, not this app.')),
  sw(dv.config.freezeUpdates,async function(on){dv.config.freezeUpdates=on;try{await saveConfigFor(dv,on?'Freeze scheduled. The phone applies it within about 15 seconds.':'Freeze removed; the phone can update normally again.')}catch(e){snack(e.message,1)}}))));
 m.append(h('div',{class:'card'},h('h2',null,'Accessibility'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block accessibility services'),
  h('div',{class:'mute'},'A sideloaded app can ask for an accessibility service and use it to read the screen and tap things on the person\'s behalf -- a known way around app controls. Turning this on switches off every accessibility service on the phone, including ones used for real accessibility needs, so leave it off if anyone here relies on one.')),
  sw(dv.config.blockAccessibility,async function(on){
   if(on&&!confirm('Turn off every accessibility service on this phone? Do this only if nobody here needs one for real accessibility use.'))return;
   dv.config.blockAccessibility=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 15 seconds.')}catch(e){snack(e.message,1)}render()}))));
 m.append(h('div',{class:'card'},h('h2',null,'Phone info'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Show which Wi-Fi the phone is on'),
  h('div',{class:'mute'},'Android only reveals the network name when its Location setting is on, so this switch turns that on for the phone. The dashboard shows the network name and signal, never where the phone is. Battery level is always shown.')),
  sw(dv.config.reportWifi,async function(on){dv.config.reportWifi=on;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}}))));
 m.append(h('div',{class:'card'},h('h2',null,'Only control from the dashboard'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Turn off the phone\'s own Administrator panel'),
  h('div',{class:'mute'},'While this is on, entering the master code on the phone itself does nothing -- every change has to come from here instead. Turning it back off also has to happen from here, so only use this if you expect the phone to stay able to reach this dashboard.')),
  sw(dv.config.phoneAdminLocked,async function(on){
   if(on&&!confirm('Turn off this phone\'s own Administrator panel? You\'ll only be able to turn it back on from this dashboard -- make sure the phone can still reach it.'))return;
   dv.config.phoneAdminLocked=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 15 seconds.')}catch(e){snack(e.message,1)}render()}))));
 m.append(h('div',{class:'card'},h('h2',null,'App icon'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Hide the app icon'),
  h('div',{class:'mute'},'Removes the agent\'s icon from the launcher and app drawer. Nothing else changes -- it keeps running and enforcing everything exactly the same. Two ways back: turn this off here again, or dial *#*#636#*#* right on the phone (works even offline; a few phone brands\' own dialer apps don\'t support this standard Android feature).')),
  sw(dv.config.hideAppIcon,async function(on){
   if(on&&!confirm('Hide the app icon on this phone? Turn it back on here, or dial *#*#636#*#* on the phone itself.'))return;
   dv.config.hideAppIcon=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 15 seconds.')}catch(e){snack(e.message,1)}render()}))));
 const mc=h('div',{class:'card'},h('h2',null,'Master code'),
  h('div',{class:'mute'},'Works on this phone with no internet (Agent → Administrator): lock, set PIN, install APKs, show/hide apps, release, erase. The phone only stores a scrambled version. Status: '+(dv.masterSet?'set':'not set')+'.'));
 const code=h('input',{type:'password',placeholder:'New master code (6+ characters, letters/numbers/symbols)',style:'width:100%;margin-top:8px'});
 mc.append(code,h('div',{class:'row',style:'margin-top:8px'},
  btn(dv.masterSet?'Change master code':'Set master code','',async function(){
   const v=code.value;if(!/^[\x20-\x7e]{6,64}$/.test(v)){snack('Use 6 to 64 normal keyboard characters.',1);return}
   const salt=crypto.getRandomValues(new Uint8Array(16));
   const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(v),'PBKDF2',false,['deriveBits']);
   const bits=new Uint8Array(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:salt,iterations:state.masterIterations},key,256));
   const hex=function(a){return [...a].map(function(b){return b.toString(16).padStart(2,'0')}).join('')};
   await call('PUT','/api/devices/'+dv.id+'/master',{salt:hex(salt),hash:hex(bits)});code.value='';dv.masterSet=true;snack('Master code saved. The phone receives it at its next check-in.');render()}),
  dv.masterSet?btn('Remove','outline',async function(){if(!confirm('Remove this phone\'s master code? Its on-phone admin panel stops working.'))return;await call('DELETE','/api/devices/'+dv.id+'/master');dv.masterSet=false;snack('Master code removed.');render()}):null));
 m.append(mc);
 m.append(h('div',{class:'card'},h('h2',null,'Recovery code'),
  h('div',{class:'mute'},'This phone\'s own code, generated by the phone itself the first time it ran. Always works here, with no internet, even with no master code set — different from every other phone\'s.'),
  dv.fallbackCode?h('div',{class:'code',style:'margin-top:8px;font-size:22px;letter-spacing:2px'},dv.fallbackCode):h('div',{class:'mute small',style:'margin-top:8px'},'Not seen yet — shows up after this phone\'s first check-in.')));
 const card=h('div',{class:'card'},h('h2',null,'Restrictions'),h('div',{class:'mute'},'Each switch saves immediately. The phone picks changes up within about 15 seconds.'));
 for(const k in state.restrictions){
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},state.restrictions[k].label),
   sw(dv.config.restrictions[k],async function(on){dv.config.restrictions[k]=on;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}})))}
 m.append(card,h('div',{class:'card'},h('h2',null,'Good to know'),
  h('div',{class:'mute'},'Blocking Developer options also turns off USB debugging, so adb stops working. If you need to get back in, send "Release device" from the Controls box (or use recovery mode).')));
}

load().catch(function(e){snack(e.message,1);document.getElementById('main').textContent='Could not load: '+e.message});
// Every 5 minutes, not 60 seconds -- each of these three calls reads every device from Workers KV
// (one get() per device), and a dashboard tab left open all day was the main thing actually burning
// through the free tier's ~100,000 reads/day cap. Any action you take still reloads immediately on
// its own, so this interval is only about catching changes from elsewhere (another tab, a phone).
setInterval(function(){if(document.hidden)return;
 call('GET','/api/devices').then(function(d){devices=d;if(tab==='devices')render()}).catch(function(){});
 call('GET','/api/browsers').then(function(b){for(const x of b)x.isBrowser=true;browsers=b;if(tab==='devices')render()}).catch(function(){});
 call('GET','/api/windevices').then(function(w){windevices=w;if(tab==='devices')render()}).catch(function(){});
},300000);
</script></body></html>`;
