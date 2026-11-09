// Dashboard pages. Material 3 styling with plain CSS and vanilla JS (no build step, no libraries).

const STYLE = String.raw`
/* Neutral, professional console look (light by default, dark when the device is set to dark).
   Every colour lives in these variables, so the whole dashboard can be re-themed from here. */
:root{
  color-scheme:light;
  --primary:#4F46E5;--on-primary:#fff;--primary-container:#EEF0FF;--on-primary-container:#3730A3;
  --secondary-container:#F2F4F7;--on-secondary-container:#1D2939;
  --surface:#F6F7F9;--surface-1:#FFFFFF;--surface-2:#FAFBFC;--surface-3:#F2F4F7;
  --on-surface:#101828;--on-surface-variant:#667085;--outline:#D0D5DD;--outline-variant:#E4E7EC;
  --error:#B42318;--error-container:#FEF3F2;--ok:#067647;--ok-container:#ECFDF3;--warn:#B54708;--warn-container:#FFFAEB;
  --shadow:0 1px 2px rgba(16,24,40,.05);
}
@media (prefers-color-scheme:dark){:root{
  color-scheme:dark;
  --primary:#6366F1;--on-primary:#fff;--primary-container:#23264F;--on-primary-container:#C7D2FE;
  --secondary-container:#1D222C;--on-secondary-container:#E4E7EC;
  --surface:#0B0D12;--surface-1:#12151C;--surface-2:#161A22;--surface-3:#1D222C;
  --on-surface:#F2F4F7;--on-surface-variant:#98A2B3;--outline:#333A47;--outline-variant:#232833;
  --error:#F97066;--error-container:#3A1613;--ok:#47CD89;--ok-container:#0F2E20;--warn:#FDB022;--warn-container:#3A2A07;
  --shadow:none;
}}
*{box-sizing:border-box}
html{background:var(--surface)}
body{margin:0;background:var(--surface);color:var(--on-surface);font:14px/1.5 Inter,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
a{color:var(--primary)}
/* top bar */
header.bar{position:sticky;top:0;z-index:5;background:var(--surface-1);border-bottom:1px solid var(--outline-variant)}
.bar .top{display:flex;align-items:center;gap:14px;max-width:1180px;margin:0 auto;padding:0 20px;height:56px}
.brand{display:flex;align-items:center;gap:10px;font-weight:600;font-size:15px;color:var(--on-surface);text-decoration:none;cursor:pointer}
.brand .logo{width:28px;height:28px;border-radius:8px;background:var(--primary);display:flex;align-items:center;justify-content:center}
.brand .logo svg{width:16px;height:16px}
.crumbs{display:flex;align-items:center;gap:8px;color:var(--on-surface-variant);font-size:14px;min-width:0;flex:1;overflow:hidden;white-space:nowrap}
.crumbs .sep{opacity:.5}.crumbs a{color:var(--on-surface-variant);text-decoration:none;cursor:pointer}.crumbs a:hover{color:var(--on-surface)}
.crumbs b{color:var(--on-surface);font-weight:500;overflow:hidden;text-overflow:ellipsis}
.bar .who{color:var(--on-surface-variant);font-size:13px}
.bar a.out{color:var(--on-surface-variant);text-decoration:none;font-size:13px;padding:6px 10px;border-radius:8px}
.bar a.out:hover{background:var(--surface-3);color:var(--on-surface)}
main{max-width:1180px;margin:0 auto;padding:24px 20px 60px}
/* page header */
.pagehead{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:20px}
.pagehead h1{font-size:24px;font-weight:600;margin:0;letter-spacing:-.01em}
.pagehead .sub{color:var(--on-surface-variant);font-size:14px}
h2{font-size:15px;font-weight:600;margin:0 0 4px}
.card{background:var(--surface-1);border:1px solid var(--outline-variant);border-radius:12px;padding:18px;margin-bottom:16px;box-shadow:var(--shadow)}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.grow{flex:1;min-width:0}
.mute{color:var(--on-surface-variant);font-size:13px}.small{font-size:12px}
.mono{font-family:ui-monospace,Menlo,Consolas,monospace}
/* buttons */
button.btn{font:500 13px/1 Inter,system-ui,sans-serif;border:1px solid transparent;border-radius:8px;padding:10px 14px;cursor:pointer;background:var(--primary);color:var(--on-primary);transition:filter .12s,background .12s}
button.btn:hover{filter:brightness(1.08)}
button.btn.tonal{background:var(--surface-1);color:var(--on-surface);border-color:var(--outline);box-shadow:var(--shadow)}
button.btn.tonal:hover{background:var(--surface-3);filter:none}
button.btn.outline{background:transparent;color:var(--on-surface);border-color:var(--outline)}
button.btn.outline:hover{background:var(--surface-3);filter:none}
button.btn.danger{background:transparent;color:var(--error);border-color:var(--error)}
button.btn:disabled{opacity:.45;cursor:default}
input[type=text],input[type=time],input[type=password],select,textarea{font:inherit;color:inherit;background:var(--surface-1);border:1px solid var(--outline);border-radius:8px;padding:9px 12px;min-width:0}
input:focus,select:focus,textarea:focus{outline:2px solid color-mix(in srgb,var(--primary) 45%,transparent);border-color:var(--primary)}
/* status chips */
.chip{display:inline-flex;align-items:center;gap:5px;border-radius:999px;padding:2px 10px;font-size:12px;font-weight:500;background:var(--surface-3);color:var(--on-surface-variant);margin:0 6px 0 0}
.chip svg{width:13px;height:13px;flex:none}
.chip.ok{background:var(--ok-container);color:var(--ok)}.chip.bad{background:var(--error-container);color:var(--error)}.chip.warn{background:var(--warn-container);color:var(--warn)}
.dot{width:7px;height:7px;border-radius:50%;background:currentColor;display:inline-block}
.seg{display:inline-flex;border:1px solid var(--outline);border-radius:8px;overflow:hidden}
.seg button{background:var(--surface-1);border:0;border-right:1px solid var(--outline);padding:6px 12px;color:var(--on-surface);font:500 13px Inter,system-ui,sans-serif;cursor:pointer}
.seg button:last-child{border-right:0}
.seg button.on{background:var(--primary-container);color:var(--on-primary-container)}
.seg button.on.block{background:var(--error-container);color:var(--error)}
.app{display:flex;gap:12px;align-items:flex-start;padding:12px 0;border-top:1px solid var(--outline-variant)}
.app:first-child{border-top:0}
.app img,.app .ph{width:40px;height:40px;border-radius:10px;flex:none;background:var(--surface-3)}
/* toggle switch */
.sw{position:relative;width:40px;height:22px;flex:none}
.sw input{opacity:0;position:absolute;inset:0;margin:0;cursor:pointer;z-index:1}
.sw i{position:absolute;inset:0;border-radius:11px;background:var(--outline);transition:.15s}
.sw i::after{content:"";position:absolute;top:2px;left:2px;width:18px;height:18px;border-radius:50%;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.25);transition:.15s}
.sw input:checked+i{background:var(--primary)}
.sw input:checked+i::after{left:20px}
.setting{display:flex;gap:16px;align-items:center;padding:12px 0;border-top:1px solid var(--outline-variant)}
.setting:first-child{border-top:0}
.act{padding:8px 0;border-top:1px solid var(--outline-variant);font-size:13px}
.act:first-child{border-top:0}
.code{font:600 28px ui-monospace,Menlo,Consolas,monospace;letter-spacing:4px;background:var(--surface-3);border-radius:10px;padding:10px 16px;display:inline-block;margin:8px 0}
pre.cmd{background:var(--surface-3);border-radius:10px;padding:12px;white-space:pre-wrap;word-break:break-all;font:12px ui-monospace,Menlo,Consolas,monospace;margin:8px 0}
.days{display:flex;gap:4px;margin:8px 0}
.days button{width:34px;height:34px;border-radius:50%;border:1px solid var(--outline);background:none;color:var(--on-surface);cursor:pointer;font-weight:500}
.days button.on{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.sched{background:var(--surface-3);border-radius:10px;padding:10px 12px;margin-top:8px}
#snack{position:fixed;left:12px;right:12px;bottom:20px;max-width:520px;margin:auto;background:#101828;color:#fff;border-radius:10px;padding:12px 16px;display:none;z-index:99;box-shadow:0 8px 24px rgba(16,24,40,.25)}
.m3dlg{border:1px solid var(--outline-variant);border-radius:14px;background:var(--surface-1);color:var(--on-surface);padding:24px;max-width:440px;width:calc(100% - 32px);box-shadow:0 20px 48px rgba(16,24,40,.25)}
.m3dlg::backdrop{background:rgba(16,24,40,.5)}
.m3dlg h2{font-size:18px;font-weight:600;margin-bottom:12px}
.m3dlg label{display:block;margin:12px 0 4px;font-size:12px;font-weight:500;color:var(--on-surface-variant)}
.m3dlg input,.m3dlg select{width:100%}
.m3dlg .row{justify-content:flex-end;margin-top:20px}
.kv{display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-top:1px solid var(--outline-variant)}
.kv:first-child{border-top:0}.kv b{font-weight:500;text-align:right;word-break:break-word}
.login{max-width:380px;margin:14vh auto 0;padding:0 16px}
.login .card{padding:28px}
/* device list (table) */
.toolbar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.table{background:var(--surface-1);border:1px solid var(--outline-variant);border-radius:12px;overflow:hidden;box-shadow:var(--shadow);margin-bottom:16px}
.trow{display:grid;grid-template-columns:28px minmax(200px,2.2fr) 1fr 1fr 1.2fr 24px;gap:12px;align-items:center;padding:14px 18px;border-top:1px solid var(--outline-variant);cursor:pointer}
.trow:first-child{border-top:0}
.trow.thead{cursor:default;background:var(--surface-2);color:var(--on-surface-variant);font-size:12px;font-weight:500;padding:10px 18px;text-transform:uppercase;letter-spacing:.04em}
.trow:not(.thead):hover{background:var(--surface-2)}
.trow .name{font-weight:500;font-size:14px}
.trow .who{display:flex;align-items:center;gap:12px;min-width:0}
.trow .who>div{min-width:0}
.avatar{width:36px;height:36px;border-radius:10px;background:var(--primary-container);color:var(--on-primary-container);display:flex;align-items:center;justify-content:center;flex:none}
.avatar svg{width:18px;height:18px}
.empty{padding:40px 20px;text-align:center}
.trow input[type=checkbox]{width:16px;height:16px;accent-color:var(--primary);cursor:pointer}
.filters{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px}
.filters input[type=text]{flex:1;min-width:180px}
.bulk{display:flex;gap:8px;align-items:center;flex-wrap:wrap;background:var(--primary-container);color:var(--on-primary-container);border-radius:10px;padding:10px 14px;margin-bottom:12px}
.bulk b{font-weight:600;margin-right:auto}
.tbl{width:100%;border-collapse:collapse}
.tbl td,.tbl th{padding:10px 12px;border-top:1px solid var(--outline-variant);text-align:left;vertical-align:top;font-size:13px}
.tbl th{border-top:0;color:var(--on-surface-variant);font-weight:500;font-size:12px;text-transform:uppercase;letter-spacing:.04em}
/* device page: sidebar + content */
.shell{display:grid;grid-template-columns:220px minmax(0,1fr);gap:28px;align-items:start}
.side{position:sticky;top:76px;display:flex;flex-direction:column;gap:2px}
.side button.nav{display:flex;align-items:center;gap:10px;width:100%;text-align:left;background:none;border:0;border-radius:8px;padding:9px 12px;color:var(--on-surface-variant);font:500 14px Inter,system-ui,sans-serif;cursor:pointer}
.side button.nav svg{width:18px;height:18px;flex:none}
.side button.nav:hover{background:var(--surface-3);color:var(--on-surface)}
.side button.nav.on{background:var(--primary-container);color:var(--on-primary-container)}
.side .badge{margin-left:auto;background:var(--primary);color:#fff;border-radius:999px;font-size:11px;padding:1px 7px}
.pills{display:flex;gap:4px;overflow-y:hidden;border-bottom:1px solid var(--outline-variant);margin-bottom:20px;overflow-x:auto}
.pills button{background:none;border:0;border-bottom:2px solid transparent;margin-bottom:-1px;padding:10px 12px;color:var(--on-surface-variant);font:500 14px Inter,system-ui,sans-serif;cursor:pointer;white-space:nowrap}
.pills button:hover{color:var(--on-surface)}
.pills button.on{color:var(--primary);border-bottom-color:var(--primary)}
section.cols{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:16px;align-items:start}
section.cols>.card{margin:0}
.search{position:relative;margin:12px 0}
.search input{width:100%;padding-left:34px}
.search .ico{position:absolute;left:10px;top:11px;pointer-events:none;color:var(--on-surface-variant);width:16px;height:16px}
.search .ico svg{width:16px;height:16px}
.search-results{position:absolute;left:0;right:0;top:100%;z-index:6;background:var(--surface-1);border:1px solid var(--outline);border-radius:10px;margin-top:4px;overflow:hidden;box-shadow:0 8px 24px rgba(16,24,40,.15);min-width:260px}
.search-results div{padding:9px 14px;cursor:pointer;border-top:1px solid var(--outline-variant);font-size:13px}
.search-results div:first-child{border-top:0}
.search-results div:hover{background:var(--surface-3)}
@media (max-width:820px){
  .shell{grid-template-columns:minmax(0,1fr);gap:12px}
  .side{position:static;flex-direction:row;overflow-x:auto;gap:4px;padding-bottom:4px}
  .side button.nav{width:auto;white-space:nowrap}
  .trow{grid-template-columns:28px minmax(0,1fr) auto 20px}
  .trow .c-batt,.trow .c-seen{display:none}
  .trow.thead{display:none}
  .bar .who{display:none}
  main{padding:16px 14px 60px}
  section.cols{grid-template-columns:minmax(0,1fr)}
}
`;

