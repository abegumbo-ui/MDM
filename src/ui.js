// Dashboard pages. Material 3 styling with plain CSS and vanilla JS (no build step, no libraries).

const STYLE = String.raw`
/* Same dark red theme as the phone app itself (see android/app/src/main/res/values/colors.xml) --
   one fixed theme, not light/dark depending on the system, same as the app only ever has the one. */
:root{
  --primary:#C62828;--on-primary:#fff;--primary-container:#3A0A0A;--on-primary-container:#FFCDD2;
  --secondary-container:#262626;--on-secondary-container:#fff;
  --surface:#0D0D0D;--surface-1:#171717;--surface-2:#1c1c1c;--surface-3:#222222;
  --on-surface:#fff;--on-surface-variant:#B0B0B0;--outline:#3A3A3A;--outline-variant:#2A2A2A;
  --error:#FF5252;--error-container:#4a1515;--ok:#7fd99a;--ok-container:#0f3d1c;--warn:#ffb95c;--warn-container:#4a2f00;
}
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
.chip svg{width:13px;height:13px;flex:none}
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
.kv{display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-top:1px solid var(--outline-variant)}
.kv:first-child{border-top:0}.kv b{font-weight:500;text-align:right;word-break:break-word}
.login{max-width:360px;margin:16vh auto 0;padding:0 16px}
/* Big 2-column home-screen tile grid -- same shape, same 2 columns, same solid red squares with a
   white icon and a label underneath, as the phone app's own Administrator home grid (Ui.tile /
   Ui.tileGrid, 96dp squares, 20dp corners, icon at 42% of the square). */
.tilegrid-lg{display:grid;grid-template-columns:repeat(2,1fr);gap:22px 8px;margin:16px 0}
.tilegrid-lg .tile{justify-self:center;display:flex;flex-direction:column;align-items:center;gap:8px;cursor:pointer;background:none;border:0;color:var(--on-surface);font:500 15px Roboto,system-ui,sans-serif;text-align:center;padding:0;width:100%}
.tilegrid-lg .tile .ico{width:96px;height:96px;max-width:100%;border-radius:20px;background:var(--primary);display:flex;align-items:center;justify-content:center}
.tilegrid-lg .tile:hover .ico{filter:brightness(1.1)}
.tilegrid-lg .tile svg{width:42%;height:42%}
/* Small icon-plus-label row -- same shape as the phone app's own Ui.rowTile, used for the
   second-level pickers inside Apps and Settings (Regular Apps / Home Screen Mode / ... and
   Settings Menu / Permissions / ...). */
.hubrow{display:flex;align-items:center;gap:14px;padding:12px;border-radius:14px;background:var(--surface-1);border:1px solid var(--outline-variant);cursor:pointer;margin-bottom:8px}
.hubrow:hover{background:var(--surface-2)}
.hubrow .ico{width:44px;height:44px;border-radius:12px;background:var(--primary);display:flex;align-items:center;justify-content:center;flex:none}
.hubrow .ico svg{width:22px;height:22px}
.hubrow .label{font-size:15px;font-weight:500}
.search{position:relative;margin:12px 0}
.search input{width:100%;padding-left:36px}
.search .ico{position:absolute;left:10px;top:50%;transform:translateY(-50%);pointer-events:none;color:var(--on-surface-variant)}
.search-results{background:var(--surface-1);border:1px solid var(--outline-variant);border-radius:12px;margin-top:4px;overflow:hidden}
.search-results div{padding:10px 14px;cursor:pointer;border-top:1px solid var(--outline-variant)}
.search-results div:first-child{border-top:0}
.search-results div:hover{background:var(--surface-2)}
`;

export const loginPage = (error = "") => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0D0D0D"><title>MDM Login</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500&display=swap">
<style>${STYLE}</style></head>
<body><div class="login"><div class="card"><h2 style="font-size:22px;margin-bottom:12px">MDM Dashboard</h2>
<form method="post" action="/login">
<input type="text" name="loginName" placeholder="Client login name (leave empty if you're the administrator)" style="width:100%;margin-bottom:8px" autocapitalize="off" autocorrect="off">
<div class="row"><input class="grow" type="password" name="password" placeholder="Password" autofocus required>
<button class="btn">Sign in</button></div>${error ? `<p class="mute" style="color:var(--error)">${error}</p>` : ""}</form></div></div></body></html>`;

export const dashboardPage = () => String.raw`<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0D0D0D"><title>MDM Dashboard</title>
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
let deviceSection=sessionStorage.getItem('deviceSection')||null,lastOpenId=sessionStorage.getItem('lastOpenId')||null;
function setDeviceSection(v){deviceSection=v;try{v?sessionStorage.setItem('deviceSection',v):sessionStorage.removeItem('deviceSection')}catch(e){}}
let deviceSearch='';
// Second-level picker inside the Apps and Settings tabs, same hub-in-front-of-a-sub-section idiom
// the phone app itself uses (AdminActivity's buildAppsHub/buildSettingsHub) -- null means "show the
// picker", a key means "show that one section, with a Back button".
let appsSub=sessionStorage.getItem('appsSub')||null,settingsSub=sessionStorage.getItem('settingsSub')||null,lockSub=sessionStorage.getItem('lockSub')||null;
function setAppsSub(v){appsSub=v;try{v?sessionStorage.setItem('appsSub',v):sessionStorage.removeItem('appsSub')}catch(e){}}
function setSettingsSub(v){settingsSub=v;try{v?sessionStorage.setItem('settingsSub',v):sessionStorage.removeItem('settingsSub')}catch(e){}}
function setLockSub(v){lockSub=v;try{v?sessionStorage.setItem('lockSub',v):sessionStorage.removeItem('lockSub')}catch(e){}}
let state=null,devices=[],browsers=[],windevices=[],clients=[],latest=null,tab='devices',openId=null,openBrowserId=null,openWinId=null,search='',sysAppSearch='';
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
// Same white-on-red icon tiles as the phone app itself: these are the exact vector paths from
// android/app/src/main/res/drawable/ic_*_tile.xml (and ic_shield.xml), not lookalikes, so the
// dashboard's icons are pixel-for-pixel the same glyphs the app uses for the same sections. "doc"
// is the one icon the phone has no equivalent for (the dashboard's own Log page).
const SVGNS='http://www.w3.org/2000/svg';
const ICONS={
 lock:'M18,8h-1L17,6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2L6,8c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L20,10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8L8.9,8L8.9,6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z',
 apps:'M4,8h4L8,4L4,4v4zM10,20h4v-4h-4v4zM4,20h4v-4L4,16v4zM4,14h4v-4L4,10v4zM10,14h4v-4h-4v4zM16,4v4h4L20,4h-4zM10,8h4L14,4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z',
 globe:'M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56C16.57,5.14 18.03,6.35 18.92,8zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82C10.52,6.57 11.17,5.24 12,4.04zM4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38C7.56,10.66 7.5,11.33 7.5,12s0.06,1.34 0.14,2L4.26,14zM5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56C7.43,18.86 5.97,17.65 5.08,16zM8.03,8L5.08,8c0.89,-1.65 2.35,-2.86 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82C13.48,17.43 12.83,18.76 12,19.96zM14.34,14L9.66,14C9.57,13.34 9.5,12.68 9.5,12s0.07,-1.34 0.16,-2h4.68c0.09,0.66 0.16,1.32 0.16,2S14.43,13.34 14.34,14zM14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95C18.03,17.65 16.57,18.86 14.59,19.56zM16.36,14c0.08,-0.66 0.14,-1.33 0.14,-2s-0.06,-1.34 -0.14,-2h3.38C19.9,10.64 20,11.31 20,12s-0.1,1.36 -0.26,2L16.36,14z',
 key:'M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4v-4h2v-4H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2C9,13.1 8.1,14 7,14z',
 message:'M20,2L4,2c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM6,9h12v2L6,11L6,9zM14,14L6,14v-2h8v2zM18,8L6,8L6,6h12v2z',
 device:'M17,19L7,19L7,5h10v14zM17,1L7,1c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2L19,3c0,-1.1 -0.9,-2 -2,-2z',
 home:'M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z',
 shield:'M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z',
 wifi:'M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3C13.35,15.34 10.66,15.34 9,17zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z',
 update:'M19,13c0,3.87 -3.13,7 -7,7 -3.87,0 -7,-3.13 -7,-7 0,-3.83 3.08,-6.94 6.9,-7l0,2.02c-2.7,0.07 -4.9,2.3 -4.9,4.98 0,2.76 2.24,5 5,5 2.76,0 5,-2.24 5,-5 0,-1.27 -0.49,-2.42 -1.27,-3.3l-1.48,1.48 0,-5.5 5.5,0 -1.69,1.69c1.17,1.28 1.94,2.96 1.94,4.63z',
 doc:'M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2L20,8l-6,-6zM13,9L13,3.5L18.5,9L13,9z',
 battery:'M15.67,4H14V2h-4v2H8.33C7.6,4,7,4.6,7,5.33v15.33C7,21.4,7.6,22,8.33,22h7.33c0.74,0,1.34,-0.6,1.34,-1.33V5.33C17,4.6,16.4,4,15.67,4z'};
function svgIcon(name,fill){const s=document.createElementNS(SVGNS,'svg');s.setAttribute('viewBox','0 0 24 24');s.setAttribute('fill',fill||'#fff');
 const p=document.createElementNS(SVGNS,'path');p.setAttribute('d',ICONS[name]||'');s.append(p);return s}
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
 if(state.session.role==='admin')clients=await call('GET','/api/clients');
 render();
 call('GET','/api/latest-agent').then(function(r){if(JSON.stringify(r.latest)!==JSON.stringify(latest)){latest=r.latest;render()}}).catch(function(){})}
async function saveConfigFor(d,msg){await call('PUT',(d.isBrowser?'/api/browsers/':'/api/devices/')+d.id+'/config',d.config);snack(msg||'Saved. Phones update within about 5 minutes.')}

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
 if(tab==='codes'||tab==='settings'||tab==='clients'){
  const back=h('button',{class:'btn outline'},'‹ Devices');back.onclick=function(){location.hash='devices'};
  m.append(h('div',{class:'row'},back));
  if(tab==='codes')renderCodes(m);
  else if(tab==='clients')renderClients(m);
  else renderSettings(m);
  return}
 renderDevices(m)}
window.addEventListener('hashchange',function(){route();window.scrollTo(0,0);render()});