export const loginPage = (error = "") => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#F6F7F9"><title>MDM Login</title>
<link rel="manifest" href="/manifest.webmanifest"><link rel="apple-touch-icon" href="/icon-192.png">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap">
<style>${STYLE}</style></head>
<body><div class="login"><div class="card"><h2 style="font-size:20px;margin-bottom:16px">Sign in to MDM</h2>
<form method="post" action="/login">
<input type="text" name="loginName" placeholder="Client login name (leave empty if you're the administrator)" style="width:100%;margin-bottom:8px" autocapitalize="off" autocorrect="off">
<input type="text" name="code" inputmode="numeric" autocomplete="one-time-code" placeholder="Authenticator code (only if two-step login is on)" style="width:100%;margin-bottom:8px">
<div class="row"><input class="grow" type="password" name="password" placeholder="Password" autofocus required>
<button class="btn">Sign in</button></div>${error ? `<p class="mute" style="color:var(--error)">${error}</p>` : ""}</form></div></div></body></html>`;

export const dashboardPage = () => String.raw`<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#F6F7F9"><title>MDM Dashboard</title>
<link rel="manifest" href="/manifest.webmanifest"><link rel="apple-touch-icon" href="/icon-192.png">
<meta name="mobile-web-app-capable" content="yes"><meta name="apple-mobile-web-app-capable" content="yes">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap">
<script src="https://cdn.jsdelivr.net/npm/qrcode-generator@1.4.4/qrcode.min.js"></script>
<style>${STYLE}</style></head>
<body>
<header class="bar"><div class="top"><a class="brand" href="#devices"><span class="logo"><svg viewBox="0 0 24 24" fill="#fff"><path d="M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z"/></svg></span>MDM</a><div class="crumbs" id="crumbs"></div><span class="who" id="who"></span><a class="out" href="/logout">Sign out</a></div></header>
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
// A device page has 5 groups in a side menu, and each group has a few tabs along the top. Both the
// group and the tab are remembered (also in sessionStorage -- a phone browser backgrounded for a
// minute often throws the tab away and reloads it, which looked like "it goes back by itself").
let subs={};try{subs=JSON.parse(sessionStorage.getItem('subs')||'{}')||{}}catch(e){subs={}}
function saveSubs(){try{sessionStorage.setItem('subs',JSON.stringify(subs))}catch(e){}}
const GROUPS=[
 {key:'overview',label:'Overview',icon:'home',subs:[['status','Status'],['messages','Messages'],['log','Activity log']]},
 {key:'apps',label:'Apps',icon:'apps',subs:[['regular','Apps'],['kiosk','Home screen'],['blocking','Blocked'],['system','System apps'],['browser','Browser']]},
 {key:'internet',label:'Internet',icon:'wifi',subs:[['network','Wi-Fi'],['dns','Website filter'],['traffic','Traffic log']]},
 {key:'security',label:'Security',icon:'shield',subs:[['screen','Screen lock'],['applock','App lock'],['admincode','Admin code'],['restrictions','Restrictions']]},
 {key:'device',label:'Device',icon:'device',subs:[['menu','Settings menu'],['updates','Updates'],['device','Controls']]}];