/* ---------- devices ---------- */
const NAMES={lock:'Lock screen',reboot:'Reboot',wipe:'Wipe',release:'Release device',install:'Install APK',uninstall:'Uninstall app',sync:'Sync','install-result':'Install result','code:install':'Install code used','code:uninstall':'Removal code used',setPin:'Set screen PIN',clearPin:'Remove screen lock',unlock:'Unlock',addWifi:'Add Wi-Fi',resetAppCode:'Reset app code',updateAgent:'Update agent','uninstall-result':'Uninstall result',listSystemApps:'Scan for hidden system apps',locate:'Find location',checkUpdates:'Check for app updates',updateApp:'Update app'};
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
 if(bat)c.append(h('span',{class:'chip '+(bat.pct<=15&&!bat.charging?'bad':'')},svgIcon('battery','currentColor'),bat.pct+'%'+(bat.charging?' ⚡':'')));
 const wf=d.info.wifi;
 if(wf)c.append(h('span',{class:'chip'},svgIcon(wf.transport==='wifi'?'wifi':'device','currentColor'),
  wf.transport==='wifi'?(wf.ssid||'Wi-Fi'):wf.transport==='mobile'?('SIM card'+(wf.carrier?' — '+wf.carrier:'')):'No connection'));
 if(d.inflight.some(function(x){return x.type==='lock'})||d.pending)c.append(h('span',{class:'chip warn'},'⏳ Command on its way'));
 if(lockedNow(d))c.append(h('span',{class:'chip warn'},'🔒 Locked until '+new Date(d.info.lock.until).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
 if(browsingFreely(d))c.append(h('span',{class:'chip warn'},'🌐 Free browsing until '+new Date(d.info.browseUntil).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
 if(d.info.kiosk)c.append(h('span',{class:'chip ok'},'🏠 Home screen mode'));
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
   const nameCol=h('div',{class:'grow'},h('div',{class:'name'},d.name));
   if(state.session.role==='admin'&&d.ownerId)nameCol.append(h('div',{class:'mute small'},d.ownerId));
   card.append(h('div',{class:'ico'},'📱'),nameCol,h('div',{class:'mute',style:'font-size:22px'},'›'));
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
 row.append(codes);
 // The logo is the only thing left here, and it's admin-only (see renderSettings) -- a client
 // would find nothing behind this button, so it doesn't show one at all.
 if(state.session.role==='admin'){
  const settings=h('button',{class:'btn outline'},'Settings');settings.onclick=function(){location.hash='settings'};
  const clients=h('button',{class:'btn outline'},'Clients');clients.onclick=function(){location.hash='clients'};
  row.append(settings,clients);
 }
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

/* ---------- hub picker: same idiom as the phone app's own Apps/Settings hubs ---------- */
function hubPicker(m,options){
 // Same shape as the phone app's own Ui.rowTile: a small red icon square on the left, the label
 // next to it, stacked full-width rows -- not a grid of big tiles (that's reserved for the
 // Administrator home screen itself, see renderDeviceDetail's section picker below).
 for(const [key,label,setSub,icon] of options){
  const row=h('div',{class:'hubrow'},h('div',{class:'ico'},svgIcon(icon)),h('div',{class:'label'},label));
  row.onclick=function(){setSub(key);render()};
  m.append(row)}}
function phoneLocalNote(m,text){
 m.append(h('div',{class:'card'},h('div',{class:'mute'},text)))}

/* ---------- Apps hub: Regular Apps / Home Screen Mode / Blocking / System Apps ---------- */
function buildAppsHub(m,dv){
 if(!appsSub){
  hubPicker(m,[['regular','Regular Apps',setAppsSub,'apps'],['kiosk','Home Screen Mode',setAppsSub,'home'],['blocking','Blocking',setAppsSub,'shield'],['system','System Apps',setAppsSub,'device']]);
  return}
 // The one "‹ Back" button for this whole page (renderDeviceDetail, below) already pops one level
 // at a time -- no second Back button nested in here too.
 if(appsSub==='regular'){
  renderApps(m,dv);
  const al=h('div',{class:'card'},h('h2',null,'Apps on this phone'),h('div',{class:'mute'},'Every app actually installed here, with the same Default / Allow / Block / Schedule controls as App rules.'));
  const installedSorted=dv.packages.filter(function(a){return a.p!==SELF_PACKAGE}).sort(function(a,b){
   const aa=cur(dv,a.p).mode==='allow'?0:1,ab=cur(dv,b.p).mode==='allow'?0:1;
   return aa!==ab?aa-ab:0});
  for(const a of installedSorted)al.append(appRow({p:a.p,l:a.l,s:a.s,prot:a.protected,hiddenOn:a.h?1:0},dv));
  if(!dv.packages.length)al.append(h('div',{class:'mute'},'The phone has not reported its apps yet.'));
  m.append(al);

  const appBox=h('div',{class:'card'},h('h2',null,'Install an APK'));const ar=h('div',{class:'row',style:'margin-top:8px'});
  const file=h('input',{type:'file',accept:'.apk,application/vnd.android.package-archive',style:'display:none'});
  file.onchange=async function(){const f=file.files[0];file.value='';if(!f)return;
   if(f.size>24*1024*1024){snack('That file is '+Math.round(f.size/1048576)+' MB. The limit is 24 MB. Use a download link for bigger apps.',1);return}
   snack('Uploading '+f.name+'…');
   try{const r=await fetch('/api/apk?name='+encodeURIComponent(f.name),{method:'PUT',body:f});const j=await r.json();if(!r.ok)throw new Error(j.error||'Upload failed');
    await call('POST','/api/devices/'+dv.id+'/command',{type:'install',args:{apkId:j.id}});snack('Install queued. The phone runs it at its next check-in (within about 5 minutes).');load()}catch(e){snack(e.message,1)}};
  ar.append(file,btn('Upload APK from this computer…','',async function(){file.click()}));
  ar.append(btn('Install from link…','',async function(){const url=prompt('Direct https:// link to an APK file');if(!url)return;await call('POST','/api/devices/'+dv.id+'/command',{type:'install',args:{url:url}});snack('Install queued. The phone runs it at its next check-in (within about 5 minutes).');load()}));
  ar.append(btn('Uninstall by package…','',async function(){const p=prompt('Package name to uninstall');if(!p)return;await call('POST','/api/devices/'+dv.id+'/command',{type:'uninstall',args:{packageName:p}});snack('Uninstall queued. The phone runs it at its next check-in (within about 5 minutes).');load()}));
  appBox.append(ar,h('div',{class:'mute small',style:'margin-top:8px'},'Uploads are kept for a week and limited to 24 MB. For bigger apps use a link, or let the person install from the Play Store with approval mode (Settings → Device).'));
  m.append(appBox);

  const codeBox=h('div',{class:'card'},h('h2',null,'Codes for this phone'),
   h('div',{class:'mute'},'Locked to this phone — giving the code to a different phone does nothing. Valid 1 hour, one use.'));
  const out=h('div',{style:'margin-top:10px'});
  const genCode=async function(type,minutes){
   const r=await call('POST','/api/codes',{type:type,deviceId:dv.id,minutes:minutes});
   out.textContent='';out.append(h('div',{class:'code'},r.code),
    h('div',{class:'mute small'},type==='freebrowse'?'Opens any site on this phone\'s Browser for '+r.minutes+' minutes.':'Type on this phone: '+(type==='install'?'Install an app.':'Remove agent.')))};
  codeBox.append(h('div',{class:'row',style:'margin-top:8px'},
   btn('Install code','',function(){genCode('install')}),
   btn('Removal code','outline',function(){genCode('uninstall')}),
   btn('Browse-freely code','outline',async function(){
    const v=await ask('Browse freely for how long?',[{key:'minutes',label:'Duration',options:[['15','15 minutes'],['30','30 minutes'],['60','1 hour'],['120','2 hours'],['240','4 hours']],value:'60'}],'Generate');
    if(!v)return;genCode('freebrowse',parseInt(v.minutes,10))})),out);
  m.append(codeBox);
 } else if(appsSub==='kiosk'){
  const hsCard=h('div',{class:'card'},h('h2',null,'Home screen mode'));
  hsCard.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Only allowed apps can be opened'),
   h('div',{class:'mute'},'The agent becomes this phone\'s home screen and shows only the apps you set to Allow, with your logo and your custom icons. An app set to Block is fully switched off, same as always; one left at Default or set to Soft block just has no icon here and stays installed and running in the background. Calls and texts still work. Settings is not available unless you Allow it, so add Wi-Fi from Settings → Network. On or off, nothing in between: the master code on the phone (Administrator) can turn it off there too, and that reaches this switch on the next sync, same as every other setting here.')),
   sw(dv.config.homeScreen,async function(on){
    if(on&&!confirm('Turn on Home screen mode on this phone? First make sure the apps the person needs (phone, messages, maps…) are set to Allow on the Regular Apps box, because only those will appear.')){render();return}
    dv.config.homeScreen=on;
    // Soft block only means anything while this switch is on; off a leftover "soft" app would be
    // stuck invisible in App rules (no button shows it as selected) instead of back at Default.
    if(!on)for(const pkg in dv.config.apps)if(dv.config.apps[pkg].mode==='soft')delete dv.config.apps[pkg];
    try{await saveConfigFor(dv,on?'Home screen mode on. The phone switches within about 5 minutes.':'Home screen mode off.')}catch(e){snack(e.message,1)}render()})));
  hsCard.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Hide the Administrator button'),
   h('div',{class:'mute'},'Home screen mode always shows an "Administrator" button below the allowed apps, to get back in. Hiding it removes that button for everyone who picks up the phone -- dialing *#*#636#*#* on the phone itself, or turning this back off from here, both still work.')),
   sw(dv.config.hideKioskAdmin,async function(on){dv.config.hideKioskAdmin=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()})));
  m.append(hsCard);
 } else if(appsSub==='blocking'){
  phoneLocalNote(m,'Set directly on the phone\'s own Administrator screen (Apps → Blocking) -- specific screens to bounce away from, or whole apps to hide at the Android level. Phone-only by design: it never touches this dashboard, so there\'s nothing to show or change here.');
 } else if(appsSub==='system'){
  const sal=h('div',{class:'card'},h('h2',null,'System apps'),
   h('div',{class:'mute'},'Apps built into the phone with no icon of their own -- not what shows in "Apps on this phone". Scanning asks the phone directly; it is not kept in sync automatically. Blocking one of these needs extra confirmation: it can break a part of the phone.'));
  sal.append(h('div',{style:'margin-top:8px'},btn('Scan for hidden system apps','tonal',async function(){
   await call('POST','/api/devices/'+dv.id+'/command',{type:'listSystemApps',args:{}});
   snack('Scanning. The phone reports back at its next check-in (within about 5 minutes) -- tap Refresh after a moment.');load()})));
  const lastScan=dv.results.slice().reverse().find(function(r){return r.type==='listSystemApps'});
  if(!lastScan)sal.append(h('div',{class:'mute small',style:'margin-top:8px'},'Not scanned yet.'));
  else if(!lastScan.ok)sal.append(h('div',{class:'mute small',style:'margin-top:8px'},'Last scan failed: '+lastScan.msg));
  else{
   let sysApps=[];try{sysApps=JSON.parse(lastScan.msg)}catch(e){}
   sal.append(h('div',{class:'mute small',style:'margin-top:8px'},sysApps.length+' found, as of '+ago(lastScan.at)+'.'));
   const sq=h('input',{type:'text',placeholder:'Search system apps',value:sysAppSearch,style:'width:100%;margin-top:8px'});
   sq.oninput=function(){sysAppSearch=sq.value;const pos=sq.selectionStart;render();const n=document.querySelector('#main input[placeholder="Search system apps"]');if(n){n.focus();n.setSelectionRange(pos,pos)}};
   sal.append(sq);
   const shown=sysApps.filter(function(a){const s=sysAppSearch.toLowerCase();return !s||(a.l||'').toLowerCase().includes(s)||a.p.toLowerCase().includes(s)});
   for(const a of shown)sal.append(appRow({p:a.p,l:a.l,s:true,prot:false,hiddenOn:a.h?1:0},dv,{triple:true}));
   if(!sysApps.length)sal.append(h('div',{class:'mute'},'No hidden system apps found.'));
   else if(!shown.length)sal.append(h('div',{class:'mute'},'No system apps match.'))}
  m.append(sal);
 }
}

/* ---------- Settings hub: Settings Menu / Permissions / Network / Updates / Device ---------- */
function buildSettingsHub(m,dv){
 if(!settingsSub){
  hubPicker(m,[['menu','Settings Menu',setSettingsSub,'apps'],['permissions','Permissions',setSettingsSub,'key'],['network','Network',setSettingsSub,'wifi'],['updates','Updates',setSettingsSub,'update'],['device','Device',setSettingsSub,'device']]);
  return}
 if(settingsSub==='menu')phoneLocalNote(m,'Most of the 15 categories here already work with no setup at all -- they point at a standard Android settings screen (Wi-Fi, Bluetooth, sound, display…) that works the same on every phone. A handful are this phone brand\'s own screens with no standard Android way to open them by name (notably some Motorola-only ones), so for just those few, Settings → Settings Menu → Learn on the phone itself captures the exact screen once, by physically going there -- genuinely needs to happen on the phone, there is no remote equivalent for an unlisted manufacturer screen. Everything else needs nothing here.');
 else if(settingsSub==='permissions')renderPermissions(m,dv);
 else if(settingsSub==='network')renderNetworkSection(m,dv);
 else if(settingsSub==='updates')renderUpdatesSection(m,dv);
 else if(settingsSub==='device')renderDeviceCard(m,dv);
}