function getSub(g){const G=GROUPS.find(function(x){return x.key===g});const v=subs[g];return G.subs.some(function(x){return x[0]===v})?v:G.subs[0][0]}
function goTo(g,sub){setDeviceSection(g);if(sub){subs[g]=sub;saveSubs()}render()}
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
 search:'M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z',
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
async function saveConfigFor(d,msg){const r=await call('PUT',(d.isBrowser?'/api/browsers/':'/api/devices/')+d.id+'/config',d.config);snack(msg||'Saved. Phones update within about 5 minutes.');return r}

/* ---------- shell ---------- */
// Everything is sandboxed to its own thing: the top bar only ever says "Devices". Apps and Sites
// live inside each device's (or Browser's) own page, scoped to it. Codes and Settings are the only
// truly account-wide things with nowhere specific to live, so they're reached from a button on the
// bare list instead of a permanent tab.
function setCrumbs(parts){
 const c=document.getElementById('crumbs');c.textContent='';
 parts.forEach(function(p,i){
  if(i)c.append(h('span',{class:'sep'},'/'));
  if(p[1]){const a=h('a',null,p[0]);a.onclick=function(){location.hash=p[1]};c.append(a)}else c.append(h('b',null,p[0]))})}
function pageHead(m,title,sub){m.append(h('div',{class:'pagehead'},h('div',{class:'grow'},h('h1',null,title),sub?h('div',{class:'sub'},sub):null)))}
function render(){
 const m=document.getElementById('main');m.textContent='';
 const who=document.getElementById('who');if(who&&state&&state.session)who.textContent=state.session.role==='admin'?'Administrator':'Client account';
 if(tab==='devices'&&openId){const d=devices.find(function(x){return x.id===openId});if(d){setCrumbs([['Devices','devices'],[d.name]]);renderDeviceDetail(m,d);return}openId=null}
 if(tab==='devices'&&openBrowserId){const b=browsers.find(function(x){return x.id===openBrowserId});if(b){setCrumbs([['Devices','devices'],[b.name]]);renderBrowserDetail(m,b);return}openBrowserId=null}
 if(tab==='devices'&&openWinId){const w=windevices.find(function(x){return x.id===openWinId});if(w){setCrumbs([['Devices','devices'],[w.name]]);renderWinDetail(m,w);return}openWinId=null}
 if(tab==='codes'||tab==='settings'||tab==='clients'||tab==='audit'){
  const T={codes:'Codes',clients:'Clients',settings:'Settings',audit:'Activity'}[tab];
  setCrumbs([['Devices','devices'],[T]]);
  pageHead(m,T);
  if(tab==='codes')renderCodes(m);
  else if(tab==='clients')renderClients(m);
  else if(tab==='audit')renderAudit(m);
  else renderSettings(m);
  return}
 setCrumbs([['Devices']]);
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
 const c=h('div',{style:'margin-top:6px;display:flex;flex-wrap:wrap;gap:6px 0'});
 if(d.syncPaused)c.append(h('span',{class:'chip warn'},'Not connected (paused on the phone)'));
 else c.append(h('span',{class:'chip '+(isOnline(d)?'ok':'bad')},h('span',{class:'dot'}),isOnline(d)?'Online':'Offline'));
 const bat=d.info.battery;
 if(bat)c.append(h('span',{class:'chip '+(bat.pct<=15&&!bat.charging?'bad':'')},svgIcon('battery','currentColor'),bat.pct+'%'+(bat.charging?' · charging':'')));
 const wf=d.info.wifi;
 if(wf)c.append(h('span',{class:'chip'},svgIcon(wf.transport==='wifi'?'wifi':'device','currentColor'),
  wf.transport==='wifi'?(wf.ssid||'Wi-Fi'):wf.transport==='mobile'?('SIM card'+(wf.carrier?' — '+wf.carrier:'')):'No connection'));
 if(d.inflight.some(function(x){return x.type==='lock'})||d.pending)c.append(h('span',{class:'chip warn'},'Command pending'));
 if(lockedNow(d))c.append(h('span',{class:'chip warn'},'Locked until '+new Date(d.info.lock.until).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
 if(browsingFreely(d))c.append(h('span',{class:'chip warn'},'Free browsing until '+new Date(d.info.browseUntil).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
 if(d.info.kiosk)c.append(h('span',{class:'chip ok'},'Home screen mode'));
 if(d.info.deviceOwner===false)c.append(h('span',{class:'chip bad'},'Not device owner!'));
 if(needsUpdate(d))c.append(h('span',{class:'chip warn'},'Update available'));
 const n=pendingCount(d);if(n&&full)c.append(h('span',{class:'chip warn',title:pendingApps(d).map(function(p){return p.l||p.p}).join(', ')},n+' changes pending'));
 return c}
let devSearch='',devFilter='all';
const bulkSel=new Set();
function trow(icon,name,subtitle,statusEl,batt,seen,hash,checkId){
 const r=h('div',{class:'trow'});
 r.onclick=function(){location.hash=hash};
 let box=h('div');
 if(checkId){
  const cb=h('input',{type:'checkbox',title:'Select'});cb.checked=bulkSel.has(checkId);
  cb.onclick=function(e){e.stopPropagation()};
  cb.onchange=function(){cb.checked?bulkSel.add(checkId):bulkSel.delete(checkId);r.dispatchEvent(new CustomEvent('selchange',{bubbles:true}))};
  box=cb}
 r.append(box,h('div',{class:'who'},h('div',{class:'avatar'},svgIcon(icon,'currentColor')),h('div',null,h('div',{class:'name'},name),h('div',{class:'mute small'},subtitle))),
  statusEl,h('div',{class:'c-batt mute'},batt),h('div',{class:'c-seen mute'},seen),h('div',{class:'mute'},'›'));
 return r}
function renderDevices(m){
 const total=devices.length+browsers.length+windevices.length;
 const head=h('div',{class:'pagehead'},h('div',{class:'grow'},h('h1',null,'Devices'),h('div',{class:'sub',id:'devcount'},total+(total===1?' device':' devices'))));
 const bar=h('div',{class:'toolbar'});
 bar.append(btn('Refresh','tonal',load));
 const codes=h('button',{class:'btn'},'Add device');codes.onclick=function(){location.hash='codes'};bar.append(codes);
 if(state.session.role==='admin'){
  const act=h('button',{class:'btn tonal'},'Activity');act.onclick=function(){location.hash='audit'};
  const clients=h('button',{class:'btn tonal'},'Clients');clients.onclick=function(){location.hash='clients'};
  const settings=h('button',{class:'btn tonal'},'Settings');settings.onclick=function(){location.hash='settings'};
  bar.append(act,clients,settings)}
 head.append(bar);m.append(head);
 if(!total){
  m.append(h('div',{class:'card empty'},h('h2',null,'No devices yet'),h('p',{class:'mute'},'Press "Add device" to get a code for a phone or a Browser.')));
  return}

 // search + filter (filters rows in place, so typing never loses its place)
 const q=h('input',{type:'text',placeholder:'Search devices…',value:devSearch});
 const f=h('select');
 for(const o of [['all','All devices'],['online','Online'],['offline','Offline'],['update','Needs an update'],['locked','Locked now']])f.append(new Option(o[1],o[0]));
 f.value=devFilter;
 m.append(h('div',{class:'filters'},q,f));
 const bulk=h('div',{class:'bulk'});bulk.style.display='none';m.append(bulk);

 const rows=[];
 const t=h('div',{class:'table'});
 t.append(h('div',{class:'trow thead'},h('div'),h('div',null,'Device'),h('div',null,'Status'),h('div',null,'Battery'),h('div',null,'Last seen'),h('div')));
 for(const d of devices){
  const st=h('div',null);
  if(d.syncPaused)st.append(h('span',{class:'chip warn'},'Paused'));
  else st.append(h('span',{class:'chip '+(isOnline(d)?'ok':'bad')},h('span',{class:'dot'}),isOnline(d)?'Online':'Offline'));
  if(lockedNow(d))st.append(h('span',{class:'chip warn'},'Locked'));
  if(needsUpdate(d))st.append(h('span',{class:'chip warn'},'Update'));
  const bat=d.info.battery;
  const sub=(state.session.role==='admin'&&d.ownerId)?d.ownerId:'Android phone';
  const row=trow('device',d.name,sub,st,bat?bat.pct+'%'+(bat.charging?' · charging':''):'—',ago(d.lastSeen),'device/'+d.id,d.id);
  rows.push({el:row,text:(d.name+' '+sub).toLowerCase(),phone:d});t.append(row)}
 for(const b of browsers){
  const row=trow('globe','Browser',b.name+' · no MDM on this phone',h('div',null,h('span',{class:'chip'},'Browser')),'—',b.lastSeen?ago(b.lastSeen):'—','browser/'+b.id);
  rows.push({el:row,text:('browser '+b.name).toLowerCase(),phone:null});t.append(row)}
 for(const w of windevices){
  const row=trow('device',w.name,(w.info.hostname||'LockGuard')+' · Windows',h('div',null,h('span',{class:'chip '+(w.enabled?'ok':'')},w.enabled?'Locked down':'Not locked down')),'—',w.lastSeen?ago(w.lastSeen):'—','win/'+w.id);
  rows.push({el:row,text:(w.name+' windows '+(w.info.hostname||'')).toLowerCase(),phone:null});t.append(row)}
 const none=h('div',{class:'empty mute'},'No devices match.');none.style.display='none';
 t.append(none);
 m.append(t);

 const visible=function(r){
  if(devSearch&&!r.text.includes(devSearch.toLowerCase()))return false;
  if(devFilter==='all')return true;
  const d=r.phone;if(!d)return false;
  return devFilter==='online'?isOnline(d)&&!d.syncPaused:devFilter==='offline'?!isOnline(d):devFilter==='update'?needsUpdate(d):devFilter==='locked'?lockedNow(d):true};
 const drawBulk=function(){
  const ids=rows.filter(function(r){return r.phone&&bulkSel.has(r.phone.id)&&visible(r)}).map(function(r){return r.phone.id});
  bulk.textContent='';
  if(!ids.length){bulk.style.display='none';return}
  bulk.style.display='flex';
  bulk.append(h('b',null,ids.length+(ids.length===1?' phone selected':' phones selected')));
  const send=async function(label,type,args,confirmMsg){
   if(confirmMsg&&!confirm(confirmMsg))return;
   let ok=0,bad=0;
   for(const id of ids){try{await call('POST','/api/devices/'+id+'/command',{type:type,args:args||{}});ok++}catch(e){bad++}}
   snack(label+' queued for '+ok+(ok===1?' phone':' phones')+(bad?' ('+bad+' failed)':'')+'. Each runs it at its next check-in.',bad>0);load()};
  bulk.append(btn('Sync now','tonal',function(){return send('Sync','sync')}),
   btn('Lock screen','tonal',function(){return send('Lock','lock',{minutes:0},'Lock the screen on '+ids.length+(ids.length===1?' phone?':' phones?'))}),
   btn('Clear','outline',async function(){bulkSel.clear();for(const c of t.querySelectorAll('input[type=checkbox]'))c.checked=false;drawBulk()}))};
 const apply=function(){
  let n=0;
  for(const r of rows){const v=visible(r);r.el.style.display=v?'':'none';if(v)n++}
  none.style.display=n?'none':'block';
  document.getElementById('devcount').textContent=(n===total?total:n+' of '+total)+(total===1?' device':' devices');\n  drawBulk()};
 q.oninput=function(){devSearch=q.value;apply()};
 f.onchange=function(){devFilter=f.value;apply()};
 t.addEventListener('selchange',drawBulk);
 apply();drawBulk();
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
  :[0,'Reset protection is not set','Add your Google account ID under Device → Controls → Factory Reset Protection.']);
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

function phoneLocalNote(m,text){
 m.append(h('div',{class:'card'},h('div',{class:'mute'},text)))}

/* ---------- Apps hub: Regular Apps / Home Screen Mode / Blocking / System Apps ---------- */
function buildAppsHub(m,dv){
 const appsSub=getSub('apps');
 if(appsSub==='regular'){
  // Same gating as the phone's own Regular Apps section (AdminActivity.buildAppsSection): while
  // Home screen mode is on, app allow/block decisions only come from the Allowed Apps editor in
  // the Home Screen Mode tab -- showing the same editor here too would just be two controls
  // fighting over the same config.apps, with no way to tell here that Home screen mode is why an
  // app looks different than expected.
  if(dv.config.homeScreen){
   const note=h('div',{class:'card'},h('div',{class:'mute'},'Home screen mode is on. Apps can only be allowed or blocked from the Allowed Apps list in the Home screen tab while it\'s on.'));
   note.append(h('div',{style:'margin-top:10px'},btn('Turn off Home screen mode','outline',async function(){
    if(!confirm('Turn off Home screen mode on this phone?'))return;
    dv.config.homeScreen=false;
    for(const pkg in dv.config.apps)if(dv.config.apps[pkg].mode==='soft')delete dv.config.apps[pkg];
    try{await saveConfigFor(dv,'Home screen mode off.')}catch(e){snack(e.message,1)}render()})));
   m.append(note);
  } else {
   renderAppEditor(m,dv);
  }

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
  appBox.append(ar,h('div',{class:'mute small',style:'margin-top:8px'},'Uploads are kept for a week and limited to 24 MB. For bigger apps use a link, or let the person install from the Play Store with approval mode (Device → Controls).'));
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
   h('div',{class:'mute'},'The agent becomes this phone\'s home screen and shows only the apps you set to Allow, with your logo and your custom icons, plus a "Settings" tile that always shows regardless of the Allow list (see Settings Menu below -- it\'s the only way to reach any part of Settings while this is on). An app set to Block is fully switched off, same as always; one left at Default or set to Soft block just has no icon here and stays installed and running in the background. Calls and texts still work. On or off, nothing in between: the master code on the phone (Administrator) can turn it off there too, and that reaches this switch on the next sync, same as every other setting here.')),
   sw(dv.config.homeScreen,async function(on){
    if(on&&!confirm('Turn on Home screen mode on this phone? First make sure the apps the person needs (phone, messages, maps…) are set to Allow on the Apps tab, because only those will appear.')){render();return}
    dv.config.homeScreen=on;
    // Soft block only means anything while this switch is on; off a leftover "soft" app would be
    // stuck invisible in App rules (no button shows it as selected) instead of back at Default.
    if(!on)for(const pkg in dv.config.apps)if(dv.config.apps[pkg].mode==='soft')delete dv.config.apps[pkg];
    try{await saveConfigFor(dv,on?'Home screen mode on. The phone switches within about 5 minutes.':'Home screen mode off.')}catch(e){snack(e.message,1)}render()})));
  hsCard.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Hide the Administrator button'),
   h('div',{class:'mute'},'Home screen mode always shows an "Administrator" button below the allowed apps, to get back in. Hiding it removes that button for everyone who picks up the phone -- dialing *#*#636#*#* on the phone itself, or turning this back off from here, both still work.')),
   sw(dv.config.hideKioskAdmin,async function(on){dv.config.hideKioskAdmin=on;try{await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()})));
  m.append(hsCard);
  const smCard=h('div',{class:'card'},h('h2',null,'Settings Menu'),
   h('div',{class:'mute'},'The disguised "Settings" tile home screen mode always shows, whatever the Allow list says -- the only way to reach any part of Settings while it\'s on. Which categories it shows and where each one links to is set up on the phone itself (most need no setup at all; a few manufacturer-only screens need a one-time "Learn" there). Same place as Device → Settings menu, shown here too since it\'s part of how home screen mode actually works.'));
  smCard.append(h('div',{style:'margin-top:8px'},btn('Open Device → Settings menu','outline',function(){goTo('device','menu')})));
  m.append(smCard);
  m.append(h('div',{class:'card'},h('h2',null,'Allowed Apps'),h('div',{class:'mute'},'Same Default / Allow / Block / Schedule controls as the Apps tab -- set to Allow whatever should appear while Home screen mode is on, before turning it on above.')));
  renderAppEditor(m,dv);
 } else if(appsSub==='blocking'){
  const bCard=h('div',{class:'card'},h('h2',null,'Block Apps/Screens'),
   h('div',{class:'mute'},'Specific screens (exact app components) to bounce away from the instant they open -- set directly on the phone\'s own Administrator screen (Apps → Blocking → Block Apps/Screens). Picking the exact screen needs the phone itself (it offers an autocomplete built from what\'s actually been seen in the foreground there), so this one box stays phone-only.'));
  m.append(bCard);
  renderWholeAppBlocklist(m,dv);
 } else if(appsSub==='browser'){
  renderSites(m,dv);
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
function buildInternetGroup(m,dv){
 const sub=getSub('internet');
 if(sub==='network')renderNetworkSection(m,dv);
 else if(sub==='dns'){renderDnsBlocklist(m,dv);renderDnsAllowlist(m,dv)}
 else if(sub==='traffic')renderTrafficLog(m,dv);
}
function buildDeviceGroup(m,dv){
 const sub=getSub('device');
 if(sub==='menu')phoneLocalNote(m,'Most of the 16 categories here already work with no setup at all -- they point at a standard Android settings screen (Wi-Fi, Bluetooth, sound, display…) that works the same on every phone, or (Android Auto) jump straight into that app itself. A handful are this phone brand\'s own screens with no standard Android way to open them by name (notably some Motorola-only ones), so for just those few, Settings → Settings Menu → Learn on the phone itself captures the exact screen once, by physically going there -- genuinely needs to happen on the phone, there is no remote equivalent for an unlisted manufacturer screen. Everything else needs nothing here.');
 else if(sub==='updates')renderUpdatesSection(m,dv);
 else if(sub==='device')renderDeviceCard(m,dv);
}

function renderDeviceDetail(m,d){
 if(d.id!==lastOpenId){lastOpenId=d.id;try{sessionStorage.setItem('lastOpenId',d.id)}catch(e){}setDeviceSection(null);subs={};saveSubs()}
 const headBox=h('div',{class:'pagehead'},h('div',{class:'avatar'},svgIcon('device','currentColor')),h('div',{class:'grow'},h('h1',null,d.name),chipsFor(d,true)),btn('Refresh','tonal',load));
 m.append(headBox);

 const queue=async function(label,type,args){await call('POST','/api/devices/'+d.id+'/command',{type:type,args:args||{}});snack(label+' queued. The phone runs it at its next check-in (within about 5 minutes).');load()};
 const cmd=function(box,label,type,args,confirmMsg,cls){box.append(btn(label,cls||'tonal',async function(){
   if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}await queue(label,type,a)}))};

 // ----- Overview (status + location + activity; the phone app has no single tile for this, it's
 // the dashboard's own equivalent of AdminActivity's Overview toggle+status card) -----
 const ov=h('section',{class:'cols'});
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
 if(state.session.role==='admin'){
  const ownerCard=h('div',{class:'card'},h('h2',null,'Owner'),
   h('div',{class:'mute'},'Which client account (if any) this phone belongs to -- they see only their own devices and can manage this one from their own login. Admin always sees every device regardless.'));
  const sel=h('select',{style:'width:100%;margin-top:8px'});
  sel.append(h('option',{value:''},'Admin only (no client)'));
  for(const c of clients)sel.append(h('option',{value:c.id},c.name+' ('+c.id+')'));
  sel.value=d.ownerId||'';
  sel.onchange=async function(){
   try{await call('PUT','/api/devices/'+d.id+'/owner',{ownerId:sel.value||null});d.ownerId=sel.value||null;snack('Saved.');load()}catch(e){snack(e.message,1);sel.value=d.ownerId||''}};
  ownerCard.append(sel);
  ov.append(ownerCard);
 }
 ov.append(locCard);
 const acts=h('div',{class:'card'},h('h2',null,'Activity'));
 if(d.pending)acts.append(h('div',{class:'act'},'⏳ '+d.pending+' command(s) waiting for the phone\'s next check-in'));
 for(const c of d.inflight)acts.append(h('div',{class:'act'},'⏳ '+(NAMES[c.type]||c.type)+' — sent '+ago(c.at)+', waiting for the phone to confirm'));
 for(const r of d.results.slice().reverse().slice(0,6).filter(function(r){return r.type!=='listSystemApps'&&r.type!=='locate'&&r.type!=='checkUpdates'}))acts.append(h('div',{class:'act'},(r.ok?'✅ ':'❌ ')+(NAMES[r.type]||r.type)+(r.msg?' — '+r.msg:'')+' · '+ago(r.at)));
 if(acts.children.length===1)acts.append(h('div',{class:'mute'},'No activity yet.'));
 ov.append(acts);

 // ----- Lock (hub: Lock / App lock / Administrator code -- every kind of lock in one place) -----
 const lockSub=getSub('security');
 const lk=h('section');
 if(lockSub==='screen'){
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
  lockBox.append(h('div',{class:'mute small',style:'margin-top:10px'},'Nobody can read a PIN or pattern the person chose themselves, not even the phone\'s maker. What you can do: take it off (Remove screen lock) or replace it (Set PIN), which needs PIN control to be ready (Overview). A PIN you set here is shown on the Overview page. To make sure every lock is one you set, switch on \'Only the administrator can set the screen lock\' in Device → Controls.'));
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
  alBox.append(alRow,h('div',{class:'mute small',style:'margin-top:10px'},'Nobody can read a PIN the person chose themselves, not even the phone\'s maker -- same as the screen lock PIN above. Setting one here replaces whatever was set on the phone itself. Needs an agent build that knows setAppLockPin/clearAppLockPin -- if the phone replies "unknown command", update the agent first (Device → Controls → Update agent) and try again once it has checked in. Applies within about 5 minutes.'));
  lk.append(alBox);
 } else if(lockSub==='restrictions'){
  renderPermissions(lk,d);
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

 // ----- Add-ons (the allowed-sites list, the Browser add-on's own on/off switch, and "make
 // Browser the only browser" -- the phone's standalone-Browser install/open buttons are the only
 // part of its own Add-ons screen that stays phone-local, since "install" is a one-shot action
 // with nothing to mirror as a toggle) -----
 
 // ----- Settings (hub: Settings Menu / Permissions / Network / Updates / Device) -----
 
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


 const internet=h('section');buildInternetGroup(internet,d);
 const devg=h('section');buildDeviceGroup(devg,d);
 const body={overview:{status:ov,messages:msgs,log:lg},apps:ap,internet:internet,security:lk,device:devg};

 // ----- search: every group and tab -----
 const searchIndex=[
  ['Status, location and activity','overview','status'],['Messages from the phone','overview','messages'],['Activity log','overview','log'],
  ['Screen lock: lock, unlock, set PIN','security','screen'],['App lock','security','applock'],['Administrator code (master code)','security','admincode'],
  ['Restrictions (permissions)','security','restrictions'],
  ['Apps: allow, block, schedule','apps','regular'],['Home screen mode','apps','kiosk'],['Hide the Administrator button','apps','kiosk'],
  ['Blocked apps and screens','apps','blocking'],['System apps','apps','system'],['Browser: allowed sites','apps','browser'],
  ['Wi-Fi networks','internet','network'],['Website filter (DNS block and allow lists)','internet','dns'],['Traffic log','internet','traffic'],
  ['Settings menu','device','menu'],['App and system updates','device','updates'],['Device controls: sync, reboot, reset','device','device'],
  ['Turn the phone\'s own Administrator panel on or off','device','device'],['Disable fingerprint, face or biometric unlock','device','device']];
 const sWrap=h('div',{class:'search',style:'margin:0;width:260px;max-width:100%'});
 const sInput=h('input',{type:'text',placeholder:'Search every setting…',value:deviceSearch});
 const sResults=h('div',{class:'search-results'});
 const renderSResults=function(){
  sResults.textContent='';
  if(!deviceSearch){sResults.style.display='none';return}
  const q=deviceSearch.toLowerCase();
  const hits=searchIndex.filter(function(e){return e[0].toLowerCase().includes(q)});
  sResults.style.display='block';
  if(!hits.length){sResults.append(h('div',{class:'mute'},'No settings match.'));return}
  for(const e of hits){const row=h('div',null,e[0]);row.onclick=function(){deviceSearch='';goTo(e[1],e[2])};sResults.append(row)}};
 sInput.oninput=function(){deviceSearch=sInput.value;renderSResults()};
 renderSResults();
 sWrap.append(h('div',{class:'ico'},svgIcon('search','currentColor')),sInput,sResults);
 headBox.insertBefore(sWrap,headBox.lastChild);

 // ----- side menu (5 groups) + tabs along the top of the chosen group -----
 const sec=GROUPS.some(function(g){return g.key===deviceSection})?deviceSection:'overview';
 const G=GROUPS.find(function(g){return g.key===sec});
 const nMsg=(d.messages||[]).length;
 const side=h('nav',{class:'side'});
 for(const g of GROUPS){
  const b=h('button',{class:'nav'+(g.key===sec?' on':'')},svgIcon(g.icon,'currentColor'),g.label);
  if(g.key==='overview'&&nMsg)b.append(h('span',{class:'badge'},String(nMsg)));
  b.onclick=function(){goTo(g.key)};side.append(b)}
 const content=h('div',{class:'content'});
 const cur=getSub(sec);
 const pills=h('div',{class:'pills'});
 for(const sub of G.subs){
  const b=h('button',{class:sub[0]===cur?'on':''},sub[1]+(sec==='overview'&&sub[0]==='messages'&&nMsg?' ('+nMsg+')':''));
  b.onclick=function(){subs[sec]=sub[0];saveSubs();render()};pills.append(b)}
 content.append(pills);
 content.append(typeof body[sec].nodeType==='number'?body[sec]:body[sec][cur]);
 m.append(h('div',{class:'shell'},side,content));
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
// App rules + "Apps on this phone": the one editor for config.apps, shown from Regular Apps while
// Home screen mode is off, and from the Home Screen Mode tab (as "Allowed Apps") regardless of
// on/off -- same shared data, same split AdminActivity itself keeps (its own "Show / Hide Apps"
// vs "Allowed Apps" pickers), just reached from whichever tab actually applies right now.
function renderAppEditor(m,dv){
 renderApps(m,dv);
 const al=h('div',{class:'card'},h('h2',null,'Apps on this phone'),h('div',{class:'mute'},'Every app actually installed here, with the same Default / Allow / Block / Schedule controls as App rules.'));
 const installedSorted=dv.packages.filter(function(a){return a.p!==SELF_PACKAGE}).sort(function(a,b){
  const aa=cur(dv,a.p).mode==='allow'?0:1,ab=cur(dv,b.p).mode==='allow'?0:1;
  return aa!==ab?aa-ab:0});
 for(const a of installedSorted)al.append(appRow({p:a.p,l:a.l,s:a.s,prot:a.protected,hiddenOn:a.h?1:0},dv));
 if(!dv.packages.length)al.append(h('div',{class:'mute'},'The phone has not reported its apps yet.'));
 m.append(al);
}
// Whole-package hides (Android's setApplicationHidden, not the regular per-app Block mode) --
// same list the phone's own Blocking > Block Whole Apps screen edits.
function renderWholeAppBlocklist(m,dv){
 const wab=dv.config.wholeAppBlocklist||{};
 const card=h('div',{class:'card'},h('h2',null,'Block Whole Apps'),
  h('div',{class:'mute'},'Hides an entire app at the Android level -- it can\'t open or run at all, not just one screen inside it. Can hide something the phone or other apps depend on, so pick carefully.'));
 const installed=dv.packages.filter(function(a){return a.p!==SELF_PACKAGE}).sort(function(a,b){return (a.l||a.p).localeCompare(b.l||b.p)});
 const sel=h('select',null,new Option('Pick an installed app…',''));
 for(const a of installed)if(!wab[a.p])sel.append(new Option((a.l||a.p)+' ('+a.p+')',a.p));
 const free=h('input',{type:'text',placeholder:'Or type an exact package name',class:'grow'});
 card.append(h('div',{class:'row',style:'margin-top:8px'},sel,free,btn('Block it','tonal',async function(){
  const pkg=(sel.value||free.value.trim());
  if(!pkg)return;
  if(!confirm('Block "'+pkg+'" entirely? This hides the whole app at the Android level -- it won\'t open or run at all until you Pause or Remove it here.'))return;
  dv.config.wholeAppBlocklist=dv.config.wholeAppBlocklist||{};dv.config.wholeAppBlocklist[pkg]={paused:false};
  try{await saveConfigFor(dv,'Blocked. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}render()})));
 m.append(card);

 const list=h('div',{class:'card'});
 const keys=Object.keys(wab);
 if(!keys.length)list.append(h('div',{class:'mute'},'No whole apps blocked.'));
 for(const pkg of keys.sort()){
  const paused=!!wab[pkg].paused;
  const known=dv.packages.find(function(x){return x.p===pkg});
  const row=h('div',{class:'app'});
  row.append(h('div',{class:'grow'},h('div',{style:'font-weight:500'},(known&&known.l)||pkg),
   h('div',{class:'mute small mono'},pkg+(paused?'  (paused)':''))));
  row.append(h('div',{class:'row'},
   btn(paused?'Resume':'Pause','outline',async function(){dv.config.wholeAppBlocklist[pkg].paused=!paused;try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}render()}),
   btn('Remove','danger',async function(){if(!confirm('Remove the whole-app block on '+pkg+'? This un-hides it.'))return;delete dv.config.wholeAppBlocklist[pkg];try{await saveConfigFor(dv,'Removed.')}catch(e){snack(e.message,1)}render()})));
  list.append(row)}
 m.append(list);
}
// A different thing from both Block Whole Apps above and the Browser's own site allowlist: this
// blocks a hostname for every app on the phone, device-wide, via the agent's own always-on local
// DNS filter (DnsFilterVpnService on the phone).
function renderDnsBlocklist(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'DNS Hostname Block'),
  h('div',{class:'mute'},'Blocks specific hostnames for the whole phone at the network level -- not a per-app block, and not the Browser\'s own site allowlist. One hostname per line; each one also blocks every subdomain of it. Example, to block photos/Street View/video in Google Maps while leaving the map itself working: streetviewpixels-pa.googleapis.com blocks Street View imagery specifically (Maps-only, no side effects elsewhere). lh3.googleusercontent.com, lh4.googleusercontent.com, lh5.googleusercontent.com and lh6.googleusercontent.com block business/user photos -- but those same servers also serve images in Gmail, Drive and Photos, so this affects those too. googlevideo.com blocks in-app video -- but also every YouTube video on the phone, everywhere, not just inside Maps. Turning this on starts the agent\'s own local VPN (always-on, so it can\'t just be disconnected from Settings) -- the phone shows a permanent VPN notification icon the whole time any hostname is listed here. Takes effect within about 5 minutes of saving.'));
 const ta=h('textarea',{style:'width:100%;min-height:120px;font-family:monospace'},(dv.config.dnsBlocklist||[]).join('\n'));
 card.append(h('div',{style:'margin-top:8px'},ta));
 card.append(h('div',{style:'margin-top:8px'},btn('Save','tonal',async function(){
  const typed=ta.value.split('\n').map(function(s){return s.trim()}).filter(Boolean);
  dv.config.dnsBlocklist=typed;
  try{
   const r=await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.');
   const acceptedCount=r&&Array.isArray(r.dnsBlocklist)?r.dnsBlocklist.length:null;
   // A hostname-looking regex silently drops anything that doesn't match -- a "*" wildcard, a
   // "https://" prefix, AdBlock-style "||...^" syntax -- with no error from the save itself, so a
   // whole pasted list rejected line-by-line looked exactly like saving nothing at all. This is
   // the only place that actually says so.
   if(acceptedCount!==null&&acceptedCount<typed.length){
    snack('Only '+acceptedCount+' of '+typed.length+' line(s) looked like plain hostnames and were kept -- the rest were dropped (no "*", "https://", or AdBlock-style syntax; just the bare hostname, one per line).',1)
   }
  }catch(e){snack(e.message,1)}
  render()})));
 m.append(card);
}
// Overrides DNS Hostname Block above for a hostname that matches both -- for when a block entry
// is deliberately broad (a whole CDN domain) but one specific host under it is still needed.
function renderDnsAllowlist(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'DNS Hostname Allow'),
  h('div',{class:'mute'},'Overrides DNS Hostname Block above for whatever\'s listed here -- a hostname (or any subdomain of one) that matches both lists is let through. Use this when a block entry is deliberately broad but one specific host under it is still needed. Same format as the block list: one hostname per line. Takes effect within about 5 minutes of saving.'));
 const ta=h('textarea',{style:'width:100%;min-height:80px;font-family:monospace'},(dv.config.dnsAllowlist||[]).join('\n'));
 card.append(h('div',{style:'margin-top:8px'},ta));
 card.append(h('div',{style:'margin-top:8px'},btn('Save','tonal',async function(){
  const typed=ta.value.split('\n').map(function(s){return s.trim()}).filter(Boolean);
  dv.config.dnsAllowlist=typed;
  try{
   const r=await saveConfigFor(dv,'Saved. The phone applies it within about 5 minutes.');
   const acceptedCount=r&&Array.isArray(r.dnsAllowlist)?r.dnsAllowlist.length:null;
   if(acceptedCount!==null&&acceptedCount<typed.length){
    snack('Only '+acceptedCount+' of '+typed.length+' line(s) looked like plain hostnames and were kept -- the rest were dropped (no "*", "https://", or AdBlock-style syntax; just the bare hostname, one per line).',1)
   }
  }catch(e){snack(e.message,1)}
  render()})));
 m.append(card);
}
// Mirrors the phone's own Administrator panel (Blocking > Traffic Log) -- the phone uploads its
// newest entries on every sync, this just shows what's arrived. Read-only; there's nothing to
// configure here, and Clear is on the phone itself (clearing the phone's local log doesn't touch
// what already made it up here).
function renderTrafficLog(m,dv){
 const log=dv.trafficLog||[];
 const card=h('div',{class:'card'},h('h2',null,'Traffic Log'),
  h('div',{class:'mute'},'Every DNS lookup and every HTTPS connection\'s hostname the DNS filter has seen on the phone, newest first, with the app that made it and whether it was blocked. Uploaded from the phone\'s own Administrator panel (Blocking > Traffic Log) on each sync -- only fills in while the DNS Hostname Block list above is non-empty, since that\'s what keeps the filter running at all.'));
 if(!log.length)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing logged yet.'));
 for(const e of log.slice().reverse()){
  const row=h('div',{class:'act'},(e.blocked?'⛔ ':'• ')+'['+(e.kind==='dns'?'DNS':'TLS')+'] '+(e.host||'(no hostname)')+' · '+ago(e.time));
  row.append(h('div',{class:'mute small',style:'margin-left:20px'},e.app||('uid '+e.uid)));
  card.append(row)}
 m.append(card);
}
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
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Browser add-on'),
  h('div',{class:'mute'},'A lighter Browser built right into the agent app, its own icon, nothing extra installed. Opens only the sites on this same allowlist. The separate standalone Browser app below is untouched either way.')),
  sw(dv.config.browserAddonEnabled,async function(on){dv.config.browserAddonEnabled=on;try{await saveConfigFor(dv,on?'Browser add-on on. Its icon is on the phone now.':'Browser add-on off.')}catch(e){snack(e.message,1)}}))));
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
 m.append(h('div',{class:'pagehead'},h('div',{class:'avatar'},svgIcon('globe','currentColor')),h('div',{class:'grow'},h('h1',null,b.name),h('div',{class:'mute small'},'Connected directly — no MDM on this phone · last check-in '+ago(b.lastSeen))),btn('Refresh','tonal',load)));

 const controls=h('div',{class:'card'},h('h2',null,'This browser'));
 controls.append(h('div',{class:'row',style:'margin-top:8px'},
  btn('Rename','outline',async function(){const name=prompt('Name for this browser',b.name);if(!name)return;await call('PUT','/api/browsers/'+b.id,{name:name});load()}),
  btn('Disconnect','danger',async function(){if(!confirm('Disconnect '+b.name+'? It goes back to needing to connect again, and allows nothing until then.'))return;await call('DELETE','/api/browsers/'+b.id);location.hash='devices'})));
 m.append(controls);

 if(state.session.role==='admin'){
  const ownerCard=h('div',{class:'card'},h('h2',null,'Owner'),
   h('div',{class:'mute'},'Which client account (if any) this browser belongs to -- they see only their own devices and can manage this one from their own login. Admin always sees every device regardless.'));
  const sel=h('select',{style:'width:100%;margin-top:8px'});
  sel.append(h('option',{value:''},'Admin only (no client)'));
  for(const c of clients)sel.append(h('option',{value:c.id},c.name+' ('+c.id+')'));
  sel.value=b.ownerId||'';
  sel.onchange=async function(){
   try{await call('PUT','/api/browsers/'+b.id+'/owner',{ownerId:sel.value||null});b.ownerId=sel.value||null;snack('Saved.');load()}catch(e){snack(e.message,1);sel.value=b.ownerId||''}};
  ownerCard.append(sel);
  m.append(ownerCard);
 }

 renderRequestsCard(m,(b.siteRequests||[]).map(function(r){return{path:'/api/browsers/'+b.id+'/site-requests',deviceName:b.name,url:r.url,at:r.at}}),b);
 renderCloneCard(m,b);
 renderSitesEditor(m,b);
}