function renderDeviceDetail(m,d){
 if(d.id!==lastOpenId){lastOpenId=d.id;try{sessionStorage.setItem('lastOpenId',d.id)}catch(e){}setDeviceSection(null);setAppsSub(null);setSettingsSub(null);setLockSub(null)}
 const back=h('button',{class:'btn outline'},'‹ All phones');back.onclick=function(){location.hash='devices'};
 m.append(h('div',{class:'row'},back,h('div',{class:'grow'}),btn('Refresh','tonal',load)));
 m.append(h('div',{class:'row',style:'margin-top:12px'},h('div',{class:'ico dev',style:'width:44px;height:44px;border-radius:12px;background:var(--primary-container);display:flex;align-items:center;justify-content:center;font-size:22px;flex:none;padding:0'},'📱'),
  h('div',{class:'grow'},h('h2',{style:'font-size:20px'},d.name),chipsFor(d,true))));

 const queue=async function(label,type,args){await call('POST','/api/devices/'+d.id+'/command',{type:type,args:args||{}});snack(label+' queued. The phone runs it at its next check-in (within about 5 minutes).');load()};
 const cmd=function(box,label,type,args,confirmMsg,cls){box.append(btn(label,cls||'tonal',async function(){
   if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}await queue(label,type,a)}))};

 // ----- Overview (status + location + activity; the phone app has no single tile for this, it's
 // the dashboard's own equivalent of AdminActivity's Overview toggle+status card) -----
 const ov=h('section');
 if(d.syncPaused)ov.append(h('div',{class:'card',style:'border-color:var(--warn)'},h('h2',null,'Not connected'),h('div',{class:'mute'},'This device is not connected to the dashboard because it was turned off (from this phone\'s own Admin screen, to save battery). It keeps enforcing whatever it had last. It reconnects only when someone turns it back on there — nothing here can reach it in the meantime.')));
 const info=h('div',{class:'card'},h('h2',null,'Phone'));
 info.append(kv('Recovery code',d.fallbackCode||'not seen yet (needs a check-in on a current build)'),
  kv('Android',d.info.android||'?'),kv('Agent build',(d.info.versionCode||'?')+(latest?' (latest '+latest.versionCode+')':'')),
  kv('Last check-in',ago(d.lastSeen)),kv('Battery',d.info.battery?d.info.battery.pct+'%'+(d.info.battery.charging?' (charging)':''):'unknown'),
  kv('Connection',d.info.wifi?(d.info.wifi.transport==='wifi'?'Wi-Fi '+(d.info.wifi.ssid||'(name hidden)')+(d.info.wifi.rssi?' · '+d.info.wifi.rssi+' dBm':''):d.info.wifi.transport==='mobile'?'SIM card'+(d.info.wifi.carrier?' — '+d.info.wifi.carrier:''):'None'):'unknown'),
  kv('Screen lock',d.info.screenLock===undefined?'unknown':d.info.screenLock?'On':'Off'),
  kv('PIN control',d.info.pinControl===undefined?'unknown':d.info.pinControl?'Ready: you can set or remove the lock':'Not active yet (see Lock)'),
  kv('Apps',d.packages.length+' ('+d.packages.filter(function(p){return p.h}).length+' hidden)'),
  kv('Sync',pendingCount(d)?pendingApps(d).map(function(p){const want=new Set(d.applied.hide);return (p.l||p.p)+(want.has(p.p)?' (hiding)':' (showing)')}).join(', ')+' — applies within about 5 minutes':'In sync'));
 if(d.adminPin&&d.adminPin.ok){
  const pinV=h('b',null,'••••');const showPin=h('button',{class:'btn outline',style:'padding:2px 10px;margin-left:8px'},'Show');showPin.onclick=function(){pinV.textContent=d.adminPin.pin};
  info.append(h('div',{class:'kv'},h('span',{class:'mute'},'PIN you set'),h('span',null,pinV,showPin)))}
 const nOv=Object.keys(d.overrides||{}).length;
 if(nOv)info.append(kv('Changed on the phone',nOv+' app(s)'));
 ov.append(info);
 ov.append(resetCard(d));
 const locCard=h('div',{class:'card'},h('h2',null,'Location'),
  h('div',{class:'mute'},'One fix at a time, only when you ask -- nothing here tracks the phone continuously or stores a history of where it\'s been.'));
 locCard.append(h('div',{style:'margin-top:8px'},btn('Find now','tonal',async function(){
  await call('POST','/api/devices/'+d.id+'/command',{type:'locate',args:{}});
  snack('Asked the phone to find itself. It reports back at its next check-in (within about 5 minutes) -- tap Refresh after a moment.');load()})));
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
 ov.append(locCard);
 const acts=h('div',{class:'card'},h('h2',null,'Activity'));
 if(d.pending)acts.append(h('div',{class:'act'},'⏳ '+d.pending+' command(s) waiting for the phone\'s next check-in'));
 for(const c of d.inflight)acts.append(h('div',{class:'act'},'⏳ '+(NAMES[c.type]||c.type)+' — sent '+ago(c.at)+', waiting for the phone to confirm'));
 for(const r of d.results.slice().reverse().slice(0,6).filter(function(r){return r.type!=='listSystemApps'&&r.type!=='locate'&&r.type!=='checkUpdates'}))acts.append(h('div',{class:'act'},(r.ok?'✅ ':'❌ ')+(NAMES[r.type]||r.type)+(r.msg?' — '+r.msg:'')+' · '+ago(r.at)));
 if(acts.children.length===1)acts.append(h('div',{class:'mute'},'No activity yet.'));
 ov.append(acts);

 // ----- Lock (hub: Lock / App lock / Administrator code -- every kind of lock in one place) -----
 const lk=h('section');
 if(!lockSub){
  hubPicker(lk,[['screen','Lock',setLockSub,'lock'],['applock','App lock',setLockSub,'lock'],['admincode','Administrator code',setLockSub,'key']]);
 } else if(lockSub==='screen'){
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
  lockBox.append(h('div',{class:'mute small',style:'margin-top:10px'},'Nobody can read a PIN or pattern the person chose themselves, not even the phone\'s maker. What you can do: take it off (Remove screen lock) or replace it (Set PIN), which needs PIN control to be ready (Overview). A PIN you set here is shown on the Overview page. To make sure every lock is one you set, switch on \'Only the administrator can set the screen lock\' in Settings → Device.'));
  lk.append(lockBox);
 } else if(lockSub==='applock'){
  const appLockOn=!!d.info.appLock;
  const alBox=h('div',{class:'card'},h('h2',null,'App lock'),
   h('div',{class:'mute'},appLockOn?'On: opening MDM Agent on the phone needs its own PIN.':'Off: anyone who opens MDM Agent on the phone sees it with no extra check.'));
  const alRow=h('div',{class:'row',style:'margin-top:8px'});
  alRow.append(btn(appLockOn?'Change App PIN…':'Turn on App lock…','',async function(){
    const pin=prompt('New app-lock PIN (4+ characters). Needed to open MDM Agent on the phone.');
    if(!pin)return;await queue('App lock','setAppLockPin',{pin:pin})}));
  if(appLockOn)alRow.append(btn('Turn off App lock','outline',async function(){
    if(!confirm('Turn off App lock on this phone?'))return;await queue('App lock','clearAppLockPin',{})}));
  alBox.append(alRow,h('div',{class:'mute small',style:'margin-top:10px'},'Nobody can read a PIN the person chose themselves, not even the phone\'s maker -- same as the screen lock PIN above. Setting one here replaces whatever was set on the phone itself. Needs an agent build that knows setAppLockPin/clearAppLockPin -- if the phone replies "unknown command", update the agent first (Settings → Device → Update agent) and try again once it has checked in. Applies within about 5 minutes.'));
  lk.append(alBox);
 } else if(lockSub==='admincode'){
  const mc=h('div',{class:'card'},h('h2',null,'Administrator code (master code)'),
   h('div',{class:'mute'},'Works on this phone with no internet (Agent → Administrator): lock, set PIN, install APKs, show/hide apps, release, erase. The phone only stores a scrambled version. Status: '+(d.masterSet?'set':'not set')+'.'));
  const code=h('input',{type:'password',placeholder:'New administrator code (6+ characters, letters/numbers/symbols)',style:'width:100%;margin-top:8px'});
  mc.append(code,h('div',{class:'row',style:'margin-top:8px'},
   btn(d.masterSet?'Change administrator code':'Set administrator code','',async function(){
    const v=code.value;if(!/^[\x20-\x7e]{6,64}$/.test(v)){snack('Use 6 to 64 normal keyboard characters.',1);return}
    const salt=crypto.getRandomValues(new Uint8Array(16));
    const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(v),'PBKDF2',false,['deriveBits']);
    const bits=new Uint8Array(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:salt,iterations:state.masterIterations},key,256));
    const hex=function(a){return [...a].map(function(b){return b.toString(16).padStart(2,'0')}).join('')};
    await call('PUT','/api/devices/'+d.id+'/master',{salt:hex(salt),hash:hex(bits)});code.value='';d.masterSet=true;snack('Administrator code saved. The phone receives it at its next check-in.');render()}),
   d.masterSet?btn('Remove','outline',async function(){if(!confirm('Remove this phone\'s administrator code? Its on-phone admin panel stops working.'))return;await call('DELETE','/api/devices/'+d.id+'/master');d.masterSet=false;snack('Administrator code removed.');render()}):null));
  lk.append(mc);
  lk.append(h('div',{class:'card'},h('h2',null,'Recovery code'),
   h('div',{class:'mute'},'This phone\'s own code, generated by the phone itself the first time it ran. Always works here, with no internet, even with no administrator code set — different from every other phone\'s.'),
   d.fallbackCode?h('div',{class:'code',style:'margin-top:8px;font-size:22px;letter-spacing:2px'},d.fallbackCode):h('div',{class:'mute small',style:'margin-top:8px'},'Not seen yet — shows up after this phone\'s first check-in.')));
 }

 // ----- Apps (hub: Regular Apps / Home Screen Mode / Blocking / System Apps) -----
 const ap=h('section');buildAppsHub(ap,d);

 // ----- Add-ons (the allowed-sites list and "make Browser the only browser" -- the phone's own
 // Add-ons screen is just the two Browser-install toggles, which are phone-local; the allowlist
 // itself has always lived here) -----
 const ad=h('section');renderSites(ad,d);

 // ----- Settings (hub: Settings Menu / Permissions / Network / Updates / Device) -----
 const se=h('section');buildSettingsHub(se,d);

 // ----- Messages -----
 const msgs=h('section');
 if((d.messages||[]).length){
  const mc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Messages from the phone ('+d.messages.length+')'));
  for(const msg of d.messages.slice().reverse()){
   mc.append(h('div',{class:'app'},h('div',{class:'grow'},h('div',null,msg.msg),h('div',{class:'mute small'},ago(msg.at))),
    btn('Dismiss','outline',async function(){await call('DELETE','/api/devices/'+d.id+'/messages?id='+encodeURIComponent(msg.id));load()})))}
  msgs.append(mc)
 } else msgs.append(h('div',{class:'card'},h('div',{class:'mute'},'No messages from the phone yet.')));

 // ----- Log (dashboard-only -- the phone app has no log viewer of its own) -----
 const lg=h('section');
 const pl=h('div',{class:'card'},h('h2',null,'Dashboard log'),h('div',{class:'mute'},'What was sent from here. Compare against Phone log below to see whether it actually arrived -- it should, within about 5 minutes.'));
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

 // ----- section picker -----
 // The exact same home screen as the phone's own Administrator screen: a 2-column grid of big red
 // icon tiles (Lock / Apps / Add-ons / App lock / Settings / Messages, same icons, same order --
 // see AdminActivity's own tile grid), tapping one replaces the grid with that section and a
 // "‹ Back" button, same as AdminActivity.openSection/onBackPressed. Two extra tiles the phone
 // doesn't have: Overview (the phone shows this as a toggle above its grid, not a tile) and Log
 // (the phone has no log viewer of its own). Apps and Settings are real two-level hubs underneath,
 // same picker-then-section idiom as AdminActivity's buildAppsHub/buildSettingsHub.
 const sections={overview:['Overview','device',ov],lock:['Lock','lock',lk],apps:['Apps','apps',ap],
  addons:['Add-ons','globe',ad],settings:['Settings','key',se],
  messages:['Messages','message',msgs],log:['Log','doc',lg]};
 const openSection=function(key){setAppsSub(null);setSettingsSub(null);setLockSub(null);setDeviceSection(key);render()};

 // ----- search: every section and sub-section, same as "search Settings" on a phone -----
 const searchIndex=[
  ['Overview',function(){openSection('overview')}],
  ['Lock — Lock / unlock / screen PIN',function(){setDeviceSection('lock');setLockSub('screen');render()}],
  ['Lock — App lock',function(){setDeviceSection('lock');setLockSub('applock');render()}],
  ['Lock — Administrator code (master code)',function(){setDeviceSection('lock');setLockSub('admincode');render()}],
  ['Apps — Regular Apps',function(){setDeviceSection('apps');setAppsSub('regular');render()}],
  ['Apps — Home Screen Mode',function(){setDeviceSection('apps');setAppsSub('kiosk');render()}],
  ['Apps — Blocking',function(){setDeviceSection('apps');setAppsSub('blocking');render()}],
  ['Apps — System Apps',function(){setDeviceSection('apps');setAppsSub('system');render()}],
  ['Add-ons (Browser)',function(){openSection('addons')}],
  ['Settings — Settings Menu',function(){setDeviceSection('settings');setSettingsSub('menu');render()}],
  ['Settings — Permissions',function(){setDeviceSection('settings');setSettingsSub('permissions');render()}],
  ['Settings — Network / Wi-Fi',function(){setDeviceSection('settings');setSettingsSub('network');render()}],
  ['Settings — Updates',function(){setDeviceSection('settings');setSettingsSub('updates');render()}],
  ['Settings — Device',function(){setDeviceSection('settings');setSettingsSub('device');render()}],
  ['Turn off / on the phone\'s own Administrator panel',function(){setDeviceSection('settings');setSettingsSub('device');render()}],
  ['Disable fingerprint / face / biometric unlock',function(){setDeviceSection('settings');setSettingsSub('device');render()}],
  ['Apps — Home Screen Mode — hide the Administrator button',function(){setDeviceSection('apps');setAppsSub('kiosk');render()}],
  ['Messages',function(){openSection('messages')}],['Log',function(){openSection('log')}]];
 const sWrap=h('div',{class:'search'});
 const sInput=h('input',{type:'text',placeholder:'Search every setting…',value:deviceSearch});
 const sResults=h('div',{class:'search-results'});
 const renderSResults=function(){
  sResults.textContent='';
  if(!deviceSearch){sResults.style.display='none';return}
  const q=deviceSearch.toLowerCase();
  const hits=searchIndex.filter(function(e){return e[0].toLowerCase().includes(q)});
  if(!hits.length){sResults.style.display='block';sResults.append(h('div',{class:'mute'},'No settings match.'));return}
  sResults.style.display='block';
  for(const [label,go] of hits){const row=h('div',null,label);row.onclick=function(){deviceSearch='';go()};sResults.append(row)}};
 sInput.oninput=function(){deviceSearch=sInput.value;const pos=sInput.selectionStart;render();const n=document.querySelector('#main input[placeholder="Search every setting…"]');if(n){n.focus();n.setSelectionRange(pos,pos)}};
 renderSResults();
 sWrap.append(h('div',{class:'ico'},'🔎'),sInput,sResults);
 m.append(sWrap);

 if(!deviceSection){
  const grid=h('div',{class:'tilegrid-lg'});
  for(const key of ['lock','apps','addons','settings','messages','overview','log']){
   const [label,icon]=sections[key];
   const tile=h('button',{class:'tile'},h('div',{class:'ico'},svgIcon(icon)),h('div',null,label));
   tile.onclick=function(){openSection(key)};
   grid.append(tile)}
  m.append(grid);
 } else {
  // One "‹ Back" button total, however deep: a sub-picker (Apps/Settings/Lock) pops back to its
  // own hub first, everything else pops straight to the home grid -- never two Back buttons
  // stacked for the same tap.
  const back=h('button',{class:'btn outline',style:'margin-bottom:12px'},'‹ Back');
  back.onclick=function(){
   if(appsSub||settingsSub||lockSub){setAppsSub(null);setSettingsSub(null);setLockSub(null);render()}
   else{setDeviceSection(null);render()}};
  m.append(back,sections[deviceSection][2]);
 }
}

/* ---------- apps ---------- */
// The agent is always launchable (it has its own icon, unless hidden) so it always reports itself
// as an installed app -- nothing useful ever comes from seeing it in its own app list, and there's
// no sane action to take on it here (it can't be blocked off or uninstalled from itself).
const SELF_PACKAGE='com.familymdm.agent';
function allApps(dv){
 const seen=new Map();
 for(const a of dv.packages){
  if(a.p===SELF_PACKAGE)continue;
  const o=seen.get(a.p)||{p:a.p,l:a.l,s:a.s,prot:a.protected,hiddenOn:0};if(a.h)o.hiddenOn++;seen.set(a.p,o)}
 for(const p in dv.config.apps)if(p!==SELF_PACKAGE&&!seen.has(p))seen.set(p,{p:p,l:dv.config.apps[p].label||p,s:false,prot:false,hiddenOn:0});
 // Allowed apps first (what you actually came here to check), then everything else alphabetically.
 return [...seen.values()].sort(function(a,b){
  const aa=cur(dv,a.p).mode==='allow'?0:1,ab=cur(dv,b.p).mode==='allow'?0:1;
  return aa!==ab?aa-ab:(a.l||a.p).localeCompare(b.l||b.p)})}
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
     btn('Approve','',async function(){dv.config.apps[pkg]={mode:'allow',label:label};await saveConfigFor(dv,'Approved '+label+'. It appears on the phone within about 5 minutes.');render()}),
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
  await saveConfigFor(dv,'Saved '+(a.l||a.p)+'. The phone applies it within about 5 minutes.');delete draft[a.p];render()});
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
 // Never offer to uninstall a protected package (MDM Agent itself included -- it blocks its own
 // uninstall at the Android level anyway, see PolicyApplier's setUninstallBlocked, so the button
 // would only ever fail) or a system app, which Android won't actually remove either.
 if(!a.s&&!a.prot&&installedHere)ctl.append(btn('Uninstall','danger',async function(){
  if(!confirm('Uninstall '+(a.l||a.p)+' from '+dv.name+'? This removes the app and its data from the phone.'))return;
  await call('POST','/api/devices/'+dv.id+'/command',{type:'uninstall',args:{packageName:a.p}});
  snack('Uninstall queued. The phone does it at its next check-in (within about 5 minutes).');load()}));
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
  sw(dv.config.restrictBrowsing,async function(on){dv.config.restrictBrowsing=on;try{await saveConfigFor(dv,on?'This is now the only browser. Phones apply it within about 5 minutes.':'Chrome and other browsers can be used again.')}catch(e){snack(e.message,1)}}))));
 renderRequestsCard(m,(dv.siteRequests||[]).map(function(r){return{path:'/api/devices/'+dv.id+'/site-requests',deviceName:dv.name,url:r.url,at:r.at}}),dv);
 renderCloneCard(m,dv);
 renderSitesEditor(m,dv);
}
async function addSite(dv,entry){
 const key=(entry.type)+':'+hostOfUrl(entry.url)+(entry.type==='exact'?':'+Date.now():'');
 dv.config.sites=dv.config.sites||{};
 dv.config.sites[key]={type:entry.type,url:entry.url,label:entry.label,blockImages:false,installable:true};
 await saveConfigFor(dv,'Added '+(entry.label||entry.url)+'. Phones pick it up within about 5 minutes.');
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
  snack('Cloned. Phones pick up the change within about 5 minutes.');load()})));
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
  snack(on?'Locking down. The computer applies this within about 5 minutes.':'Lockdown turned off.');load()}),
  ' Lock down internet access (everything except the allowed programs below)'));
 if(w.enabled!==w.reportedEnabled)controls.append(h('div',{class:'mute small',style:'margin-top:4px'},'⏳ Waiting for the computer to apply this.'));
 controls.append(h('div',{class:'row',style:'margin-top:12px'},
  btn('Rename','outline',async function(){const name=prompt('Name for this computer',w.name);if(!name)return;await call('PUT','/api/windevices/'+w.id,{name:name});load()}),
  btn('Uninstall from this computer','danger',async function(){if(!confirm('Remove LockGuard from '+w.name+' entirely? It will take internet access and the firewall rules off automatically, next time it checks in.'))return;await call('POST','/api/windevices/'+w.id+'/command',{type:'uninstall'});snack('Queued. It\'ll remove itself within about 5 minutes.')}),
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
  btn('Upload logo…','',async function(){const blob=await pickImage(256,false);if(!blob)return;await putImage('/api/logo',blob);iconVer.__logo=Date.now();snack('Logo saved. Phones pick it up within about 5 minutes.');render()}),
  btn('Remove logo','outline',async function(){await call('DELETE','/api/logo');iconVer.__logo=Date.now();snack('Logo removed.');render()})));
 m.append(logoCard);
 m.append(h('div',{class:'card'},h('h2',null,'Everything else moved'),
  h('div',{class:'mute'},'New-apps approval, Factory Reset Protection, Home screen mode, auto-update, Wi-Fi reporting, the master code, and every restriction switch are now each phone\'s own — open a phone and look for its Settings box.')));
}
/* ---------- clients (admin-only: each owns a slice of devices/browsers, created here) ---------- */
function renderClients(m){
 m.append(h('div',{class:'card'},h('h2',null,'Clients'),
  h('div',{class:'mute'},'Each client logs in on their own, sees only their own phones and browsers, and generates their own codes to enroll them. You (the administrator) still see everything, from every client, right here.')));
 const addCard=h('div',{class:'card'},h('h2',null,'Add a client'));
 const loginName=h('input',{type:'text',placeholder:'Login name (3-32 letters/numbers/-/_, what they log in with)',style:'width:100%;margin-top:8px'});
 const name=h('input',{type:'text',placeholder:'Display name (optional, defaults to the login name)',style:'width:100%;margin-top:8px'});
 const password=h('input',{type:'password',placeholder:'Password (6+ characters) -- give this to them yourself',style:'width:100%;margin-top:8px'});
 addCard.append(loginName,name,password,h('div',{style:'margin-top:8px'},btn('Add client','tonal',async function(){
  try{
   await call('POST','/api/clients',{loginName:loginName.value.trim(),name:name.value.trim(),password:password.value});
   snack('Client added. Give them the login name and password to sign in.');
   loginName.value='';name.value='';password.value='';
   clients=await call('GET','/api/clients');render()
  }catch(e){snack(e.message,1)}})));
 m.append(addCard);

 const list=h('div',{class:'card'});
 if(!clients.length)list.append(h('div',{class:'mute'},'No clients yet.'));
 for(const c of clients){
  const n=devices.filter(function(d){return d.ownerId===c.id}).length+browsers.filter(function(d){return d.ownerId===c.id}).length;
  list.append(h('div',{class:'app'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},c.name),
   h('div',{class:'mute small'},'Login: '+c.id+' · '+n+' device(s)')),
   btn('Delete','danger',async function(){
    if(!confirm('Delete client "'+c.name+'"? They can no longer log in. Their devices stay exactly as they are -- not deleted, not reassigned -- just invisible to every client until you hand them to a new or recreated account.'))return;
    await call('DELETE','/api/clients/'+c.id);clients=await call('GET','/api/clients');render()})))}
 m.append(list);
}
/** Everything that used to be shared by every phone, now that specific phone's own. */
/* ---------- Settings hub: Permissions / Network / Updates / Device (Settings Menu is phone-only) ---------- */
function renderPermissions(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'Restrictions'),h('div',{class:'mute'},'Each switch saves immediately. The phone picks changes up within about 5 minutes.'));
 for(const k in state.restrictions){
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},state.restrictions[k].label),
   sw(dv.config.restrictions[k],async function(on){dv.config.restrictions[k]=on;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}})))}
 m.append(card,h('div',{class:'card'},h('h2',null,'Good to know'),
  h('div',{class:'mute'},'Blocking Developer options also turns off USB debugging, so adb stops working. If you need to get back in, send "Release device" from Settings → Device (or use recovery mode).')));
}
function renderNetworkSection(m,dv){
 const wl=h('div',{class:'card'},h('h2',null,'Wi-Fi'));
 wl.append(kv('Now',dv.info.wifi?(dv.info.wifi.transport==='wifi'?(dv.info.wifi.ssid||'(name hidden: Location is off)'):dv.info.wifi.transport==='mobile'?'SIM card'+(dv.info.wifi.carrier?' — '+dv.info.wifi.carrier:''):'No connection'):'unknown'));
 for(const n of dv.wifiNetworks){const pw=h('span',{class:'mono'},n.password?'••••••••':'(open)');
  const show=h('button',{class:'btn outline',style:'padding:2px 10px;margin-left:8px'},'Show');show.onclick=function(){pw.textContent=n.password||'(open)'};
  wl.append(h('div',{class:'kv'},h('span',null,n.ssid),h('span',null,pw,n.password?show:null)))}
 wl.append(h('div',{class:'mute small',style:'margin-top:8px'},'Android does not let apps read saved Wi-Fi passwords, so networks you add here are remembered for you.'));
 wl.append(h('div',{style:'margin-top:8px'},btn('Add Wi-Fi network…','',async function(){
   const v=await ask('Add a Wi-Fi network',[{key:'ssid',label:'Network name',max:32},{key:'password',label:'Password (leave empty for an open network)',max:63}],'Add to phone');
   if(!v||!v.ssid)return;await call('POST','/api/devices/'+dv.id+'/command',{type:'addWifi',args:{ssid:v.ssid,password:v.password}});snack('Add Wi-Fi queued. The phone runs it at its next check-in (within about 5 minutes).');load()})));
 m.append(wl);
}
function renderUpdatesSection(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'App updates'),
  h('div',{class:'mute'},'Checks only the apps already on this phone that aren\'t blocked, plus whatever Waze, Google Maps and Android Auto need to keep working -- never the whole Play Store.'));
 card.append(h('div',{style:'margin-top:8px'},btn('Check for updates','tonal',async function(){
  await call('POST','/api/devices/'+dv.id+'/command',{type:'checkUpdates',args:{}});
  snack('Checking. The phone reports back at its next check-in (within about 5 minutes) -- tap Refresh after a moment.');load()})));
 const lastCheck=dv.results.slice().reverse().find(function(r){return r.type==='checkUpdates'});
 if(!lastCheck)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Not checked yet.'));
 else if(!lastCheck.ok)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Last check failed: '+lastCheck.msg));
 else{
  let updates=[];try{updates=JSON.parse(lastCheck.msg)}catch(e){}
  card.append(h('div',{class:'mute small',style:'margin-top:8px'},(updates.length?updates.length+' update(s) available':'Everything checked is already up to date')+', as of '+ago(lastCheck.at)+'.'));
  for(const u of updates){
   const row=h('div',{class:'app'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},u.l||u.p),
    h('div',{class:'mute small'},'build '+u.installed+' → '+u.available)),
    btn('Update','tonal',async function(){
     await call('POST','/api/devices/'+dv.id+'/command',{type:'updateApp',args:{packageName:u.p}});
     snack('Update queued for '+(u.l||u.p)+'. Runs at the phone\'s next check-in (within about 5 minutes).');load()}));
   card.append(row)}}
 m.append(card);
 m.append(h('div',{class:'card'},h('h2',null,'Agent updates'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Update the agent automatically'),
  h('div',{class:'mute'},'The phone checks GitHub for a newer build every 6 hours and installs it itself. You can always update it yourself from Settings → Device, or from the phone\'s admin panel.')),
  sw(dv.config.autoUpdate,async function(on){dv.config.autoUpdate=on;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}}))));
 m.append(h('div',{class:'card'},h('h2',null,'System updates'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Freeze Android system updates'),
  h('div',{class:'mute'},'Android will not let any app block OTA updates completely -- a freeze can only last up to 90 days at a time, with a mandatory 60-day gap before the next one. This sets the most freeze Android allows, back to back, which covers most of the year but leaves about two months of it open to an update landing. There is no setting that closes that gap; this is a limit of Android itself, not this app.')),
  sw(dv.config.freezeUpdates,async function(on){dv.config.freezeUpdates=on;try{await saveConfigFor(dv,on?'Freeze scheduled. The phone applies it within about 5 minutes.':'Freeze removed; the phone can update normally again.')}catch(e){snack(e.message,1)}}))));
}
function renderDeviceCard(m,dv){
 const nOv=Object.keys(dv.overrides||{}).length;
 const dr=h('div',{class:'row'});
 const cmdHere=function(label,type,args,confirmMsg,cls){dr.append(btn(label,cls||'tonal',async function(){
  if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}
  await call('POST','/api/devices/'+dv.id+'/command',{type:type,args:a||{}});snack(label+' queued. The phone runs it at its next check-in (within about 5 minutes).');load()}))};
 const devBox=h('div',{class:'card'},h('h2',null,'Phone'));
 cmdHere('Sync now','sync');cmdHere('Reboot','reboot');
 if(needsUpdate(dv))cmdHere('Update agent to build '+latest.versionCode,'updateAgent',{});else cmdHere('Update agent','updateAgent',{},null,'outline');
 if(nOv)cmdHere('Clear phone-side changes','clearOverrides',{},'Forget the app changes made on the phone with the master code?','outline');
 devBox.append(dr);
 const dd=h('div',{class:'row',style:'margin-top:12px'});
 const cmdDd=function(label,type,args,confirmMsg,cls){dd.append(btn(label,cls||'tonal',async function(){
  if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}
  await call('POST','/api/devices/'+dv.id+'/command',{type:type,args:a||{}});snack(label+' queued. The phone runs it at its next check-in (within about 5 minutes).');load()}))};
 cmdDd('Release device','release',{uninstall:false},'Release this device? It stops being managed and every restriction is removed.','outline');
 cmdDd('Release & remove app','release',{uninstall:true},'Release the device AND start removing the agent app? The phone will ask to confirm.','outline');
 cmdDd('Wipe','wipe',null,'ERASE this device completely?','danger');
 dd.append(btn('Delete device','outline',async function(){if(!confirm('Delete this device from the dashboard? The phone stays managed; use Release first if you want to actually free the phone.'))return;await call('DELETE','/api/devices/'+dv.id);location.hash='devices'}));
 devBox.append(dd);m.append(devBox);

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
   dv.config.frpAccounts=list;await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.');await load()}),
  btn('Turn off','outline',async function(){dv.config.frpAccounts=[];await saveConfigFor(dv,'Reset protection removed.');await load()})));
 frp.append(h('div',{class:'mute small',style:'margin-top:10px'},'How to get your Google account ID:'),
  h('ol',{class:'mute small',style:'margin:4px 0 0 18px;padding:0'},
   h('li',null,'Open ',h('a',{href:'https://developers.google.com/people/api/rest/v1/people/get',target:'_blank',rel:'noopener'},'the Google People API page'),' and click "Try it".'),
   h('li',null,'Set resourceName to people/me and personFields to metadata, then click Execute and sign in with the Google account you control.'),
   h('li',null,'In the result, copy the long number (about 21 digits) next to "id" or after "people/". Paste it above.')),
  h('div',{class:'mute small',style:'margin-top:8px'},'Keep that Google account safe: whoever can sign in to it can set the phone up again after a reset.'));
 m.append(frp);
 m.append(h('div',{class:'card'},h('h2',null,'Accessibility'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block accessibility services'),
  h('div',{class:'mute'},'A sideloaded app can ask for an accessibility service and use it to read the screen and tap things on the person\'s behalf -- a known way around app controls. Turning this on switches off every accessibility service on the phone, including ones used for real accessibility needs, so leave it off if anyone here relies on one.')),
  sw(dv.config.blockAccessibility,async function(on){
   if(on&&!confirm('Turn off every accessibility service on this phone? Do this only if nobody here needs one for real accessibility use.'))return;
   dv.config.blockAccessibility=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()}))));
 m.append(h('div',{class:'card'},h('h2',null,'Fingerprint / face unlock'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Disable fingerprint/face at the lock screen'),
  h('div',{class:'mute'},'The "Block enrolling fingerprint or face unlock" restriction (App rules) only stops a new one from being set up -- it does nothing to a fingerprint or face already enrolled, which keeps right on working. This is the actual off switch: forces PIN/pattern/password at the lock screen, whatever is already enrolled.')),
  sw(dv.config.disableBiometricUnlock,async function(on){
   dv.config.disableBiometricUnlock=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()}))));
 m.append(h('div',{class:'card'},h('h2',null,'Phone info'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Show which Wi-Fi the phone is on'),
  h('div',{class:'mute'},'Android only reveals the network name when its Location setting is on, so this switch turns that on for the phone. The dashboard shows the network name and signal, never where the phone is. Battery level is always shown.')),
  sw(dv.config.reportWifi,async function(on){dv.config.reportWifi=on;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}}))));
 m.append(h('div',{class:'card'},h('h2',null,'Only control from the dashboard'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Turn off the phone\'s own Administrator panel'),
  h('div',{class:'mute'},'While this is on, entering the master code on the phone itself does nothing -- every change has to come from here instead. Turning it back off also has to happen from here, so only use this if you expect the phone to stay able to reach this dashboard.')),
  sw(dv.config.phoneAdminLocked,async function(on){
   if(on&&!confirm('Turn off this phone\'s own Administrator panel? You\'ll only be able to turn it back on from this dashboard -- make sure the phone can still reach it.'))return;
   dv.config.phoneAdminLocked=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()}))));
 m.append(h('div',{class:'card'},h('h2',null,'App icon'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Hide the app icon'),
  h('div',{class:'mute'},'Removes the agent\'s icon from the launcher and app drawer. Nothing else changes -- it keeps running and enforcing everything exactly the same. Two ways back: turn this off here again, or dial *#*#636#*#* right on the phone (works even offline; a few phone brands\' own dialer apps don\'t support this standard Android feature).')),
  sw(dv.config.hideAppIcon,async function(on){
   if(on&&!confirm('Hide the app icon on this phone? Turn it back on here, or dial *#*#636#*#* on the phone itself.'))return;
   dv.config.hideAppIcon=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()}))));
 // The administrator (master) code and the phone's own recovery code moved to the Lock tile's
 // "Administrator code" row -- every kind of lock lives together there now, not scattered.
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