function renderWinDetail(m,w){
 m.append(h('div',{class:'pagehead'},h('div',{class:'avatar'},svgIcon('device','currentColor')),h('div',{class:'grow'},h('h1',null,w.name),h('div',{class:'mute small'},(w.info.hostname||'Windows computer')+' · last check-in '+ago(w.lastSeen))),btn('Refresh','tonal',load)));

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

/* ---------- activity: who changed what ---------- */
function renderAudit(m){
 if(state.session.role!=='admin'){m.append(h('div',{class:'card'},h('div',{class:'mute'},'Only the administrator can see this.')));return}
 const card=h('div',{class:'card'},h('h2',null,'Recent activity'),h('div',{class:'mute'},'Sign-ins and every change made from the dashboard, newest first. It records what was done, never PINs or codes.'));
 const box=h('div',{style:'margin-top:12px;overflow-x:auto'},h('div',{class:'mute'},'Loading…'));card.append(box);m.append(card);
 const nameOf=function(t){
  if(!t)return '';
  const list=t.kind==='browsers'?browsers:t.kind==='windevices'?windevices:devices;
  const x=list.find(function(d){return d.id===t.id});return x?x.name:'(removed device)'};
 call('GET','/api/audit').then(function(r){
  box.textContent='';
  if(!r.entries.length){box.append(h('div',{class:'mute'},'Nothing recorded yet.'));return}
  const tb=h('table',{class:'tbl'},h('tr',null,h('th',null,'When'),h('th',null,'Who'),h('th',null,'What'),h('th',null,'Device')));
  for(const e of r.entries.slice(0,150))tb.append(h('tr',null,h('td',{class:'mute',style:'white-space:nowrap'},new Date(e.at).toLocaleString([],{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'})),h('td',null,e.who||''),h('td',null,e.text||''),h('td',null,nameOf(e.target))));
  box.append(tb)}).catch(function(e){box.textContent='';box.append(h('div',{class:'mute'},e.message))})}

/* ---------- settings: two-step login + alerts ---------- */
function renderTwoStep(m){
 const card=h('div',{class:'card'},h('h2',null,'Two-step login'),
  h('div',{class:'mute'},'After your password, ask for a 6-digit code from an authenticator app (Google Authenticator, Authy, 1Password and similar). Strongly recommended: the dashboard can lock and wipe phones. If you ever lose the app there is deliberately no way around it from this page: the owner of the Cloudflare account has to delete the "admin:totp" entry from the STATE storage.'));
 const body=h('div',{style:'margin-top:12px'});card.append(body);m.append(card);
 const draw=async function(){
  body.textContent='';
  let st;try{st=await call('GET','/api/2fa')}catch(e){body.append(h('div',{class:'mute'},e.message));return}
  if(st.enabled){
   body.append(h('div',null,h('span',{class:'chip ok'},'On')));
   const code=h('input',{type:'text',placeholder:'Current 6-digit code',inputmode:'numeric',maxlength:7,style:'margin-top:10px;width:200px'});
   body.append(code,h('div',{style:'margin-top:8px'},btn('Turn off','danger',async function(){
    await call('POST','/api/2fa/disable',{code:code.value});snack('Two-step login is off.');draw()})))}
  else{
   body.append(h('div',null,h('span',{class:'chip'},'Off')),h('div',{style:'margin-top:10px'},btn('Set up…','',async function(){
    const r=await call('POST','/api/2fa/start',{});
    body.textContent='';
    const qr=qrcode(0,'M');qr.addData(r.uri);qr.make();
    body.append(h('div',{class:'mute'},'1. In your authenticator app, add an account by scanning this code (or typing the key).'),
     h('img',{src:qr.createDataURL(5,8),alt:'Two-step login QR code',style:'background:#fff;padding:4px;border-radius:10px;display:block;margin:10px 0'}),
     h('div',{class:'mute small'},'Key: '),h('div',{class:'mono',style:'word-break:break-all;margin-bottom:10px'},r.secret),
     h('div',{class:'mute'},'2. Type the 6-digit code it shows to turn two-step login on.'));
    const code=h('input',{type:'text',placeholder:'6-digit code',inputmode:'numeric',maxlength:7,style:'margin-top:8px;width:200px'});
    body.append(code,h('div',{style:'margin-top:8px'},btn('Turn on','',async function(){
     await call('POST','/api/2fa/enable',{code:code.value});snack('Two-step login is on. You will be asked for a code next time you sign in.');draw()})))})))}};
 draw()}
function renderAlerts(m){
 const card=h('div',{class:'card'},h('h2',null,'Alerts'),
  h('div',{class:'mute'},'Get a message on your phone when something needs attention. Paste the address of a notification service. Easiest: install the free ntfy app, pick a private topic name, and use https://ntfy.sh/your-topic-name . Discord, Slack and Teams webhook addresses also work. Nothing is sent until you add one.'));
 const body=h('div',{style:'margin-top:12px'},h('div',{class:'mute'},'Loading…'));card.append(body);m.append(card);
 call('GET','/api/alerts').then(function(a){
  body.textContent='';
  const save=async function(patch,msg){try{a=await call('PUT','/api/alerts',patch);snack(msg||'Saved.')}catch(e){snack(e.message,1)}};
  const url=h('input',{type:'text',placeholder:a.urlSet?'Saved ('+a.urlHint+'). Paste a new address to replace it':'https://ntfy.sh/your-private-topic',style:'width:100%'});
  body.append(url,h('div',{class:'row',style:'margin-top:8px'},
   btn('Save address','',async function(){if(!url.value.trim()){snack('Paste an address first.',1);return}await save({url:url.value.trim()},'Address saved.');url.value='';render()}),
   btn('Send a test','tonal',async function(){try{await call('POST','/api/alerts/test',{});snack('Test sent. Check your phone.')}catch(e){snack(e.message,1)}}),
   a.urlSet?btn('Remove address','outline',async function(){await save({url:''},'Address removed.');render()}):null));
  const opt=function(key,label,extra){
   const row=h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},label)),extra||null,sw(a[key],function(on){const p={};p[key]=on;save(p)}));
   body.append(row)};
  const hours=h('select');for(const n of [1,2,3,6,12,24])hours.append(new Option(n+(n===1?' hour':' hours'),n));hours.value=String(a.offlineHours);
  hours.onchange=function(){save({offlineHours:parseInt(hours.value,10)})};
  const pct=h('select');for(const n of [10,15,20,30])pct.append(new Option(n+'%',n));pct.value=String(a.batteryPct);
  pct.onchange=function(){save({batteryPct:parseInt(pct.value,10)})};
  opt('offline','A phone has not checked in for',hours);
  opt('battery','A phone battery is at or below',pct);
  opt('requests','A phone asks to open a site or sends you a message');
  opt('tamper','The Agent notices something security related on a phone');
 }).catch(function(e){body.textContent='';body.append(h('div',{class:'mute'},e.message))})}

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
 if(state.session.role==='admin'){renderTwoStep(m);renderAlerts(m)}
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
  h('div',{class:'mute'},'Blocking Developer options also turns off USB debugging, so adb stops working. If you need to get back in, send "Release device" from Device → Controls (or use recovery mode).')));
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
  h('div',{class:'mute'},'The phone checks GitHub for a newer build every 6 hours and installs it itself. You can always update it yourself from Device → Controls, or from the phone\'s admin panel.')),
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
 const notif=h('div',{class:'card'},h('h2',null,'Notification text'),
  h('div',{class:'mute'},'The DNS filter\'s notification (required by Android for any running foreground service -- it can\'t be hidden entirely). Can also be changed right on the phone (Settings → Device); whichever was changed more recently wins.'));
 const notifTitle=h('input',{type:'text',placeholder:'Title',value:dv.config.dnsFilterNotifTitle||'',style:'width:100%;margin-top:8px'});
 const notifText=h('input',{type:'text',placeholder:'Text',value:dv.config.dnsFilterNotifText||'',style:'width:100%;margin-top:8px'});
 notif.append(notifTitle,notifText,h('div',{class:'row',style:'margin-top:8px'},
  btn('Save','',async function(){
   const t=notifTitle.value.trim(),x=notifText.value.trim();
   if(!t||!x){snack('Fill in both fields.',1);return}
   dv.config.dnsFilterNotifTitle=t;dv.config.dnsFilterNotifText=x;
   try{await saveConfigFor(dv,'Saved. The phone applies it within about a minute.')}catch(e){snack(e.message,1)}render()})));
 m.append(notif);
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
