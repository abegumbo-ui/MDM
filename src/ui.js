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
@media (max-width:640px){
.setting{flex-wrap:wrap;gap:10px}
.setting>.grow{flex:1 1 100%;min-width:0}
.setting>select,.setting>input[type=text],.setting>input[type=time],.setting>textarea{flex:1 1 100%;width:100%;max-width:100%}
.card{padding:14px}
select{max-width:100%}
}
.seg{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.seg button{border:1px solid var(--outline-variant);background:transparent;color:inherit;border-radius:999px;padding:8px 14px;font:inherit;cursor:pointer}.seg button.on{background:var(--primary);border-color:var(--primary);color:var(--on-primary,#fff)}
.setting .grow>.mute,.card>.mute:first-of-type{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer}
.setting .grow>.mute.open,.card>.mute:first-of-type.open{display:block;-webkit-line-clamp:unset;overflow:visible}
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
.usage{display:grid;grid-template-columns:minmax(90px,180px) minmax(60px,1fr) auto;gap:8px 12px;align-items:center;margin-top:12px}
.usage .nm{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.usage .track{height:8px;position:relative}
.usage .bar{height:8px;min-width:2px;background:var(--primary);border-radius:0 4px 4px 0}
.usage .val{font-variant-numeric:tabular-nums;color:var(--on-surface-variant);font-size:13px;text-align:right;white-space:nowrap}
.hero{font-size:34px;font-weight:600;letter-spacing:-.02em;line-height:1.1;margin-top:8px}
.meter{height:8px;background:var(--surface-3);border-radius:4px;overflow:hidden;margin-top:10px;max-width:420px}
.meter i{display:block;height:100%;background:var(--primary)}
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
 {key:'safety',label:'Safety',icon:'message',subs:[['health','Health check'],['msgalerts','Message alerts'],['screenshots','Screenshots']]},
 {key:'apps',label:'Apps',icon:'apps',subs:[['regular','Apps'],['addons','Add-ons'],['kiosk','Home screen'],['blocking','Blocked'],['system','System apps'],['store','Play Store']]},
 {key:'time',label:'Screen time',icon:'clock',subs:[['report','Usage'],['limits','Limits']]},
 {key:'browser',label:'Browser',icon:'globe',subs:[['summary','At a glance'],['access','Filter level'],['youtube','YouTube'],['history','Sites visited'],['sites','Allowed sites'],['apps','Browser apps'],['inspect','Inspection (advanced)']]},
 {key:'calls',label:'Calls & texts',icon:'phone',subs:[['rules','Who can call'],['contacts','Contacts'],['calllog','Call log'],['texts','Texts']]},
{key:'internet',label:'Internet',icon:'wifi',subs:[['network','Wi-Fi'],['dns','Website filter'],['appnet','Internet for apps'],['traffic','Traffic log']]},
 {key:'security',label:'Security',icon:'shield',subs:[['screen','Screen lock'],['applock','App lock'],['admincode','Admin code'],['restrictions','Restrictions']]},
 {key:'device',label:'Device',icon:'device',subs:[['location','Location'],['usermenu','Phone menu'],['menu','Settings menu'],['updates','Updates'],['device','Controls']]}];
function getSub(g){const G=GROUPS.find(function(x){return x.key===g});const v=subs[g];return G.subs.some(function(x){return x[0]===v})?v:G.subs[0][0]}
function goTo(g,sub){setDeviceSection(g);if(sub){subs[g]=sub;saveSubs()}render()}
let state=null,devices=[],browsers=[],windevices=[],clients=[],latest=null,tab='devices',openId=null,openBrowserId=null,openWinId=null,search='',sysAppSearch='';
function route(){const x=(location.hash||'#devices').slice(1);
 if(x.indexOf('device/')===0){tab='devices';openId=x.slice(7);openBrowserId=null;openWinId=null}
 else if(x.indexOf('browser/')===0){tab='devices';openBrowserId=x.slice(8);openId=null;openWinId=null}
 else if(x.indexOf('win/')===0){tab='devices';openWinId=x.slice(4);openId=null;openBrowserId=null}
 else{tab=x||'devices';openId=null;openBrowserId=null;openWinId=null}}
route();
// Explanations show two lines; tapping one shows the rest (and tapping again folds it).
document.addEventListener('click',function(e){const t=e.target&&e.target.closest?e.target.closest('.setting .grow>.mute,.card>.mute'):null;if(t&&!e.target.closest('a,button,input,select,textarea'))t.classList.toggle('open')});
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
 phone:'M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z',
 shield:'M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z',
 wifi:'M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3C13.35,15.34 10.66,15.34 9,17zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z',
 update:'M19,13c0,3.87 -3.13,7 -7,7 -3.87,0 -7,-3.13 -7,-7 0,-3.83 3.08,-6.94 6.9,-7l0,2.02c-2.7,0.07 -4.9,2.3 -4.9,4.98 0,2.76 2.24,5 5,5 2.76,0 5,-2.24 5,-5 0,-1.27 -0.49,-2.42 -1.27,-3.3l-1.48,1.48 0,-5.5 5.5,0 -1.69,1.69c1.17,1.28 1.94,2.96 1.94,4.63z',
 doc:'M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2L20,8l-6,-6zM13,9L13,3.5L18.5,9L13,9z',
 clock:'M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z',
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
async function saveConfigFor(d,msg){
 if(d.isWindows){const r=await call('PUT','/api/windevices/'+d.id+'/config',{config:d.config,filterOn:d.filterOn,win:d.win});snack(msg||'Saved. The computer applies it within a minute.');return r}
 const r=await call('PUT',(d.isBrowser?'/api/browsers/':'/api/devices/')+d.id+'/config',d.config);snack(msg||'Saved. Phones update within about 5 minutes.');return r}

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
 if(!state)return; // still loading: load() draws the page once the data is in
 const m=document.getElementById('main');m.textContent='';
 const who=document.getElementById('who');if(who&&state&&state.session)who.textContent=state.session.helper?state.session.helper+(state.session.viewOnly?' (view only)':''):state.session.role==='admin'?'Administrator':'Client account';
 if(tab==='devices'&&openId){const d=devices.find(function(x){return x.id===openId});if(d){setCrumbs([['Devices','devices'],[d.name]]);renderDeviceDetail(m,d);return}openId=null}
 if(tab==='devices'&&openBrowserId){const b=browsers.find(function(x){return x.id===openBrowserId});if(b){setCrumbs([['Devices','devices'],[b.name]]);renderBrowserDetail(m,b);return}openBrowserId=null}
 if(tab==='devices'&&openWinId){const w=windevices.find(function(x){return x.id===openWinId});if(w){setCrumbs([['Devices','devices'],[w.name]]);renderWinDetail(m,w);return}openWinId=null}
 if(tab==='codes'||tab==='settings'||tab==='clients'||tab==='audit'||tab==='templates'){
  const T={codes:'Add a device',clients:'Clients',settings:'Settings',audit:'Activity',templates:'Templates'}[tab];
  setCrumbs([['Devices','devices'],[T]]);
  pageHead(m,T);
  if(tab==='codes')renderCodes(m);
  else if(tab==='clients')renderClients(m);
  else if(tab==='audit')renderAudit(m);
  else if(tab==='templates')renderTemplates(m);
  else renderSettings(m);
  return}
 setCrumbs([['Devices']]);
 renderDevices(m)}
window.addEventListener('hashchange',function(){route();window.scrollTo(0,0);render()});

/* ---------- devices ---------- */
const NAMES={addTime:'Extra screen time',openStore:'Open Play Store',closeStore:'Close Play Store',lock:'Lock screen',reboot:'Reboot',wipe:'Wipe',release:'Release device',install:'Install APK',uninstall:'Uninstall app',sync:'Sync','install-result':'Install result','code:install':'Install code used','code:uninstall':'Removal code used',setPin:'Set screen PIN',clearPin:'Remove screen lock',unlock:'Unlock',addWifi:'Add Wi-Fi',resetAppCode:'Reset app code',updateAgent:'Update agent','uninstall-result':'Uninstall result',listSystemApps:'Scan for hidden system apps',locate:'Find location',checkUpdates:'Check for app updates',updateApp:'Update app',screenshot:'Screenshot'};
const ICON={hide:'🙈',show:'👁️',app:'📦',error:'⚠️',command:'▶️',restriction:'🔒',local:'🔑',security:'🛡️',update:'⬆️',lock:'🔒',watchdog:'👀'};
function isOnline(d){return d.lastSeen&&Date.now()-d.lastSeen<12*60000}
function lockedNow(d){const lk=d.info.lock;return lk&&lk.until>Date.now()}
function storeOpen(d){const u=d.info&&d.info.storeUntil;return u===-1||(u>0&&u>Date.now())}
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
 if(storeOpen(d))c.append(h('span',{class:'chip warn'},'Play Store open'+(d.info.storeUntil>0?' until '+new Date(d.info.storeUntil).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'}):'')));
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
  const tp=h('button',{class:'btn tonal'},'Templates');tp.onclick=function(){location.hash='templates'};bar.append(tp);
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
  const row=trow('device',w.name,(w.info.hostname||'LockGuard')+' · Windows',h('div',null,h('span',{class:'chip '+(w.filterOn||w.enabled?'ok':'')},w.filterOn?'Filter on':w.enabled?'Locked down':'No filter')),'—',w.lastSeen?ago(w.lastSeen):'—','win/'+w.id);
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
  document.getElementById('devcount').textContent=(n===total?total:n+' of '+total)+(total===1?' device':' devices');
  drawBulk()};
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
 if(appsSub==='regular'){renderWhatsApp(m,dv);
  renderAppRequests(m,dv);
  renderAppGroups(m,dv);
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
 } else if(appsSub==='addons'){renderAddons(m,dv);
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
  const bCard=h('div',{class:'card'},h('h2',null,'Whole screens of apps'),
   h('div',{class:'mute'},'A single screen of an app, closed the moment it opens. Pick it on the phone: Administrator → Apps → Blocked → Tap to block → Start, open the app and the screen, press ✓. They are listed and removed on the phone.'));
  m.append(bCard);
  renderBlockedScreens(m,dv);
  renderBlockedParts(m,dv);
  renderWholeAppBlocklist(m,dv);
 } else if(appsSub==='store'){
  renderAppRequests(m,dv);
  renderStore(m,dv);
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
 else if(sub==='appnet'){renderAppFilterModes(m,dv);renderInternetApps(m,dv)}
}
function buildDeviceGroup(m,dv){
 const sub=getSub('device');
 if(sub==='menu')phoneLocalNote(m,'Most of the 16 categories here already work with no setup at all -- they point at a standard Android settings screen (Wi-Fi, Bluetooth, sound, display…) that works the same on every phone, or (Android Auto) jump straight into that app itself. A handful are this phone brand\'s own screens with no standard Android way to open them by name (notably some Motorola-only ones), so for just those few, Settings → Settings Menu → Learn on the phone itself captures the exact screen once, by physically going there -- genuinely needs to happen on the phone, there is no remote equivalent for an unlisted manufacturer screen. Everything else needs nothing here.');
 else if(sub==='usermenu')renderUserMenu(m,dv);
 else if(sub==='location')renderLocation(m,dv);
 else if(sub==='updates')renderUpdatesSection(m,dv);
 else if(sub==='device'){renderTemplateCard(m,dv);renderDeviceCard(m,dv)}
}

function renderDeviceDetail(m,d){
 if(d.id!==lastOpenId){lastOpenId=d.id;try{sessionStorage.setItem('lastOpenId',d.id)}catch(e){}setDeviceSection(null);subs={};saveSubs()}
 const headBox=h('div',{class:'pagehead'},h('div',{class:'avatar'},svgIcon('device','currentColor')),h('div',{class:'grow'},h('h1',null,d.name),chipsFor(d,true)),btn('Refresh','tonal',load));
 m.append(headBox);
 if(state.session&&state.session.viewOnly)m.append(h('div',{class:'card',style:'border-color:var(--warn)'},h('div',{class:'mute'},'This login can only look. Changes have to be made from a full login.')));

 const queue=async function(label,type,args){await call('POST','/api/devices/'+d.id+'/command',{type:type,args:args||{}});snack(label+' queued. The phone runs it at its next check-in (within about 5 minutes).');load()};
 const cmd=function(box,label,type,args,confirmMsg,cls){box.append(btn(label,cls||'tonal',async function(){
   if(confirmMsg&&!confirm(confirmMsg))return;let a=args;if(typeof args==='function'){a=args();if(!a)return}await queue(label,type,a)}))};

 // ----- Overview (status + location + activity; the phone app has no single tile for this, it's
 // the dashboard's own equivalent of AdminActivity's Overview toggle+status card) -----
 const ov=h('section',{class:'cols'});
 if(d.syncPaused)ov.append(h('div',{class:'card',style:'border-color:var(--warn)'},h('h2',null,'Not connected'),h('div',{class:'mute'},'This device is not connected to the dashboard because it was turned off (from this phone\'s own Admin screen, to save battery). It keeps enforcing whatever it had last. It reconnects only when someone turns it back on there — nothing here can reach it in the meantime.')));
 renderSos(ov,d);
 renderHealthSummary(ov,d);
 renderWhatsOn(ov,d);
 renderAppRequests(ov,d);
 renderPause(ov,d);
 renderInternetOff(ov,d);
 renderLevels(ov,d);
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
  h('div',{class:'mute'},'Finds the phone once, right now. For a history of where it has been, and alerts for places like home or school, see Device → Location.'));
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
 const callsg=h('section');buildCallsGroup(callsg,d);
 const brg=h('section');buildBrowserGroup(brg,d);
 const tmg=h('section');buildTimeGroup(tmg,d);
 const devg=h('section');buildDeviceGroup(devg,d);
 const safety={health:h('section'),msgalerts:h('section'),screenshots:h('section')};
 renderHealth(safety.health,d);renderMsgAlerts(safety.msgalerts,d);renderNudityAlerts(safety.msgalerts,d);renderScreenshots(safety.screenshots,d);
 const body={overview:{status:ov,messages:msgs,log:lg},safety:safety,apps:ap,time:tmg,browser:brg,calls:callsg,internet:internet,security:lk,device:devg};

 // ----- search: every group and tab -----
 const searchIndex=[
  ['Status, location and activity','overview','status'],['Health check: is everything working on the phone?','safety','health'],['Message alerts: worrying words in chats (WhatsApp, Instagram, texts...)','safety','msgalerts'],['Screenshot: see what is on the phone\'s screen','safety','screenshots'],['Ready-made setups: young child, teen, kosher phone','overview','status'],['Messages from the phone','overview','messages'],['Activity log','overview','log'],
  ['Screen lock: lock, unlock, set PIN','security','screen'],['App lock','security','applock'],['Administrator code (master code)','security','admincode'],
  ['Restrictions (permissions)','security','restrictions'],
  ['Apps: allow, block, schedule','apps','regular'],['Home screen mode','apps','kiosk'],['Hide the Administrator button','apps','kiosk'],
  ['Blocked apps and screens','apps','blocking'],['Tap to block: block a button, tab or feed inside an app (Shorts, Reels...)','apps','blocking'],['Block YouTube Shorts','apps','blocking'],['Block Instagram Reels','apps','blocking'],['System apps','apps','system'],['Browser at a glance: what this phone can open','browser','summary'],['Browser filter level: Moderate, Strict, YouTube','browser','access'],['Safe search: force SafeSearch on Google, Bing, DuckDuckGo','browser','access'],['Blocked words in searches and site names','browser','access'],['Block image search (Google Images and others)','browser','access'],['Protection level: Strictest, Recommended, Light','overview','status'],['Pause web filtering for a while','overview','status'],['Turn the internet off for a while','overview','status'],['Different screen time limit on weekends','time','limits'],['SOS: the phone asked for help','overview','status'],['Internet only for chosen apps','internet','appnet'],['Per app: filtered, not filtered, or no internet','internet','appnet'],['Sites visited (browsing history)','browser','history'],['Searches typed on the phone (search history)','browser','history'],['Web pages inside apps (links in Instagram, Gmail...)','browser','apps'],['Filter schedule: a different level at set times','browser','access'],['Weekly report','settings',null],['Who can call the phone, and who it can call','calls','rules'],['Allowed phone numbers','calls','rules'],['Call log','calls','calllog'],['Contacts on the phone','calls','contacts'],['WhatsApp Status and Channels','apps','regular'],['Other parents: more logins','settings',null],['Texts (SMS) log','calls','texts'],['Location history and places (arrive and leave alerts)','device','location'],['Templates: copy settings to other phones','device','device'],['Alerts: SIM card changed, phone restarted','device','device'],['Block pictures (images) on web pages','browser','access'],['YouTube Restricted Mode in the YouTube app','browser','access'],['Browser: allowed sites and site requests','browser','sites'],['Browser apps: built-in browser, only browser','browser','apps'],['Chrome: no incognito, adult-site filter, no sign-in','browser','apps'],['Block all games','apps','regular'],['Block video and streaming apps','apps','regular'],['Block dating apps','apps','regular'],['Block shopping apps','apps','regular'],['Play Store: block, open for a while, codes','apps','store'],['Screen time today and this week (usage report)','time','report'],['Daily screen time limit','time','limits'],['Bedtime','time','limits'],['School time: only the basics during school hours','time','limits'],['Time limit for one app','time','limits'],['Give extra time','time','report'],['Block all social media apps','apps','regular'],['Phone menu: which buttons he sees','device','usermenu'],
  ['Wi-Fi networks','internet','network'],['Website filter (DNS block and allow lists)','internet','dns'],['Traffic log','internet','traffic'],['Browser inspection (certificate): filter inside Chrome and other browsers','browser','inspect'],
  ['Settings menu','device','menu'],['App and system updates','device','updates'],['Device controls: sync, reboot, reset','device','device'],
  ['Turn the phone\'s own Administrator panel on or off','device','device'],['Disable fingerprint, face or biometric unlock','device','device'],
  ['SafeSearch on or off','browser','access'],['Filter level: Approved sites only, Moderate, Strict (every browser)','browser','access'],['Block ads','browser','access'],['Sites that refuse ad blocking (ads covered in black)','browser','access'],
  ['Pictures: hide nudity, cover skin (men, women), no pictures on some sites','browser','access'],['Blocked words','browser','access'],['Block image search','browser','access'],['Filter schedule','browser','access'],['YouTube Restricted Mode everywhere','browser','access'],
  ['YouTube: Only what I allow, Restricted, Open, Off','browser','youtube'],['Find a YouTube channel or video and allow it','browser','youtube'],['Let them search YouTube and ask for videos','browser','youtube'],['Allow signing in to YouTube','browser','youtube'],['Blocked screens: remove a screen that was blocked on the phone','apps','blocking'],['YouTube app on the phone (YouTube icon), message when YouTube is off','browser','youtube'],['Allowed YouTube channels and videos','browser','youtube'],
  ['Allowed sites, site requests, allow for a while (1 hour, 2 hours, custom)','browser','sites'],['Add-ons: put in or take out YouTube, the Browser add-on, the Browser app','apps','addons'],['YouTube icon on the phone','apps','addons'],['Uninstall an app','apps','regular'],['Browser add-on, make this the only browser, Chrome settings (no incognito)','browser','apps'],['Sites visited, searches list','browser','history'],['Release device, remove management','device','device'],['Check in every 15 seconds while the dashboard is open','device','device']];
 const sWrap=h('div',{class:'search',style:'margin:0;flex:1 1 320px;min-width:220px;max-width:100%'});
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
 const nMsg=(d.messages||[]).length,nApp=(d.appRequests||[]).length,nSite=(d.siteRequests||[]).length;
 const side=h('nav',{class:'side'});
 for(const g of GROUPS){
  const b=h('button',{class:'nav'+(g.key===sec?' on':'')},svgIcon(g.icon,'currentColor'),g.label);
  const nBadge=g.key==='overview'?nMsg+nApp:g.key==='browser'?nSite:g.key==='safety'?healthProblems(d).filter(function(x){return x.level==='bad'}).length+(d.msgAlerts||[]).length:0;
  if(nBadge)b.append(h('span',{class:'badge'},String(nBadge)));
  b.onclick=function(){goTo(g.key)};side.append(b)}
 const content=h('div',{class:'content'});
 const cur=getSub(sec);
 const pills=h('div',{class:'pills'});
 for(const sub of G.subs){
  const b=h('button',{class:sub[0]===cur?'on':''},sub[1]+(sec==='overview'&&sub[0]==='messages'&&nMsg?' ('+nMsg+')':''));
  b.onclick=function(){subs[sec]=sub[0];saveSubs();render()};pills.append(b)}
 content.append(pills);
 if(GROUP_HELP[sec])content.append(h('div',{class:'mute small',style:'margin:-4px 0 12px'},GROUP_HELP[sec]));
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
  snack('Uninstall queued. The phone does it at its next check-in (within a minute).');load()}));
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
function ytHostOf(u){try{return new URL(u.includes('://')?u:'https://'+u).hostname.toLowerCase().replace(/^www\./,'')}catch(e){return null}}
function isYoutubeUrl(u){const h=ytHostOf(u);return h==='youtube.com'||h==='m.youtube.com'||h==='music.youtube.com'||h==='youtu.be'||h==='youtube-nocookie.com'}
function ytVideoIdOf(u){try{const x=new URL(u.includes('://')?u:'https://'+u);const h=x.hostname.toLowerCase().replace(/^www\./,'');let id=null;if(h==='youtu.be')id=x.pathname.split('/')[1]||null;else if(h==='youtube.com'||h==='m.youtube.com'||h==='music.youtube.com'||h==='youtube-nocookie.com'){if(x.pathname==='/watch')id=x.searchParams.get('v');else{const m=/^\/(?:shorts|embed|live|v)\/([^/?#]+)/.exec(x.pathname);if(m)id=m[1]}}return id&&/^[A-Za-z0-9_-]{11}$/.test(id)?id:null}catch(e){return null}}
function ytChannelOf(u){try{const x=new URL(u.includes('://')?u:'https://'+u);const h=x.hostname.toLowerCase().replace(/^www\./,'');if(h!=='youtube.com'&&h!=='m.youtube.com')return null;const m=/^\/(@[A-Za-z0-9._-]{3,30})(?:\/|$)/.exec(x.pathname);return m?m[1].toLowerCase():null}catch(e){return null}}
function hostOfUrl(u){try{return new URL(u.includes('://')?u:'https://'+u).hostname.replace(/^www\./,'')}catch(e){return u}}
function renderRequestsCard(m,reqs,dv){
 if(!reqs.length)return;
 const rc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Site requests ('+reqs.length+')'),
  h('div',{class:'mute'},'Pages tried on this device that weren\'t on its allowlist.'),allowForPicker());
  for(const r of reqs){
   const dismiss=async function(){await call('DELETE',r.path+'?url='+encodeURIComponent(r.url));load()};
   const isVideo=!!ytVideoIdOf(r.url),ytCh=ytChannelOf(r.url),ytFind=(/^https:\/\/(www\.|m\.)?youtube\.com\/results\?search_query=([^&]*)/.exec(r.url)||[])[2];
   const detail=h('div',{class:'mute small'},isVideo?'Looking up the video…':'');
   const titleEl=h('div',{style:'font-weight:500;word-break:break-all'},ytFind!==undefined?'YouTube: "'+decodeURIComponent(ytFind.replace(/\+/g,' '))+'"':ytCh?'YouTube channel '+ytCh:r.url);
   let info=null;
   const buttons=h('div',{class:'row',style:'margin-top:8px'});
   if(ytFind!==undefined){
    buttons.append(btn('Find it','',function(){findOnYoutube(dv,decodeURIComponent(ytFind.replace(/\+/g,' ')))}),btn('Dismiss','outline',dismiss));
    rc.append(h('div',{class:'app'},h('div',{class:'grow'},titleEl,r.note?h('div',{style:'margin-top:4px'},'“'+r.note+'”'):null,h('div',{class:'mute small'},r.deviceName+' · '+ago(r.at)),buttons)));
    continue}
   if(ytCh){
    buttons.append(btn('Allow this channel','',async function(){await addSite(dv,{type:'youtube',url:'https://www.youtube.com/'+ytCh,id:ytCh,label:ytCh});await dismiss()}),btn('Dismiss','outline',dismiss));
    rc.append(h('div',{class:'app'},h('div',{class:'grow'},titleEl,r.note?h('div',{style:'margin-top:4px'},'“'+r.note+'”'):null,h('div',{class:'mute small'},r.deviceName+' · '+ago(r.at)),buttons)));
    continue}
   buttons.append(btn(isVideo?'Allow this video':'Allow this page only','',async function(){
     const label=info&&info.title?info.title+(info.author?' — '+info.author:''):hostOfUrl(r.url);
     await addSite(dv,{type:'exact',url:r.url,label:label});await dismiss()}));
   if(!isVideo||!isYoutubeUrl(r.url)){
    buttons.append(btn('Allow whole site ('+hostOfUrl(r.url)+')','',async function(){
     await addSite(dv,{type:'domain',url:hostOfUrl(r.url),label:hostOfUrl(r.url)});await dismiss()}));
   }
   buttons.append(btn('Dismiss','outline',dismiss));
   rc.append(h('div',{class:'app'},h('div',{class:'grow'},titleEl,detail,r.note?h('div',{style:'margin-top:4px'},'“'+r.note+'”'):null,
    h('div',{class:'mute small'},r.deviceName+' · '+ago(r.at)),buttons)));
   if(isVideo){
    call('GET','/api/youtube-info?url='+encodeURIComponent(r.url)).then(function(i){
     info=i;
     if(i.title){titleEl.textContent=i.title;detail.textContent=(i.author?'Channel: '+i.author:'')+' · '+r.url}
     else detail.textContent=i.error||'';
     if(i.handle){
      const all=btn('Allow all videos from '+(i.author||i.handle),'tonal',async function(){
       await addSite(dv,{type:'youtube',url:'https://www.youtube.com/'+i.handle,id:i.handle,label:'YouTube channel '+i.handle+(i.author?' — '+i.author:'')});await dismiss()});
      buttons.insertBefore(all,buttons.children[1]||null)}
    }).catch(function(){detail.textContent=''})}
  }
 m.append(rc)}
/** How much of the web this browser may open: only the allowlist (default), or the open web minus blocked categories. */
/** Pictures off on chosen sites: the page works, only its pictures are gone. */
function renderNoPictureSites(m,dv){
 const c=dv.config,list=(c.noPictureSites||[]).slice();
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'No pictures on these sites'),
  h('div',{class:'mute'},'The site works as usual, only its pictures are not shown. '+(dv.isBrowser?'In this Browser.':'In our Browser, and in Chrome, Samsung Internet and Edge when Inspection (the last tab) is on.')));
 const inp=h('input',{type:'text',placeholder:'e.g. amazon.com',class:'grow'});
 const add=function(){const host=hostOfUrl(inp.value.trim()).replace(/^www\./,'');if(!host){snack('Type a site, e.g. amazon.com',1);return}
  c.noPictureSites=[...new Set([...(c.noPictureSites||[]),host])];save('Pictures off on '+host+'.')};
 card.append(h('div',{class:'row',style:'margin-top:8px'},inp,btn('Add','tonal',add)));
 for(const s of list)card.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},s),btn('Pictures back','outline',function(){c.noPictureSites=(c.noPictureSites||[]).filter(function(x){return x!==s});save('Pictures back on '+s+'.')})));
 if(!list.length)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'None yet. Add a site above, or press "No pictures" next to a site in "Sites opened" below.'));
 m.append(card)}
/** Sites opened in our Browser (last 7 days), each with a "No pictures" button. */
function renderBrowserSitesOpened(m,b){
 const hist=b.siteHistory||{},days=Object.keys(hist).sort().reverse(),c=b.config;
 const card=h('div',{class:'card'},h('h2',null,'Sites opened'),h('div',{class:'mute'},'What was opened in this Browser, by day (the last 7 days). Blocked ones are marked.'));
 if(!days.length){card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing yet. Sites show up a minute after they are opened (needs the newest Browser).'));m.append(card);return}
 const off=new Set(c.noPictureSites||[]);
 for(const day of days.slice(0,7)){
  card.append(h('div',{style:'margin-top:12px;font-weight:500'},new Date(day+'T12:00:00Z').toLocaleDateString([],{weekday:'long',month:'short',day:'numeric'})));
  for(const [site,r] of Object.entries(hist[day])){
   const isOff=off.has(site);
   card.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{class:'grow'},site+(r[1]?' (blocked)':'')),h('span',{class:'mute small'},r[0]+'×'),
    btn(isOff?'Pictures back':'No pictures',isOff?'outline':'tonal',async function(){c.noPictureSites=isOff?(c.noPictureSites||[]).filter(function(x){return x!==site}):[...new Set([...(c.noPictureSites||[]),site])];
     try{await saveConfigFor(b,isOff?'Pictures back on '+site+'.':'Pictures off on '+site+'.')}catch(e){snack(e.message,1)}render()})))}}
 m.append(card)}
/** How the (old) Browser looks; the new Browser follows the phone's light/dark setting. */
function renderBrowserLook(m,b){
 const c=b.config;
 const look=h('select',null,new Option('Classic (dark, tabs on top)','classic'),new Option('Like Chrome (white bar, tab count)','chrome'));look.value=c.browserLook||'classic';
 look.onchange=async function(){c.browserLook=look.value;try{await saveConfigFor(b,'Saved.')}catch(e){snack(e.message,1)}render()};
 m.append(h('div',{class:'card'},h('h2',null,'Look'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Style'),h('div',{class:'mute'},'For the older Browser app. The new Browser follows the phone\'s light or dark setting.')),look)))}
/** Our Browser's own on/off switches: everything can be switched off. */
function renderBrowserSwitches(m,b){
 const c=b.config;
 const save=async function(msg){try{await saveConfigFor(b,msg||'Saved. The Browser picks it up within a minute.')}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'Search and safety'));
 const row=function(title,desc,on,set){card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title),h('div',{class:'mute'},desc)),sw(on,function(v){set(v);save()})))};
 row('Search from the address bar','Typing words (not an address) searches Google. Not on "Approved sites only".',!c.searchOff,function(v){c.searchOff=!v});
 row('SafeSearch on Google','Google shows filtered results only.',!c.safeSearchOff,function(v){c.safeSearchOff=!v});
 row('Built-in adult site block','A short built-in list of adult sites that never open, whatever the level.',!c.adultListOff,function(v){c.adultListOff=!v});
 m.append(card)}
function renderWebAccess(m,dv){
 const cur=dv.isBrowser&&dv.config.openWeb?'open':(dv.config.browserFilter||'off');
 const card=h('div',{class:'card'},h('h2',null,'Web access'),
  h('div',{class:'mute'},'Choose what this browser may open. Sites on the allowed list below are always allowed, whichever you pick. This applies to the separate Browser app and the Browser add-on built into the Agent app. On phones managed by the Agent, Moderate and Strict also apply to Chrome, Firefox, Samsung Internet and other browsers, through the Agent\'s site filter (no certificate needed): adult, gambling, drugs, scam and piracy sites are blocked for every app, and Strict\'s social media only in browsers, so WhatsApp and similar apps keep working.'));
 const opts=[
  ['off','Approved sites only','Nothing opens unless it is on the allowed list below. The strictest setting, and what every browser starts with.'],
  ['moderate','Moderate filter','Opens the rest of the web, but blocks adult sites, gambling, drugs and vaping, scams and phishing, and piracy.'],
  ['strict','Strict filter','Everything in Moderate, plus social media (Facebook, Instagram, TikTok, X).']];
 if(dv.isBrowser)opts.push(['open','Open: no filter','Every site opens, like a normal browser. The switches below still apply.']);
 for(const o of opts){
  const r=h('input',{type:'radio',name:'webaccess-'+dv.id,value:o[0]});r.checked=cur===o[0];
  r.onchange=async function(){
   if(o[0]!=='off'&&cur==='off'&&!confirm('This opens the whole web on this browser, except the blocked categories. Filters only know about sites that are already on public lists, so they cannot catch everything. Continue?')){render();return}
   if(dv.isBrowser)dv.config.openWeb=o[0]==='open';
   if(o[0]!=='open')dv.config.browserFilter=o[0];
   try{await saveConfigFor(dv,'Saved. '+(o[0]==='off'?'Approved sites only.':'The browser downloads the lists the next time it checks in, and stays on approved sites only until it has them.'))}catch(e){snack(e.message,1)}
   render()};
  card.append(h('label',{class:'setting',style:'cursor:pointer'},r,h('div',{class:'grow'},h('div',{style:'font-weight:500'},o[1]),h('div',{class:'mute'},o[2]))))}
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},'The lists are public, regularly updated, and about 13 MB the first time. They catch sites already known to them, so they will not cover everything: brand-new sites, and many chat, video and forum sites (for example Reddit, Snapchat, Discord, Telegram and YouTube) are not in them. Until a phone has downloaded the lists, our own browsers stay on approved sites only, but Chrome and other browsers are not filtered yet (the phone cannot cut off the whole internet to wait for them). In Chrome the filter works on site names, not pages, and while it is on QUIC (a faster web protocol) is switched off so browsers use the kind of connection the filter can see. A browser using its own encrypted DNS can sometimes get around the name lookup, though the connection itself is still checked.'));
 m.append(card)}
/* ---------- Overview: SOS, pause, protection level ---------- */
function renderSos(box,d){
 const ev=(d.events||[]).filter(function(e){return e.k==='sos'&&Date.now()-e.at<48*3600000});if(!ev.length)return;
 const card=h('div',{class:'card',style:'border-color:var(--error)'},h('h2',null,'Asked for help'));
 for(const e of ev.slice().reverse()){const link=(String(e.m).match(/https:\/\/maps\.google\.com\/\?q=[-0-9.,]+/)||[])[0];
  card.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{class:'grow'},link?'Where the phone was':e.m),h('span',{class:'mute small'},ago(e.at)),link?h('a',{href:link,target:'_blank',rel:'noopener'},'Open map'):null))}
 box.append(card)}
function hhmm(t){return new Date(t).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})}
function renderPause(box,d){
 const c=d.config,on=(c.pauseUntil||0)>Date.now();
 const save=async function(msg){try{await saveConfigFor(d,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,on?'Web filtering is paused':'Pause web filtering'));
 if(on){card.append(h('div',{class:'mute'},'Until '+hhmm(c.pauseUntil)+'. It comes back by itself then, even if the phone is offline.'),h('div',{style:'margin-top:8px'},btn('Turn filtering back on now','',function(){c.pauseUntil=0;save('Filtering comes back on at the next check-in.')})))}
 else{card.append(h('div',{class:'mute'},(d.isWindows?'Lets every site through for a while, for example while you use the computer yourself: blocked sites, categories, blocked words, SafeSearch and YouTube Restricted Mode stand down. Blocked programs stay blocked. Starts within a minute.':'Lets every site through for a while, for example while you use the phone yourself: blocked sites, categories, blocked words, SafeSearch and YouTube Restricted Mode stand down. Blocked apps stay blocked, and our own browsers keep their allowed-sites list. Starts at the phone\'s next check-in.')));
  const row=h('div',{class:'row',style:'margin-top:8px'});
  for(const [mins,label] of [[15,'15 minutes'],[60,'1 hour'],[180,'3 hours']])row.append(btn(label,'tonal',function(){c.pauseUntil=Date.now()+mins*60000;save('Paused until '+hhmm(c.pauseUntil)+'.')}));
  card.append(row)}
 box.append(card)}
function renderInternetOff(box,d){
 if(d.isBrowser)return;
 const c=d.config,on=(c.internetOffUntil||0)>Date.now();
 const save=async function(msg){try{await saveConfigFor(d,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,on?'The internet is off':'Turn the internet off'));
 if(on){card.append(h('div',{class:'mute'},'Until '+hhmm(c.internetOffUntil)+'. Calls and texts still work. It comes back by itself.'),h('div',{style:'margin-top:8px'},btn('Turn the internet back on','',function(){c.internetOffUntil=0;save('The internet comes back on at the next check-in.')})))}
 else{card.append(h('div',{class:'mute'},'No app can use the internet for a while (homework, dinner, a punishment). Calls and texts still work, and apps you put under "Internet only for chosen apps" keep theirs. The phone itself stays usable.'));
  const row=h('div',{class:'row',style:'margin-top:8px'});
  if(d.isWindows)card.querySelector('.mute').textContent='No website opens for a while (homework, dinner, a punishment), except the dashboard. Programs that do not look up websites by name are not affected.';
  for(const [mins,label] of [[30,'30 minutes'],[60,'1 hour'],[120,'2 hours'],[1440,'Until tomorrow']])row.append(btn(label,'tonal',function(){c.internetOffUntil=Date.now()+mins*60000;save('Internet off until '+hhmm(c.internetOffUntil)+'.')}));
  card.append(row)}
 box.append(card)}
const LEVELS={
 email:{label:'Email only',desc:'Only email apps (Gmail, Outlook, Yahoo Mail, Samsung Email) can use the internet. Calls and texts work as usual. Browsers open approved sites only; new apps wait for you.',
  set:{browserFilter:'off',safeSearch:true,youtubeDns:'strict',blockImages:false,blockImageSearch:true,blockAppGroups:[],approveNew:true,noIncognito:true,internetOnlyApps:true,internetApps:['com.google.android.gm','com.microsoft.office.outlook','com.yahoo.mobile.client.android.mail','com.samsung.android.email.provider','com.android.email']}},
 young:{label:'Young child (under 10)',desc:'Only sites you approve. No pictures on web pages, no image search, no web pages inside apps. Every app group blocked; new apps wait for you. WhatsApp Status and Channels closed. Message alerts on.',
  set:{browserFilter:'off',safeSearch:true,youtubeDns:'strict',blockImages:true,blockImageSearch:true,blockAppGroups:'all',approveNew:true,noIncognito:true,blockWaStatus:true,blockWaChannels:true,inAppBrowsers:'all',messageAlerts:true}},
 teen:{label:'Teen',desc:'Strict filter (adult, gambling, drugs, scam, piracy sites blocked). SafeSearch, YouTube Restricted Mode, no image search. Dating apps blocked, new apps wait for you. Browser pages that apps open are closed. Message alerts on. Ads blocked.',
  set:{browserFilter:'strict',safeSearch:true,youtubeDns:'moderate',blockImages:false,blockImageSearch:true,blockAppGroups:['dating'],approveNew:true,noIncognito:true,inAppBrowsers:'tabs',messageAlerts:true,blockAds:true}},
 kosher:{label:'Kosher phone',desc:'Only sites you approve, no pictures on web pages, no image search, no web pages inside apps. Social media, video, games, dating and shopping apps blocked; new apps wait for you. WhatsApp chats and calls work; Status and Channels closed.',
  set:{browserFilter:'off',safeSearch:true,youtubeDns:'strict',blockImages:true,blockImageSearch:true,blockAppGroups:'all',approveNew:true,noIncognito:true,blockWaStatus:true,blockWaChannels:true,inAppBrowsers:'all'}},
 strictest:{label:'Strictest',desc:'Approved sites only. SafeSearch on, strict YouTube Restricted Mode, no pictures on web pages, no image search. Every app group blocked (social media, games, video, dating, shopping). New apps wait for you. No incognito in Chrome.',
  set:{browserFilter:'off',safeSearch:true,youtubeDns:'strict',blockImages:true,blockImageSearch:true,blockAppGroups:'all',approveNew:true,noIncognito:true}},
 recommended:{label:'Recommended',desc:'Strict filter: adult, gambling, drugs, scam, piracy and social media sites blocked. SafeSearch on, YouTube Restricted Mode, no image search. Social media and dating apps blocked. New apps wait for you. No incognito in Chrome. Ads blocked.',
  set:{browserFilter:'strict',safeSearch:true,youtubeDns:'moderate',blockImages:false,blockImageSearch:true,blockAppGroups:['social','dating'],approveNew:true,noIncognito:true,blockAds:true}},
 light:{label:'Light',desc:'Moderate filter: adult, gambling, drugs, scam and piracy sites blocked. SafeSearch on, YouTube Restricted Mode. Dating apps blocked. Everything else open.',
  set:{browserFilter:'moderate',safeSearch:true,youtubeDns:'moderate',blockImages:false,blockImageSearch:false,blockAppGroups:['dating'],approveNew:false,noIncognito:false}}};
function applyLevel(c,s){const groups=Object.keys(state.appGroups||{});
 c.browserFilter=s.browserFilter;c.safeSearch=s.safeSearch;c.youtubeDns=s.youtubeDns;c.blockImages=s.blockImages;c.blockImageSearch=s.blockImageSearch;
 c.blockAppGroups=s.blockAppGroups==='all'?groups:s.blockAppGroups.filter(function(g){return groups.includes(g)});
 c.approveNew=s.approveNew;c.chromePolicy=Object.assign({},c.chromePolicy||{},{noIncognito:s.noIncognito});
 for(const k of ['blockWaStatus','blockWaChannels','inAppBrowsers','messageAlerts','blockAds'])if(s[k]!==undefined)c[k]=s[k];
 // Only "Email only" limits the internet to chosen apps; every other setup switches that off again.
 c.internetOnlyApps=!!s.internetOnlyApps;if(s.internetApps)c.internetApps=s.internetApps.slice()}
function renderLevels(box,d){
 const card=h('div',{class:'card'},h('h2',null,'Ready-made setups'),h('div',{class:'mute'},'Not sure what to switch on? Pick the one that fits. One tap sets many settings at once; you can still change any single setting afterwards. Your allowed sites, and apps you set to Allow, are kept.'));
 for(const k of Object.keys(LEVELS)){const L=LEVELS[k];
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},L.label),h('div',{class:'mute small'},L.desc)),btn('Use this','tonal',async function(){
   if(!confirm('Set this phone to "'+L.label+'"?\n\n'+L.desc))return;
   applyLevel(d.config,L.set);try{await saveConfigFor(d,'Set to '+L.label+'. The phone picks it up within about 5 minutes.')}catch(e){snack(e.message,1)}render()})))}
 box.append(card)}

/** "Images" tab of Google, Bing and DuckDuckGo. */
/** Ad servers blocked phone-wide (with Moderate or Strict). */
function renderBlockAds(m,dv){const c=dv.config;
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block ads'),
  h('div',{class:'mute small'},dv.isWindows?'Ad servers are blocked in every browser and program on this computer (on Moderate and Strict). Needs LockGuard from November 13 or later; it updates itself.':dv.isBrowser?'No ads on web pages in this Browser, and no ads on YouTube (before or during videos, banners, promoted videos). Best effort on YouTube: it changes its pages from time to time.':'Ad servers are blocked in every app and browser on the phone, on every level (Chrome, Samsung Internet and the rest too). YouTube\'s own ads are removed in our Browser, the YouTube app, and Chrome with Inspection. A few apps may show empty boxes where ads were; switch it off if one stops working.')),
  sw(c.blockAds,async function(on){c.blockAds=on;try{await saveConfigFor(dv,on?'Ads blocked from the next check-in.':'Ads allowed again.')}catch(e){snack(e.message,1)}render()}))))}
/** Sites that will not open with ads blocked: their ads load, covered in black (our Browser). */
function renderAdsCovered(m,dv){const c=dv.config;if(dv.isWindows||!c.blockAds)return;
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'Sites that refuse ad blocking'),
  h('div',{class:'mute'},'Some sites will not open while ads are blocked. On these, the ads load so the site works, but every ad is covered in black and cannot be tapped. In our Browser; the ⋮ menu there also has "Show ads on this page (covered)".'));
 card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Notice them automatically'),
  h('div',{class:'mute'},'When a page says "turn off your ad blocker", the Browser switches that site to covered ads and reloads it by itself.')),
  sw(!c.adsAutoCoverOff,function(on){c.adsAutoCoverOff=!on;save(on?'The Browser notices such sites by itself.':'Only the sites listed here.')})));
 const inp=h('input',{type:'text',class:'grow',placeholder:'e.g. example-news.com'});
 card.append(h('div',{class:'row',style:'margin-top:8px'},inp,btn('Add','tonal',function(){const host=hostOfUrl(inp.value.trim()).replace(/^www\./,'');if(!host)return;
  c.adsCoveredSites=[...new Set([...(c.adsCoveredSites||[]),host])];save('Ads on '+host+' load, covered.')})));
 for(const s of (c.adsCoveredSites||[]))card.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},s),btn('Block ads again','outline',function(){c.adsCoveredSites=(c.adsCoveredSites||[]).filter(function(x){return x!==s});save('Ads on '+s+' are blocked again.')})));
 const local=(dv.info&&dv.info.adsCovered)||[];
 if(local.length){card.append(h('div',{style:'margin-top:12px;font-weight:500'},'Switched on the phone'),h('div',{class:'mute small'},'Noticed by the Browser, or chosen in its ⋮ menu. They can be switched back from the same menu.'));
  for(const s of local)card.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},s),(c.adsCoveredSites||[]).includes(s)?null:btn('Keep on the list','outline',function(){c.adsCoveredSites=[...new Set([...(c.adsCoveredSites||[]),s])];save('Kept.')})))}
 m.append(card)}
function renderImageSearch(m,dv){const c=dv.config;
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block image search'),
  h('div',{class:'mute small'},'The "Images" tab of Google, Bing and DuckDuckGo is refused; normal searches keep working. '+(dv.isBrowser?'Applies to this browser.':'In our own browsers always. In Chrome and other browsers only with Inspection on (the last tab).'))),
  sw(c.blockImageSearch,async function(on){c.blockImageSearch=on;try{await saveConfigFor(dv,on?'Image search will be blocked.':'Image search is allowed again.')}catch(e){snack(e.message,1)}}))))}

/* ---------- Calls & texts ---------- */
function buildCallsGroup(m,dv){
 const sub=getSub('calls');
 if(sub==='rules')renderCallRules(m,dv);
 else if(sub==='calllog')renderCallLog(m,dv);
 else if(sub==='contacts')renderContacts(m,dv);
 else if(sub==='texts')renderTexts(m,dv);
}
const CALL_MODE_OPTS=[['all','Anyone'],['contacts','Only the phone\'s contacts and the numbers below'],['list','Only the numbers below']];
function renderCallRules(m,dv){
 const c=dv.config,save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const cc=(dv.info||{}).callControl;
 const wantIn=(c.callsIn||'all')!=='all',wantOut=(c.callsOut||'all')!=='all';
 if(wantIn||wantOut){
  let msg=null;
  if(!cc)msg='The phone has not reported yet whether call controls are ready (it needs the latest Agent build).';
  else if(cc.supported===false)msg='This phone\'s Android version is too old for call controls (Android 10 or newer is needed).';
  else if((wantIn&&!cc.screening)||(wantOut&&!cc.redirection))msg='One step left on the phone: tap the notification "Finish setting up call controls" and choose Allow (Android asks once; nobody can do this from here). Until then calls are not checked.';
  if(msg)m.append(h('div',{class:'card',style:'border-color:var(--warn)'},h('h2',null,'Not active yet'),h('div',{class:'mute'},msg)));
  else m.append(h('div',{class:'card'},h('div',{class:'mute'},'Call controls are active on the phone.')));
 }
 const sel=function(val,on){const s=h('select',{style:'max-width:100%'});for(const [v,l] of CALL_MODE_OPTS){const o=new Option(l,v);if((val||'all')===v)o.selected=true;s.append(o)}s.onchange=function(){on(s.value)};return s};
 m.append(h('div',{class:'card'},h('h2',null,'Who can call'),
  h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Incoming calls'),h('div',{class:'mute small'},'Calls from anyone else are rejected without ringing. Calls from hidden or private numbers cannot be blocked here; turn on "Block unknown callers" in the phone\'s own Phone app for those.')),
   sel(c.callsIn,function(v){c.callsIn=v;save('Saved. Applies at the next check-in.')})),
  h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Outgoing calls'),h('div',{class:'mute small'},'Calls to other numbers are stopped with a message. Emergency calls (911, 112 and so on) and voicemail always work.')),
   sel(c.callsOut,function(v){c.callsOut=v;save('Saved. Applies at the next check-in.')})),
  h('div',{class:'mute small',style:'margin-top:8px'},'"Contacts" lets through anyone saved in the phone\'s contacts, and he can add contacts himself. For full control choose "Only the numbers below". To stop call controls being switched off in Settings, turn on "Block changing default apps" (Security → Restrictions).'),
  !(c.restrictions||{}).defaultAppsDisabled&&(wantIn||wantOut)?h('div',{style:'margin-top:8px'},btn('Turn on "Block changing default apps"','tonal',function(){c.restrictions=Object.assign({},c.restrictions,{defaultAppsDisabled:true});save('Turned on.')})):null));
 const nc=h('div',{class:'card'},h('h2',null,'Allowed numbers ('+(c.callNumbers||[]).length+')'),h('div',{class:'mute'},'Used by both settings above. A number matches however it is written (with or without the country code, spaces or dashes).'));
 (c.callNumbers||[]).forEach(function(n,i){nc.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},(n.name?n.name+' · ':'')+n.number),btn('Remove','danger',function(){c.callNumbers.splice(i,1);save('Removed.')})))});
 const name=h('input',{type:'text',placeholder:'Name, e.g. Mom',style:'flex:1;min-width:140px'}),num=h('input',{type:'tel',placeholder:'Phone number',style:'flex:1;min-width:160px'});
 nc.append(h('div',{class:'row',style:'margin-top:10px;flex-wrap:wrap'},name,num,btn('Add number','',function(){
  if(num.value.replace(/\D/g,'').length<3){snack('Type a phone number.',1);return}
  c.callNumbers=(c.callNumbers||[]).concat([{name:name.value.trim(),number:num.value.trim()}]);save('Number added.')})));
 const calls=dv.calls||[],seen=new Set(),recent=[];
 for(const x of calls.slice().reverse()){const k=x.number.replace(/\D/g,'').slice(-9);if(!k||seen.has(k)||(c.callNumbers||[]).some(function(n){return n.number.replace(/\D/g,'').slice(-9)===k}))continue;seen.add(k);recent.push(x);if(recent.length>=8)break}
 if(recent.length){nc.append(h('div',{style:'font-weight:500;margin-top:14px'},'Add from recent calls'));
  for(const x of recent)nc.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{class:'grow'},(x.name?x.name+' · ':'')+x.number),btn('Add','tonal',function(){c.callNumbers=(c.callNumbers||[]).concat([{name:x.name||'',number:x.number}]);save('Number added.')})))}
 m.append(nc);
 m.append(h('div',{class:'card'},h('div',{class:'mute small'},'Also in Security → Restrictions: "Block making calls" (all calls except emergency) and "Block sending and receiving SMS" (all texts). Android does not let an app block texts from just some numbers.')))}
function renderContacts(m,dv){
 const c=dv.config,save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const sw2=function(key,title,desc,on,off){return h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title),h('div',{class:'mute small'},desc)),sw(c[key],function(v){c[key]=v;save(v?on:off)}))};
 m.append(h('div',{class:'card'},h('h2',null,'Contacts'),
  sw2('reportContacts','Show the phone\'s contacts here','The list is sent whenever it changes on the phone.','The list arrives with the next check-in.','Switched off.'),
  sw2('syncContacts','Put the allowed numbers into the phone\'s contacts','Every number under Who can call → Allowed numbers shows up by name in the phone app. Only contacts added this way are ever changed or removed. Android has no way to stop him adding contacts himself; for full control set calls to "Only the numbers below".','Allowed numbers go into the phone\'s contacts at the next check-in.','The contacts added this way are removed at the next check-in.')));
 const list=dv.contacts||[],allowed=(c.callNumbers||[]).map(function(n){return n.number.replace(/\D/g,'').slice(-9)});
 const card=h('div',{class:'card'},h('h2',null,'On the phone ('+list.length+')'));
 if(dv.contactsAt)card.append(h('div',{class:'mute small'},'Updated '+ago(dv.contactsAt)));
 if(!list.length)card.append(h('div',{class:'mute'},c.reportContacts?'Nothing yet. The list arrives with the phone\'s next check-in.':'Switch on "Show the phone\'s contacts here" above.'));
 for(const x of list){const k=x.number.replace(/\D/g,'').slice(-9),ok=k&&allowed.includes(k);
  card.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{class:'grow'},(x.name||'(no name)')+' · '+x.number),ok?h('span',{class:'chip'},'allowed to call'):btn('Allow calls','tonal',function(){c.callNumbers=(c.callNumbers||[]).concat([{name:x.name,number:x.number}]);save('Added to the allowed numbers.')})))}
 m.append(card)}
function logToggle(m,dv,title,desc,key,extra){
 const c=dv.config;
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title),h('div',{class:'mute small'},desc)),
  sw(c[key],async function(on){c[key]=on;try{await saveConfigFor(dv,on?'Switched on. Entries arrive with the phone\'s next check-ins (the past week first).':'Switched off.')}catch(e){snack(e.message,1)}render()})),extra||null))}
const CALL_TYPE_WORDS={in:'Incoming',out:'Outgoing',missed:'Missed',voicemail:'Voicemail',rejected:'Rejected',blocked:'Blocked',other:'Call'};
function dur(s){if(!s)return '';const m=Math.floor(s/60);return m?m+' min '+(s%60)+' s':s+' s'}
function renderCallLog(m,dv){
 logToggle(m,dv,'Send the call log to the dashboard','Who the phone called, who called it, missed and blocked calls, and how long each call took.','callLog');
 const list=(dv.calls||[]).slice().reverse(),card=h('div',{class:'card'},h('h2',null,'Calls'));
 if(!list.length)card.append(h('div',{class:'mute'},dv.config.callLog?'Nothing yet. Calls show up after the phone\'s next check-in.':'Switch on the call log above.'));
 for(const x of list.slice(0,150))card.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{style:'width:90px'},h('span',{class:'chip'+(x.type==='blocked'||x.type==='missed'?' bad':'')},CALL_TYPE_WORDS[x.type]||'Call')),h('span',{class:'grow'},(x.name?x.name+' · ':'')+(x.number||'hidden number')),h('span',{class:'mute small'},dur(x.secs)),h('span',{class:'mute small',style:'min-width:130px;text-align:right'},new Date(x.at).toLocaleString())));
 m.append(card)}
function renderTexts(m,dv){
 const c=dv.config;
 logToggle(m,dv,'Send the texts (SMS) log to the dashboard','Who texted the phone and whom it texted, and when. Only ordinary text messages: not WhatsApp or other chat apps.','textLog',
  c.textLog?h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Include what the messages say'),h('div',{class:'mute small'},'Off: only who and when. On: the first 300 letters of each message are stored on the dashboard.')),sw(c.textContent,async function(on){c.textContent=on;try{await saveConfigFor(dv,on?'Message words included from now on.':'Only who and when from now on.')}catch(e){snack(e.message,1)}render()})):null);
 const list=(dv.texts||[]).slice().reverse(),card=h('div',{class:'card'},h('h2',null,'Texts'));
 if(!list.length)card.append(h('div',{class:'mute'},c.textLog?'Nothing yet. Texts show up after the phone\'s next check-in.':'Switch on the texts log above.'));
 for(const x of list.slice(0,150))card.append(h('div',{style:'margin:6px 0'},h('div',{class:'row'},h('span',{class:'chip'},x.type==='in'?'From':'To'),h('span',{class:'grow'},(x.name?x.name+' · ':'')+x.number),h('span',{class:'mute small'},new Date(x.at).toLocaleString())),x.text?h('div',{class:'mute',style:'margin:2px 0 0 8px;white-space:pre-wrap'},x.text):null));
 m.append(card)}

/* ---------- Internet: per-app filter modes ---------- */
function renderAppFilterModes(m,dv){
 const c=dv.config,save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'Filtering per app'),h('div',{class:'mute'},'"Filtered" is normal: the site filter, safe search and blocked words apply. "Not filtered": the site filter leaves this app alone, useful when the filter breaks an app you trust. "No internet": the app opens but cannot go online.'));
 const skip=new Set(c.filterSkipApps||[]),nonet=c.networkBlocklist||{};
 const apps=dv.packages.filter(function(p){return p.p!==SELF_PACKAGE&&!p.s}).sort(function(a,b){return (a.l||a.p).localeCompare(b.l||b.p)});
 const changed=apps.filter(function(a){return skip.has(a.p)||nonet[a.p]});
 const row=function(a){const s=h('select',null,new Option('Filtered','normal'),new Option('Not filtered','skip'),new Option('No internet','nonet'));s.value=nonet[a.p]?'nonet':skip.has(a.p)?'skip':'normal';
  s.onchange=function(){const ns=new Set(c.filterSkipApps||[]),nb=Object.assign({},c.networkBlocklist||{});ns.delete(a.p);delete nb[a.p];
   if(s.value==='skip')ns.add(a.p);if(s.value==='nonet')nb[a.p]={paused:false};c.filterSkipApps=[...ns];c.networkBlocklist=nb;save('Saved. Applies at the next check-in.')};
  return h('div',{class:'setting'},h('div',{class:'grow'},a.l||a.p),s)};
 for(const a of changed)card.append(row(a));
 const rest=apps.filter(function(a){return !changed.includes(a)});
 if(rest.length){const more=h('details',{style:'margin-top:8px'},h('summary',{style:'cursor:pointer'},(changed.length?'Other apps':'All apps')+' ('+rest.length+')'));for(const a of rest)more.append(row(a));card.append(more)}
 if(!apps.length)card.append(h('div',{class:'mute'},'The phone has not reported its apps yet.'));
 m.append(card)}

/* ---------- Apps: WhatsApp Status and Channels ---------- */
function renderWhatsApp(m,dv){
 if(dv.isBrowser)return;
 const c=dv.config,on=(dv.info||{}).accessibility;
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const row=function(key,title,desc){return h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title),h('div',{class:'mute small'},desc)),sw(c[key],function(v){c[key]=v;save(v?'Saved. Applies at the next check-in.':'Allowed again.')}))};
 const card=h('div',{class:'card'},h('h2',null,'WhatsApp'),h('div',{class:'mute'},'Chats, groups and calls keep working. These close parts of WhatsApp the moment they open. They work by the name of WhatsApp\'s screens, so a WhatsApp update can stop them working until we adjust; the phone log says "Closed WhatsApp Status" each time it works.'),
  row('blockWaStatus','Close Status','Watching other people\'s statuses.'),row('blockWaChannels','Close Channels','Following and reading channels.'),row('waTextOnly','Text and calls only','No photos, videos or voice messages: WhatsApp\'s media servers are blocked. Test it on the phone first: send yourself a text and a photo.'));
 if((c.blockWaStatus||c.blockWaChannels)&&on===false)card.append(h('div',{class:'mute small',style:'margin-top:8px;color:var(--warn)'},'One step left on the phone: tap the notification "Finish setting up: turn on MDM Agent" and switch it on.'));
 m.append(card)}

/* ---------- Browser: web pages inside apps ---------- */
function renderInAppBrowsers(m,dv){
 if(dv.isBrowser)return;
 const c=dv.config,save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const mode=c.inAppBrowsers||'off',on=(dv.info||{}).accessibility;
 const card=h('div',{class:'card'},h('h2',null,'Web pages inside apps'),h('div',{class:'mute'},'When he taps a link inside an app like Instagram or Gmail, the page opens inside that app. Those pages are already filtered like Chrome; this closes them altogether, so links in apps do not open.'));
 const opts=[['off','Allowed','Links in apps open as usual (still filtered).'],['tabs','Close browser pages opened by apps','The page an app opens on top of itself (Chrome, Samsung Internet...). Chrome itself still works. Some apps\' "Sign in with Google" uses this page, so a sign-in may be stopped too.'],['all','Close every web page inside apps (strictest)','Also pages built into apps. Some apps show their own screens as web pages (logins, help, some whole apps): add those below so they keep working.']];
 for(const [v,l,d] of opts){const r=h('input',{type:'radio',name:'inapp'});r.checked=mode===v;r.onchange=function(){c.inAppBrowsers=v;save(v==='off'?'Links in apps open again.':'Saved. Applies at the next check-in.')};
  card.append(h('label',{class:'setting',style:'cursor:pointer'},r,h('div',{class:'grow',style:'margin-left:10px'},h('div',{style:'font-weight:500'},l),h('div',{class:'mute small'},d))))}
 if(mode!=='off'){
  if(on===false)card.append(h('div',{class:'card',style:'border-color:var(--warn);margin-top:10px'},h('div',{style:'font-weight:500'},'One step left on the phone'),h('div',{class:'mute small'},'Tap the notification "Finish setting up: turn on MDM Agent", choose MDM Agent and switch it on. Android lets nobody do this from afar. To stop it being switched off again, keep "Block changing apps in Settings" on (Security → Restrictions).')));
  else if(on===undefined)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'The phone has not said yet whether this is switched on (it needs the latest Agent build).'));
  else card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Active on the phone.'));
 }
 if(mode==='all'){
  const ex=new Set(c.inAppExceptions||[]);const apps=dv.packages.filter(function(p){return p.p!==SELF_PACKAGE&&!p.s}).sort(function(a,b){return (a.l||a.p).localeCompare(b.l||b.p)});
  const box=h('details',{style:'margin-top:10px'},h('summary',{style:'cursor:pointer'},'Apps whose web pages stay allowed ('+ex.size+')'));
  for(const a of apps){const cb=h('input',{type:'checkbox'});cb.checked=ex.has(a.p);cb.onchange=function(){if(cb.checked)ex.add(a.p);else ex.delete(a.p)};box.append(h('label',{class:'row',style:'cursor:pointer;margin:4px 0'},cb,a.l||a.p))}
  box.append(h('div',{style:'margin-top:6px'},btn('Save','tonal',function(){c.inAppExceptions=[...ex];save('Saved.')})));
  card.append(box)}
 m.append(card)}

/* ---------- Browser: filter schedule ---------- */
const DAY_NAMES=['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
function renderFilterSchedule(m,dv){
 if(dv.isBrowser)return;
 const c=dv.config,s=Object.assign({on:false,level:'strict',days:[0,1,2,3,4],from:'08:00',to:'15:00'},c.filterSchedule||{});
 const LV=[['off','Approved sites only'],['strict','Strict'],['moderate','Moderate']];
 const lv=h('select',null);for(const [v,l] of LV)lv.append(new Option(l,v));lv.value=s.level;
 const days=h('div',{class:'row',style:'flex-wrap:wrap;margin-top:6px'});const boxes=[];
 DAY_NAMES.forEach(function(n,i){const cb=h('input',{type:'checkbox'});cb.checked=s.days.includes(i);boxes.push(cb);days.append(h('label',{class:'row',style:'cursor:pointer;margin-right:10px'},cb,n))});
 const from=h('input',{type:'time',value:s.from}),to=h('input',{type:'time',value:s.to});
 const save=async function(on){c.filterSchedule={on:on,level:lv.value,days:boxes.map(function(b,i){return b.checked?i:-1}).filter(function(i){return i>=0}),from:from.value||'08:00',to:to.value||'15:00'};
  try{await saveConfigFor(dv,on?'Schedule saved.':'Schedule off.')}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Filter schedule'),
  h('div',{class:'mute small'},'A different level at set times, for example Strict on school days from 8:00 to 15:00. Uses the '+(dv.isWindows?'computer':'phone')+'\'s own clock; a change takes effect at its next check-in (within '+(dv.isWindows?'a minute':'about 5 minutes')+'). Outside these hours the level above applies.'+(s.on?' Now on: '+(LV.find(function(x){return x[0]===s.level})||[0,''])[1]+' '+s.from+' to '+s.to+'.':''))),
  sw(s.on,function(on){save(on)})),
  h('div',{class:'row',style:'margin-top:8px;flex-wrap:wrap'},h('span',null,'Level'),lv,h('span',null,'from'),from,h('span',null,'to'),to),days,
  h('div',{style:'margin-top:8px'},btn('Save schedule','tonal',function(){save(true)})));
 m.append(card)}

/* ---------- Browser: sites visited ---------- */
let sitesDay=null,sitesQuery='',sitesBlockedOnly=false;
function renderSitesVisited(m,dv){
 const c=dv.config,hist=dv.siteHistory||{},daysList=Object.keys(hist).sort().reverse();
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Keep a list of the sites this phone opens'),
  h('div',{class:'mute small'},(dv.isWindows?'Every website looked up on this computer, by any browser or program; background services are left out. Kept for two weeks.':'From every app, not only browsers; background services are left out. Kept for two weeks. The phone shows a key (VPN) icon while it runs.')+' The level filters (Strict, Moderate, safe search, blocked words) also fill this list when they are on.')),
  sw(c.recordSites,async function(on){c.recordSites=on;try{await saveConfigFor(dv,on?'From the next check-in the phone keeps the list.':'Switched off.')}catch(e){snack(e.message,1)}render()}))));
 if(!dv.isWindows){
  const sc=h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Keep a list of searches'),
   h('div',{class:'mute small'},'What is typed into search boxes in Google, YouTube, TikTok, Instagram and browsers. The child is told this is on. Needs the accessibility switch on the phone.')),
   sw(c.recordSearches,async function(on){c.recordSearches=on;try{await saveConfigFor(dv,on?'From the next check-in searches are kept.':'Switched off.')}catch(e){snack(e.message,1)}render()})));
  const srch=(dv.searches||[]).slice().reverse();
  if(srch.length){sc.append(h('div',{style:'margin-top:10px;font-weight:500'},'Searches ('+srch.length+')'));
   for(const x of srch.slice(0,150))sc.append(h('div',{class:'act'},h('b',null,x.q),' · '+(CHAT_NAMES[x.app]||SEARCH_NAMES[x.app]||x.app)+' · '+ago(x.at)))}
  else if(c.recordSearches)sc.append(h('div',{class:'mute small',style:'margin-top:8px'},'No searches yet.'));
  m.append(sc);
  const vc=h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Keep a list of YouTube videos watched'),
   h('div',{class:'mute small'},'The name of each video watched for at least 10 seconds in the YouTube app. The child is told this is on. Needs the accessibility switch on the phone.')),
   sw(c.recordVideos,async function(on){c.recordVideos=on;try{await saveConfigFor(dv,on?'From the next check-in videos are listed.':'Switched off.')}catch(e){snack(e.message,1)}render()})));
  const vids=(dv.videos||[]).slice().reverse();
  if(vids.length){vc.append(h('div',{style:'margin-top:10px;font-weight:500'},'Videos ('+vids.length+')'));
   for(const x of vids.slice(0,150))vc.append(h('div',{class:'act'},h('b',null,x.title),' · '+ago(x.at)))}
  else if(c.recordVideos)vc.append(h('div',{class:'mute small',style:'margin-top:8px'},'No videos yet.'));
  m.append(vc)}
 const card=h('div',{class:'card'},h('h2',null,'Sites visited'));
 if(!daysList.length){card.append(h('div',{class:'mute'},'Nothing yet. Sites show up after the phone\'s next check-ins.'));m.append(card);return}
 if(!sitesDay||!hist[sitesDay])sitesDay=daysList[0];
 const sel=h('select',null);for(const d of daysList)sel.append(new Option(new Date(d+'T12:00:00Z').toLocaleDateString([],{weekday:'long',month:'short',day:'numeric'}),d));sel.value=sitesDay;sel.onchange=function(){sitesDay=sel.value;render()};
 const q=h('input',{type:'text',placeholder:'Search sites',value:sitesQuery,style:'flex:1;min-width:150px'});q.oninput=function(){sitesQuery=q.value;draw()};
 const bo=h('input',{type:'checkbox'});bo.checked=sitesBlockedOnly;bo.onchange=function(){sitesBlockedOnly=bo.checked;draw()};
 card.append(h('div',{class:'row',style:'flex-wrap:wrap;margin:8px 0'},sel,q,h('label',{class:'row',style:'cursor:pointer'},bo,'Only blocked')));
 const list=h('div');card.append(list);
 const draw=function(){list.textContent='';
  const rows=Object.entries(hist[sitesDay]||{}).filter(function(e){return (!sitesQuery||e[0].includes(sitesQuery.toLowerCase()))&&(!sitesBlockedOnly||e[1][1])});
  if(!rows.length){list.append(h('div',{class:'mute'},'No sites match.'));return}
  for(const [site,r] of rows)list.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{class:'grow'},site),r[2]?h('span',{class:'mute small'},r[2]):null,
   r[1]?h('span',{class:'chip bad'},'blocked'+(r[1]<r[0]?' '+r[1]+' of '+r[0]:'')):h('span',{class:'mute small'},r[0]+'×')))};
 draw();m.append(card)}

/* ---------- Internet: only chosen apps get internet ---------- */
function renderInternetApps(m,dv){
 const c=dv.config,save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'Internet only for chosen apps'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Only the apps ticked below can use the internet'),
  h('div',{class:'mute small'},'Every other app still opens, but gets no internet. Calls and texts, notifications for the ticked apps, and Wi-Fi sign-in pages keep working. The phone shows a key (VPN) icon while this is on.')),
  sw(c.internetOnlyApps,function(on){c.internetOnlyApps=on;save(on?'From the next check-in only the ticked apps have internet.':'Every app has internet again from the next check-in.')})));
 const chosen=new Set(c.internetApps||[]),count=h('div',{style:'font-weight:500;margin-top:12px'},chosen.size+' app(s) ticked');
 const list=h('div',{style:'margin-top:6px'});
 const apps=dv.packages.filter(function(p){return p.p!==SELF_PACKAGE}).sort(function(a,b){return (a.s?1:0)-(b.s?1:0)||(a.l||a.p).localeCompare(b.l||b.p)});
 for(const a of apps){const cb=h('input',{type:'checkbox'});cb.checked=chosen.has(a.p);cb.onchange=function(){if(cb.checked)chosen.add(a.p);else chosen.delete(a.p);count.textContent=chosen.size+' app(s) ticked'};
  list.append(h('label',{class:'row',style:'cursor:pointer;margin:4px 0'},cb,(a.l||a.p)+(a.s?' (built in)':'')))}
 if(!apps.length)list.append(h('div',{class:'mute'},'The phone has not reported its apps yet.'));
 card.append(count,list,h('div',{style:'margin-top:8px'},btn('Save the ticked apps','',function(){c.internetApps=[...chosen];save('Saved.')})));
 m.append(card)}

/* ---------- Device: location history and places ---------- */
function distM(a,b,c,d){const r=Math.PI/180,x=Math.sin((c-a)*r/2)**2+Math.cos(a*r)*Math.cos(c*r)*Math.sin((d-b)*r/2)**2;return 2*6371000*Math.asin(Math.min(1,Math.sqrt(x)))}
function placeAt(places,f){for(const p of places||[])if(distM(p.lat,p.lon,f.lat,f.lon)<=p.r)return p;return null}
function mapLink(lat,lon,label){return h('a',{href:'https://maps.google.com/?q='+lat+','+lon,target:'_blank',rel:'noopener'},label||'Map')}
function renderLocation(m,dv){
 const c=dv.config,save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const sel=h('select',null);for(const [v,l] of [[0,'Off'],[15,'Every 15 minutes'],[30,'Every 30 minutes'],[60,'Every hour']]){const o=new Option(l,v);if((c.locationEvery||0)===v)o.selected=true;sel.append(o)}
 sel.onchange=function(){c.locationEvery=Number(sel.value);save(c.locationEvery?'The phone notes its location every '+c.locationEvery+' minutes from the next check-in.':'Location history is off.')};
 m.append(h('div',{class:'card'},h('h2',null,'Location history'),h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Note where the phone is'),
  h('div',{class:'mute small'},'The phone checks its location this often and sends it with its next check-in. It uses a little battery, and Location has to be on, on the phone. "Locate now" on the Overview still works either way.')),sel)));
 const hist=(dv.locations||[]).slice().reverse();
 const hc=h('div',{class:'card'},h('h2',null,'Where it was'));
 if(!hist.length)hc.append(h('div',{class:'mute'},c.locationEvery?'Nothing yet. The first location arrives within a few check-ins.':'Switch on location history above to see where the phone has been.'));
 for(const f of hist.slice(0,40)){const p=placeAt(c.places,f);
  hc.append(h('div',{class:'row',style:'margin:4px 0'},h('span',{class:'grow'},new Date(f.at).toLocaleString()+(p?' · '+p.name:'')+(f.why==='sos'?' · asked for help':'')),h('span',{class:'mute small'},f.acc?'within '+Math.round(f.acc)+' m':''),mapLink(f.lat,f.lon)))}
 m.append(hc);
 const pc=h('div',{class:'card'},h('h2',null,'Places'),h('div',{class:'mute'},'Save places like home or school. With location history on, you get an alert (Settings → Alerts) when the phone arrives at or leaves one. It is only as quick as the location history: with "every 30 minutes" an arrival can show up to 30 minutes late.'));
 (c.places||[]).forEach(function(p,i){pc.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},p.name+' · within '+p.r+' m'),mapLink(p.lat,p.lon),btn('Remove','danger',function(){c.places.splice(i,1);save('Place removed.')})))});
 const name=h('input',{type:'text',placeholder:'Name, e.g. Home',style:'flex:1;min-width:140px'});
 const where=h('input',{type:'text',placeholder:'Paste a Google Maps link or coordinates; empty = where the phone was last',style:'flex:2;min-width:240px'});
 const rad=h('select',null);for(const r of [100,200,500,1000])rad.append(new Option('within '+r+' m',r));rad.value='200';
 pc.append(h('div',{class:'row',style:'margin-top:10px;flex-wrap:wrap'},name,where,rad,btn('Add place','',function(){
  if(!name.value.trim()){snack('Give the place a name.',1);return}
  let lat,lon;const mm=where.value.match(/(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)/);
  if(mm){lat=Number(mm[1]);lon=Number(mm[2])}
  else if(where.value.trim()){snack('No coordinates found in that. In Google Maps, press and hold the spot and copy the numbers that appear (like 40.6782, -73.9442).',1);return}
  else{if(!hist[0]){snack('No location from the phone yet. Paste coordinates instead.',1);return}lat=hist[0].lat;lon=hist[0].lon}
  c.places=(c.places||[]).concat([{name:name.value.trim(),lat:lat,lon:lon,r:Number(rad.value)}]);save('Place saved.')})));
 m.append(pc)}

/** Words that block any site name, page or search containing them. */
function renderBlockedWords(m,dv){
 const c=dv.config,ta=h('textarea',{rows:5,style:'width:100%;margin-top:8px',placeholder:'one word or phrase per line, e.g. casino'});ta.value=(c.blockedWords||[]).join('\n');
 const card=h('div',{class:'card'},h('h2',null,'Blocked words'+((c.blockedWords||[]).length?' ('+c.blockedWords.length+')':'')),
  h('div',{class:'mute'},'Any site, page or search that contains one of these is blocked. Short words (1 to 3 letters) only count as a whole word, so "sex" does not block "Sussex"; longer words also count inside other words, so "casino" blocks "onlinecasinos". A phrase counts when its words appear in that order. Hebrew works too.'),ta,
  h('div',{style:'margin-top:8px'},btn('Save words','',async function(){c.blockedWords=ta.value.split(/\n+/).map(function(x){return x.trim()}).filter(Boolean);try{await saveConfigFor(dv,'Saved. Applies at the next check-in.')}catch(e){snack(e.message,1)}render()})));
 card.append(h('div',{class:'mute small',style:'margin-top:10px'},dv.isWindows?'On this computer: any website whose name contains one of these is blocked, in every browser and program.':dv.isBrowser?'Applies to this browser.':'In our own browsers: site names, pages and searches. In Chrome and every other app: site names (through the Agent\'s site filter). Words typed into a search in Chrome are only checked when Inspection is on.'));
 m.append(card)}

/* ---------- templates: one phone's settings, saved under a name, applied to others ---------- */
function renderTemplateCard(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'Templates'),h('div',{class:'mute'},'Save everything set up for this phone (apps, filters, screen time, Chrome, safe search, menus...) under a name, then apply it to other phones in one go. Reset-protection accounts are never copied.'));
 const name=h('input',{type:'text',placeholder:'Template name, e.g. Teen phone',style:'flex:1;min-width:180px'});
 card.append(h('div',{class:'row',style:'margin-top:10px'},name,btn('Save as template','',async function(){
  if(!name.value.trim()){snack('Give it a name first.',1);return}
  await call('POST','/api/templates',{name:name.value.trim(),deviceId:dv.id});snack('Saved. Apply it to other phones from the Templates page.');name.value=''})));
 const pick=h('select',{style:'flex:1;min-width:180px'},new Option('Loading templates…',''));
 const apply=btn('Apply to this phone','tonal',async function(){
  if(!pick.value){snack('Choose a template first.',1);return}
  if(!confirm('Replace this phone\'s settings with the template "'+pick.options[pick.selectedIndex].text+'"? Its current settings are overwritten.'))return;
  await call('POST','/api/templates/'+pick.value+'/apply',{deviceIds:[dv.id]});snack('Applied. The phone picks it up within about 5 minutes.');load()});
 card.append(h('div',{class:'row',style:'margin-top:10px'},pick,apply));
 call('GET','/api/templates').then(function(list){pick.textContent='';
  if(!list.length){pick.append(new Option('No templates yet',''));return}
  pick.append(new Option('Choose a template…',''));for(const t of list)pick.append(new Option(t.name,t.id))}).catch(function(){pick.textContent='';pick.append(new Option('Could not load templates',''))});
 m.append(card)}
/* ---------- Settings: other parents ---------- */
function renderHelpers(m){
 const card=h('div',{class:'card'},h('h2',null,'Other parents'),h('div',{class:'mute'},'Give another parent their own login to the same phones. "Full" can change everything you can, except two-step login and other logins. "View only" can look but not change anything. Every change is recorded in Activity with who made it.'));
 const box=h('div',{style:'margin-top:10px'},h('div',{class:'mute'},'Loading…'));card.append(box);
 const draw=function(){call('GET','/api/helpers').then(function(list){box.textContent='';
  if(!list.length)box.append(h('div',{class:'mute small'},'No other logins yet.'));
  for(const x of list)box.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},x.name+' · login name '+x.id),h('span',{class:'chip'},x.viewOnly?'View only':'Full'),btn('Remove','danger',async function(){if(!confirm('Remove the login "'+x.id+'"? They are signed out at once.'))return;await call('DELETE','/api/helpers/'+x.id);draw()})));
 }).catch(function(e){box.textContent='';box.append(h('div',{class:'mute'},e.message))})};
 const name=h('input',{type:'text',placeholder:'Name, e.g. Sarah',style:'flex:1;min-width:120px'}),login=h('input',{type:'text',placeholder:'Login name',style:'flex:1;min-width:120px'}),pw=h('input',{type:'password',placeholder:'Password (8 or more)',style:'flex:1;min-width:140px'});
 const vo=h('input',{type:'checkbox'});
 card.append(h('div',{class:'row',style:'margin-top:12px;flex-wrap:wrap'},name,login,pw,h('label',{class:'row',style:'cursor:pointer'},vo,'View only'),btn('Add login','',async function(){
  try{await call('POST','/api/helpers',{name:name.value.trim(),loginName:login.value.trim().toLowerCase(),password:pw.value,viewOnly:vo.checked});snack('Added. They sign in at this same address with their login name and password.');name.value='';login.value='';pw.value='';draw()}catch(e){snack(e.message,1)}})));
 draw();m.append(card)}

function renderTemplates(m){
 const box=h('div',null,h('div',{class:'card'},h('div',{class:'mute'},'Loading…')));m.append(box);
 call('GET','/api/templates').then(function(list){
  box.textContent='';
  if(!list.length){box.append(h('div',{class:'card empty'},h('h2',null,'No templates yet'),h('p',{class:'mute'},'Set up one phone the way you want, then on that phone\'s page open Device → Controls → Templates → "Save as template".')));return}
  for(const t of list){
   const card=h('div',{class:'card'},h('div',{class:'row'},h('h2',{class:'grow'},t.name),h('span',{class:'mute small'},'from '+(t.from||'a phone')+' · '+ago(t.createdAt))));
   card.append(h('div',{class:'mute'},t.summary&&t.summary.length?'Differs from a fresh phone in: '+t.summary.slice(0,12).join('; ')+(t.summary.length>12?'; and '+(t.summary.length-12)+' more':''):'Same as a fresh phone.'));
   const picks=h('div',{style:'margin-top:10px'});
   for(const d of devices){const cb=h('input',{type:'checkbox',value:d.id});picks.append(h('label',{class:'row',style:'cursor:pointer;margin:4px 0'},cb,d.name))}
   card.append(h('div',{style:'font-weight:500;margin-top:12px'},'Apply to'),picks,h('div',{class:'row',style:'margin-top:8px'},
    btn('Apply to the chosen phones','',async function(){
     const ids=[...picks.querySelectorAll('input:checked')].map(function(x){return x.value});
     if(!ids.length){snack('Choose at least one phone.',1);return}
     if(!confirm('Replace the settings of '+ids.length+' phone(s) with "'+t.name+'"?'))return;
     const r=await call('POST','/api/templates/'+t.id+'/apply',{deviceIds:ids});snack('Applied to '+r.applied+' phone(s). They pick it up within about 5 minutes.');load()}),
    btn('Delete template','danger',async function(){if(!confirm('Delete the template "'+t.name+'"? Phones it was applied to keep their settings.'))return;await call('DELETE','/api/templates/'+t.id);render()})));
   box.append(card)}
 }).catch(function(e){box.textContent='';box.append(h('div',{class:'card'},h('div',{class:'mute'},e.message)))})}

/** Chrome's own settings, locked by the Agent (no certificate needed). */
function renderChromeLock(m,dv){
 const cp=dv.config.chromePolicy=dv.config.chromePolicy||{};
 const save=async function(){try{await saveConfigFor(dv,'Saved. Chrome picks it up within about 5 minutes (the next time it starts).')}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'Chrome'),h('div',{class:'mute'},'Locks settings inside Chrome itself, the way companies manage Chrome on work phones. He cannot change them in Chrome. To check on the phone, open chrome://policy in Chrome.'));
 const row=function(k,title,text){card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title),h('div',{class:'mute'},text)),sw(cp[k],function(v){cp[k]=v;save()})))};
 row('noIncognito','No incognito tabs','Removes "New incognito tab", so everything opened in Chrome stays in its history.');
 row('adultFilter','Google\'s adult-site filter','Chrome checks each site with Google and blocks adult ones, on top of the filter level.');
 row('noSignin','No signing in to Chrome','Stops signing in to Chrome with a Google account (so no syncing in bookmarks and history from elsewhere).');
 const c=dv.config,auto=[];
 if(c.safeSearch)auto.push('SafeSearch');if(c.youtubeDns&&c.youtubeDns!=='off')auto.push('YouTube Restricted Mode');if(c.blockImages)auto.push('no pictures');
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},auto.length?'Also locked in Chrome from the settings above: '+auto.join(', ')+'. Hiding pictures this way needs no certificate.':'SafeSearch, YouTube Restricted Mode and "Block pictures" from the card above are also locked inside Chrome when you switch them on. Hiding pictures this way needs no certificate.'));
 m.append(card)}

/** Safe search, YouTube's own Restricted Mode, and hiding pictures. */
/** Add-ons: put in or take out YouTube, the Browser add-on and the Browser app. */
function renderAddons(m,dv){const c=dv.config;
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const has=function(p){return (dv.packages||[]).some(function(x){return x.p===p})};
 const cmd=async function(type,args,msg){try{await call('POST','/api/devices/'+dv.id+'/command',{type:type,args:args});snack(msg)}catch(e){snack(e.message,1)}load()};
 m.append(h('div',{class:'card'},h('h2',null,'Add-ons'),h('div',{class:'mute'},'Put in or take out the extras on this phone. Each works with the same rules as everything else here.')));
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'YouTube'),
  h('div',{class:'mute small'},'A YouTube icon on the phone, built into the MDM app. It follows Browser → YouTube (only what you allow, restricted, or off).')),
  sw(c.youtubeAddon,function(on){c.youtubeAddon=on;save(on?'YouTube goes on the phone at its next check-in.':'YouTube comes off the phone at its next check-in.')}))));
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Browser add-on'),
  h('div',{class:'mute small'},'A lighter browser built into the MDM app, with its own icon. Opens only allowed sites.')),
  sw(c.browserAddonEnabled,function(on){c.browserAddonEnabled=on;save(on?'Browser add-on goes on at the next check-in.':'Browser add-on comes off at the next check-in.')}))));
 const inst=has('com.familymdm.browser');
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Browser app'),
  h('div',{class:'mute small'},inst?'Installed. The full Browser (tabs, YouTube, Allow list), following this phone\'s rules by itself.':'Not on the phone. The full Browser (tabs, YouTube, Allow list).')),
  inst?btn('Take out','outline',function(){if(!confirm('Uninstall the Browser app from '+dv.name+'?'))return;cmd('uninstall',{packageName:'com.familymdm.browser'},'The Browser comes off at the next check-in.')})
      :btn('Put in','',function(){cmd('install',{url:'https://github.com/abegumbo-ui/MDM/releases/download/latest/mdm-browser.apk'},'The Browser goes on at the next check-in.')}))));}
/** SafeSearch on or off, on its own (on unless switched off). */
function renderSafeSearch(m,dv){const c=dv.config;if(dv.isBrowser||dv.isWindows)return;
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:600;font-size:17px'},'SafeSearch'),
  h('div',{class:'mute'},'Google, Bing and DuckDuckGo show filtered results only, in Chrome, every other browser and every app, on every filter level. On unless you switch it off here.')),
  sw(!c.safeSearchOff,async function(on){c.safeSearchOff=!on;c.safeSearch=on;try{await saveConfigFor(dv,on?'SafeSearch on.':'SafeSearch off.')}catch(e){snack(e.message,1)}render()}))))}
function renderSearchPictures(m,dv){
 const c=dv.config;
 const save=async function(msg){try{await saveConfigFor(dv,msg||'Saved. Applies at the next check-in.')}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'Safe search and pictures'));
 if(!dv.isBrowser){

  const yt=h('select',null,new Option('Off','off'),new Option('Moderate','moderate'),new Option('Strict','strict'));yt.value=c.youtubeDns||'off';
  yt.onchange=function(){c.youtubeDns=yt.value;save()};
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'YouTube Restricted Mode everywhere'),
   h('div',{class:'mute'},'Turns on YouTube\'s own Restricted Mode in every browser and in the YouTube app itself, and locks it. Strict hides more than Moderate, and both hide comments. This is YouTube\'s filter, so it is good but not perfect.')),yt));
 }
 card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block pictures on web pages'),
  h('div',{class:'mute'},dv.isBrowser?'Pages load without any pictures in this browser.':'Pages load without pictures in our own browsers and in Chrome. Other browsers (Firefox, Samsung Internet...) only with Inspection (the last tab) on'+(c.tlsInspection?', which it is.':', which it is not right now.'))),
  sw(c.blockImages,function(v){c.blockImages=v;save()})));
 if(!c.blockImages){
  const pg=h('select',null);for(const [v,l] of [['off','Show all pictures'],['nudity','Hide pictures that may show nudity'],['skin','Hide those, and cover skin in every picture']])pg.append(new Option(l,v));
  if((c.pictureGuard||'off')==='skin'){
   const wh=h('select',null);for(const [v,l] of [['all','Everyone'],['women','Women only'],['men','Men only']])wh.append(new Option(l,v));
   wh.value=c.skinWho||'all';wh.onchange=function(){c.skinWho=wh.value;save('Saved. From the next check-in.')};
   card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Cover skin of'),
    h('div',{class:'mute'},'Women only / men only: the phone tells men and women apart by their faces and bodies (NudeNet). When it cannot tell, it covers. Pictures only; videos are always covered for everyone.')),wh))}
  pg.value=c.pictureGuard||'off';pg.onchange=function(){c.pictureGuard=pg.value;save(pg.value==='off'?'All pictures shown.':'Saved. Pictures are checked on the phone from the next check-in.')};
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Pictures in our Browser'),
   h('div',{class:'mute'},'Every picture on a web page is checked on the phone before it shows. "Hide" greys out a picture that may show nudity. "Cover skin" also paints grey blocks over every skin-coloured patch (faces and hands too; it goes by colour). Works in our own Browser, and in Chrome, Samsung Internet and Edge when Inspection (the last tab) is on'+(c.tlsInspection?' (it is).':' (it is not right now).')+' Videos are not covered; pages with many pictures load a little slower.')),pg))}
 if(!dv.isBrowser){
  const ta=h('textarea',{rows:3,style:'width:100%;margin-top:8px',placeholder:'one site per line, e.g. maps.google.com'});ta.value=(c.imagesAllow||[]).join('\n');
  card.append(h('div',{style:'font-weight:500;margin-top:12px'},'Show pictures on these sites anyway'),
   h('div',{class:'mute'},'Applies in Chrome, and in other browsers with Inspection on. In our own browsers, pictures are all on or all off for now.'),ta,
   h('div',{style:'margin-top:8px'},btn('Save list','tonal',async function(){c.imagesAllow=ta.value.split(/\s+/).map(function(x){return x.trim().toLowerCase()}).filter(Boolean);await save('Saved.')})));
  card.append(h('div',{class:'mute small',style:'margin-top:10px'},'Safe search and YouTube Restricted Mode run through the Agent\'s site filter, so the phone shows a VPN (key) icon while either is on.'));
 }
 m.append(card)}

/* ---------- Screen time: what he used, and the limits ---------- */
function buildTimeGroup(m,dv){
 const sub=getSub('time');
 if(sub==='limits')renderTimeLimits(m,dv);else renderUsage(m,dv);
}
function fmtMin(n){n=Math.max(0,Math.round(n||0));const hh=Math.floor(n/60),mm=n%60;return hh?(hh+' h'+(mm?' '+mm+' min':'')):mm+' min'}
function dayName(key){const p=key.split('-');const d=new Date(+p[0],+p[1]-1,+p[2]);return d.toLocaleDateString([],{weekday:'long',month:'short',day:'numeric'})}
function usageRows(box,rows,max){
 for(const r of rows){
  const bar=h('div',{class:'bar',style:'width:'+Math.max(1,Math.round(100*r.m/(max||1)))+'%'});
  box.append(h('div',{class:'nm',title:r.title||r.name},r.name),h('div',{class:'track',title:r.name+': '+fmtMin(r.m)},bar),h('div',{class:'val'},fmtMin(r.m)+(r.extra||'')))}}
function renderUsage(m,dv){
 renderAppRequests(m,dv);
 const usage=dv.usage||{},days=Object.keys(usage).sort().reverse(),st=dv.config.screenTime||{};
 const send=function(minutes){return btn('Give '+minutes+' minutes','tonal',async function(){await call('POST','/api/devices/'+dv.id+'/command',{type:'addTime',args:{minutes:minutes}});snack('Extra time queued. It starts at the phone\'s next check-in (within about 5 minutes).');load()})};
 if(!days.length){
  m.append(h('div',{class:'card'},h('h2',null,'Screen time'),h('div',{class:'mute'},'Nothing reported yet. The phone sends its screen time with each check-in once it is on a build that has this feature.')));
 }else{
  const today=usage[days[0]],limit=st.dailyLimit||0;
  const card=h('div',{class:'card'},h('h2',null,dayName(days[0])),h('div',{class:'hero'},fmtMin(today.total)),
   h('div',{class:'mute'},limit?'of '+fmtMin(limit)+' allowed today':'on screen so far. No daily limit is set.'));
  if(limit)card.append(h('div',{class:'meter',title:fmtMin(today.total)+' of '+fmtMin(limit)},h('i',{style:'width:'+Math.min(100,Math.round(100*today.total/limit))+'%'})));
  if(today.ok===false)card.append(h('div',{class:'chip warn',style:'margin-top:10px'},'The Agent cannot see app usage on this phone'),
   h('div',{class:'mute small',style:'margin-top:6px'},'On the phone open MDM Agent → Administrator → Security → App access and turn on usage access. Until then limits cannot count anything.'));
  if(today.extraUntil&&today.extraUntil>Date.now())card.append(h('div',{class:'chip warn',style:'margin-top:10px'},'Extra time until '+new Date(today.extraUntil).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})));
  const apps=today.apps||[];
  if(apps.length){
   const box=h('div',{class:'usage'});
   usageRows(box,apps.map(function(a){const lim=(st.appLimits||{})[a.p];return{name:a.l||a.p,title:a.p,m:a.m,extra:lim?' of '+fmtMin(lim):''}}),apps[0].m);
   card.append(box)}
  else card.append(h('div',{class:'mute small',style:'margin-top:10px'},'No app has been used for a full minute yet today.'));
  m.append(card);
  if(days.length>1){
   const wk=h('div',{class:'card'},h('h2',null,'Earlier days'));
   const rows=days.slice(1,8).map(function(k){return{name:dayName(k),m:usage[k].total}});
   const box=h('div',{class:'usage'});usageRows(box,rows,Math.max.apply(null,rows.map(function(r){return r.m}).concat([1])));
   wk.append(box);m.append(wk)}
 }
 m.append(h('div',{class:'card'},h('h2',null,'Give extra time'),h('div',{class:'mute'},'Lifts bedtime and every time limit for that long, starting when the phone next checks in. Afterwards the limits apply again.'),
  h('div',{class:'row',style:'margin-top:8px'},send(15),send(30),send(60))))}
function renderTimeLimits(m,dv){
 const st=dv.config.screenTime=dv.config.screenTime||{dailyLimit:0,bedtime:null,appLimits:{},exempt:[]};
 st.appLimits=st.appLimits||{};st.exempt=st.exempt||[];
 const save=async function(msg){try{await saveConfigFor(dv,msg||'Saved. The phone applies it within about 5 minutes.')}catch(e){snack(e.message,1)}};
 const pick=function(value,options,onchange){const el=h('select');for(const o of options)el.append(new Option(o[1],String(o[0])));el.value=String(value);el.onchange=function(){onchange(parseInt(el.value,10))};return el};
 // daily limit
 const dl=h('div',{class:'card'},h('h2',null,'Daily limit'));
 dl.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Screen time allowed per day'),
  h('div',{class:'mute'},'When it is used up the phone shows a lock screen until midnight. Emergency calls still work, and he can ask you for more time from that screen.')),
  pick(st.dailyLimit||0,[[0,'No limit'],[30,'30 minutes'],[60,'1 hour'],[90,'1.5 hours'],[120,'2 hours'],[180,'3 hours'],[240,'4 hours'],[300,'5 hours'],[360,'6 hours'],[480,'8 hours']],function(v){st.dailyLimit=v;save().then(render)})));
 dl.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'A different limit on some days'),
  h('div',{class:'mute'},'For example more time on weekends. Choose the days below.')),
  pick(st.weekendLimit||0,[[0,'Same as every day'],[30,'30 minutes'],[60,'1 hour'],[90,'1.5 hours'],[120,'2 hours'],[180,'3 hours'],[240,'4 hours'],[300,'5 hours'],[360,'6 hours'],[480,'8 hours']],function(v){st.weekendLimit=v;save().then(render)})));
 if(st.weekendLimit){const wd=new Set(st.weekendDays||[0,6]);const row=h('div',{class:'days'});['S','M','T','W','T','F','S'].forEach(function(n,i){const b=h('button',{class:wd.has(i)?'on':''},n);b.onclick=function(){wd.has(i)?wd.delete(i):wd.add(i);st.weekendDays=[...wd].sort();save().then(render)};row.append(b)});dl.append(row)}
 m.append(dl);
 // school time
 const sch=st.schoolTime,son=!!sch;
 const sc=h('div',{class:'card'},h('h2',null,'School time'));
 sc.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Only the basics during school hours'),
  h('div',{class:'mute'},son?'From '+sch.from+' to '+sch.to+' on the days chosen below, only the phone, texts and the apps you mark as not counting (in Apps below) open.':'Off.')),
  sw(son,function(v){st.schoolTime=v?{days:[1,2,3,4,5],from:'08:00',to:'15:00'}:null;save().then(render)})));
 if(son){const sf=h('input',{type:'time',value:sch.from}),stt=h('input',{type:'time',value:sch.to});const chosenS=new Set(sch.days);const sdays=h('div',{class:'days'});
  ['S','M','T','W','T','F','S'].forEach(function(n,i){const b=h('button',{class:chosenS.has(i)?'on':''},n);b.onclick=function(){chosenS.has(i)?chosenS.delete(i):chosenS.add(i);b.className=chosenS.has(i)?'on':''};sdays.append(b)});
  sc.append(h('div',{class:'row',style:'margin-top:8px'},h('span',{class:'mute'},'From'),sf,h('span',{class:'mute'},'until'),stt),sdays,
   btn('Save school time','',function(){if(!sf.value||!stt.value||sf.value===stt.value||!chosenS.size){snack('Choose a start, an end and at least one day.',1);return}st.schoolTime={days:[...chosenS].sort(),from:sf.value,to:stt.value};save().then(render)}));
  const br=st.schoolBreak,bf=h('input',{type:'time',value:br?br.from:'12:00'}),bt=h('input',{type:'time',value:br?br.to:'12:45'});
  sc.append(h('div',{class:'setting',style:'margin-top:8px'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Break (lunch)'),h('div',{class:'mute small'},br?'The phone works normally from '+br.from+' to '+br.to+'.':'None.')),
   sw(!!br,function(v){st.schoolBreak=v?{from:'12:00',to:'12:45'}:null;save().then(render)})));
  if(br)sc.append(h('div',{class:'row'},h('span',{class:'mute'},'From'),bf,h('span',{class:'mute'},'until'),bt,btn('Save break','tonal',function(){if(!bf.value||!bt.value||bf.value>=bt.value){snack('The break has to end after it starts.',1);return}st.schoolBreak={from:bf.value,to:bt.value};save().then(render)})))}
 m.append(sc);
 // bedtime
 const bt=st.bedtime,on=!!bt;
 const bc=h('div',{class:'card'},h('h2',null,'Bedtime'));
 bc.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Lock the phone at night'),
  h('div',{class:'mute'},on?'Locked from '+bt.from+' to '+bt.to+' on the nights chosen below.':'Off. The phone can be used at any hour.')),
  sw(on,function(v){st.bedtime=v?{days:[0,1,2,3,4,5,6],from:'22:00',to:'07:00'}:null;save().then(render)})));
 if(on){
  const from=h('input',{type:'time',value:bt.from}),to=h('input',{type:'time',value:bt.to});
  const days=h('div',{class:'days'});const names=['S','M','T','W','T','F','S'],full=['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
  const chosen=new Set(bt.days);
  names.forEach(function(n,i){const b=h('button',{class:chosen.has(i)?'on':'',title:full[i]+' night'},n);b.onclick=function(){chosen.has(i)?chosen.delete(i):chosen.add(i);b.className=chosen.has(i)?'on':''};days.append(b)});
  bc.append(h('div',{class:'row',style:'margin-top:8px'},h('span',{class:'mute'},'From'),from,h('span',{class:'mute'},'until'),to),
   h('div',{class:'mute small',style:'margin-top:8px'},'Nights it applies. A night belongs to the day it starts on: "S" on the left is Sunday night into Monday morning.'),days,
   btn('Save bedtime','',async function(){
    if(!from.value||!to.value||from.value===to.value){snack('Choose two different times.',1);return}
    if(!chosen.size){snack('Choose at least one night, or switch bedtime off.',1);return}
    st.bedtime={days:[...chosen].sort(),from:from.value,to:to.value};await save();render()}))}
 m.append(bc);
 // per app
 const ac=h('div',{class:'card'},h('h2',null,'Apps'),h('div',{class:'mute'},'Give one app its own daily limit: when it is used up, that app disappears until midnight. Or mark an app as not counting toward the daily limit (the phone app, maps).'));
 const apps=dv.packages.filter(function(p){return p.p!==SELF_PACKAGE&&(!p.s||st.appLimits[p.p]||st.exempt.includes(p.p))}).sort(function(a,b){return (a.l||a.p).localeCompare(b.l||b.p)});
 if(!apps.length)ac.append(h('div',{class:'mute small',style:'margin-top:8px'},'No apps reported by the phone yet.'));
 for(const a of apps){
  const cb=h('input',{type:'checkbox'});cb.checked=st.exempt.includes(a.p);
  cb.onchange=function(){const set=new Set(st.exempt);cb.checked?set.add(a.p):set.delete(a.p);st.exempt=[...set];save()};
  ac.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},a.l||a.p),h('label',{class:'row small mute',style:'margin-top:2px;cursor:pointer'},cb,'Does not count toward the daily limit')),
   pick(st.appLimits[a.p]||0,[[0,'No limit'],[15,'15 min a day'],[30,'30 min a day'],[45,'45 min a day'],[60,'1 hour a day'],[90,'1.5 hours a day'],[120,'2 hours a day'],[180,'3 hours a day']],function(v){if(v)st.appLimits[a.p]=v;else delete st.appLimits[a.p];save()})))}
 m.append(ac);
 m.append(h('div',{class:'card'},h('h2',null,'Good to know'),h('div',{class:'mute'},'Time is counted on the phone itself, so limits keep working with no internet. The count needs Android\'s "usage access" for the Agent: it asks for it by itself, and the Usage tab tells you if a phone refused. A lock you send yourself from Security always wins over these.')))}

/* ---------- Browser group: everything about what this phone can open on the web, in one place ---------- */
function buildBrowserGroup(m,dv){
 const sub=getSub('browser');
 if(sub==='summary')renderBrowserSummary(m,dv);
 else if(sub==='youtube')renderYoutube(m,dv);
 else if(sub==='access'){renderSafeSearch(m,dv);renderWebAccess(m,dv);renderFilterSchedule(m,dv);renderSearchPictures(m,dv);renderNoPictureSites(m,dv);renderBlockAds(m,dv);renderAdsCovered(m,dv);renderImageSearch(m,dv);renderBlockedWords(m,dv)}
 else if(sub==='history')renderSitesVisited(m,dv);
 else if(sub==='sites')renderSites(m,dv,'sites');
 else if(sub==='apps'){renderInAppBrowsers(m,dv);renderSites(m,dv,'apps')}
 else if(sub==='inspect')renderInspection(m,dv);
}
function ytModeOf(dv){const y=dv.config.youtubeMode||'auto';return y==='channels'||y==='restricted'?y:((dv.config.browserFilter||'off')==='moderate'?'restricted':'channels')}
/** A plain-English picture of this phone's browsing, with a button to the tab that changes each line. */
function renderBrowserSummary(m,dv){
 const c=dv.config,level=c.browserFilter||'off',sites=Object.values(c.sites||{});
 const nCh=sites.filter(function(x){return x.type==='youtube'}).length,nSites=sites.length-nCh,nReq=(dv.siteRequests||[]).length;
 const card=h('div',{class:'card'},h('h2',null,'What this phone can open'),h('div',{class:'mute'},'A summary of the settings on the other tabs. Each line has a button to change it.'));
 const line=function(title,text,chipText,chipCls,sub){
  const go=h('button',{class:'btn outline'},'Change');go.onclick=function(){goTo('browser',sub)};
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title,' ',chipText?h('span',{class:'chip '+(chipCls||'')},chipText):null),h('div',{class:'mute'},text)),go))};
 line('Filter level',level==='off'?'Nothing opens in our browsers unless it is on the allowed list.':level==='moderate'?'The web is open, except adult, gambling, drugs, scam and piracy sites.':'The web is open, except adult, gambling, drugs, scam, piracy and social media sites.',
  level==='off'?'Approved sites only':level==='moderate'?'Moderate':'Strict',level==='off'?'ok':'warn','access');
 line('YouTube',ytModeOf(dv)==='restricted'?'He can search YouTube and watch. No home feed, Shorts, trending, suggestions or comments.':'Only the channels and videos on the allowed list open. No YouTube home page or search.',
  ytModeOf(dv)==='restricted'?'Restricted':'Allowed channels only','','access');
 line('Allowed sites',nSites+(nSites===1?' site':' sites')+' and '+nCh+(nCh===1?' YouTube channel':' YouTube channels')+' are always allowed.'+(nReq?' '+nReq+(nReq===1?' request is':' requests are')+' waiting for you.':''),
  nReq?nReq+' waiting':'',nReq?'warn':'','sites');
 line('Safe search',(c.safeSearch?'SafeSearch is forced on Google, Bing and DuckDuckGo in every browser and app.':'Not forced in Chrome and other browsers. Our own browser always forces it on Google.')+(c.youtubeDns&&c.youtubeDns!=='off'?' YouTube Restricted Mode ('+c.youtubeDns+') is forced everywhere, the YouTube app included.':''),
  c.safeSearch?'Forced':'Not forced',c.safeSearch?'ok':'','access');
 line('Pictures',c.blockImages?(c.tlsInspection?'Hidden on web pages in our browsers, in Chrome, and in other browsers.':'Hidden in our own browsers and in Chrome. Other browsers (Firefox, Samsung Internet...) still show them unless Inspection is on.'):'Shown as normal.',
  c.blockImages?'Hidden':'Shown','','access');
 line('Chrome and other browsers',level==='off'?(c.restrictBrowsing?'Web links open in our browser only. Chrome itself is not filtered: block it under Apps if it should not be used.':'Not filtered. Only our own browsers follow the allowed list. Block Chrome under Apps, or pick Moderate or Strict to filter it.'):'Filtered by site name through the Agent\'s site filter, with the same categories as above.',
  level==='off'?'Not filtered':'Filtered','','apps');
 line('Inspection (certificate)',c.tlsInspection?'On: the Agent can look inside pages in Chrome and other browsers, so Restricted YouTube applies there too.':'Off. Our own browsers do not need it. Only needed to apply page-level rules (Restricted YouTube) inside Chrome.',
  c.tlsInspection?'On':'Off',c.tlsInspection?'warn':'','inspect');
 m.append(card);
 const ev=(dv.events||[]).filter(function(e){return /filter|Browser|inspection|certificate|site/i.test(e.m||'')}).slice(-8).reverse();
 const log=h('div',{class:'card'},h('h2',null,'What the phone reported'),h('div',{class:'mute'},'The latest browser and filter lines from the phone\'s own log. Nothing here until a phone has checked in with these features.'));
 if(!ev.length)log.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing yet.'));
 for(const e of ev)log.append(h('div',{class:'act'},e.m+' · '+ago(e.at)));
 m.append(log)}

/* ---------- requests from the phone's own menu: the Play Store, or one blocked app ---------- */
function renderAppRequests(m,dv){
 const reqs=dv.appRequests||[];if(!reqs.length)return;
 const rc=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Requests from the phone ('+reqs.length+')'),h('div',{class:'mute'},'Asked for from the phone\'s own menu.'));
 for(const r of reqs){
  const dismiss=async function(){await call('DELETE','/api/devices/'+dv.id+'/app-requests?key='+encodeURIComponent(r.key));load()};
  const row=h('div',{class:'row',style:'margin-top:8px'});
  if(r.kind==='time'){
   const more=function(minutes){return btn('Give '+minutes+' minutes','',async function(){await call('POST','/api/devices/'+dv.id+'/command',{type:'addTime',args:{minutes:minutes}});snack('He gets '+minutes+' more minutes from the phone\'s next check-in.');await dismiss()})};
   row.append(more(15),more(30),more(60));
  }else if(r.kind==='store'){
   const open=function(label,minutes){return btn(label,'',async function(){await call('POST','/api/devices/'+dv.id+'/command',{type:'openStore',args:{minutes:minutes}});snack('The Play Store opens at the phone\'s next check-in.');await dismiss()})};
   row.append(open('Open for 1 hour',60),open('Open for 15 minutes',15),open('Open until I close it',0));
  }else{
   row.append(btn('Allow this app','',async function(){dv.config.apps[r.pkg]={mode:'allow',label:r.label||r.pkg};await saveConfigFor(dv,'Allowed. It appears on the phone within about 5 minutes.');await dismiss()}));
  }
  row.append(btn('Dismiss','outline',dismiss));
  rc.append(h('div',{class:'app'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},r.kind==='time'?'More screen time':r.kind==='store'?'Open the Google Play Store':'App: '+(r.label||r.pkg)),
   h('div',{class:'mute small'},(r.kind==='app'?r.pkg+' · ':'')+ago(r.at)),row)))}
 m.append(rc)}

/* ---------- whole groups of apps, one switch each ---------- */
function renderAppGroups(m,dv){
 const groups=state.appGroups||{};if(!Object.keys(groups).length)return;
 const card=h('div',{class:'card'},h('h2',null,'App groups'),h('div',{class:'mute'},'Block a whole kind of app with one switch. An app you set to Allow below stays allowed, so you can block the group and still leave one in.'));
 for(const g in groups){
  const on=(dv.config.blockAppGroups||[]).includes(g);
  const here=dv.packages.filter(function(p){return groups[g].packages.includes(p.p)||(groups[g].categories||[]).includes(p.c)}).map(function(p){return p.l||p.p});
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block '+groups[g].label.toLowerCase()),
   h('div',{class:'mute'},(here.length?'On this phone: '+here.join(', ')+'.':'None of them are installed on this phone right now. If one is installed later, it is blocked.')+((groups[g].categories||[]).length?' Also catches any app that tells Android it is one of these.':''))),
   sw(on,async function(v){
    const set=new Set(dv.config.blockAppGroups||[]);v?set.add(g):set.delete(g);dv.config.blockAppGroups=[...set];
    try{await saveConfigFor(dv,v?'Blocked. Applies within about 5 minutes.':'No longer blocked as a group.')}catch(e){snack(e.message,1)}render()})))}
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Messaging apps such as WhatsApp are in none of these groups. Games and video apps are recognised by the kind of app they tell Android they are, plus a list of well-known names; other groups go by the list only.'));
 m.append(card)}

/* ---------- the Play Store: blocked or not, open for a while, codes ---------- */
function renderStore(m,dv){
 const PKG='com.android.vending',blocked=(dv.config.apps[PKG]||{}).mode==='block',open=storeOpen(dv);
 const card=h('div',{class:'card'},h('div',{class:'row'},h('h2',{class:'grow'},'Google Play Store'),h('span',{class:'chip '+(open?'warn':blocked?'ok':'')},open?'Open right now':blocked?'Blocked':'Not blocked')),
  h('div',{class:'mute'},'Keep the Play Store closed, and open it only when you choose: for a set time from here, when he asks from the phone, or with a one-time code you give him.'));
 card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Keep the Play Store closed'),
  h('div',{class:'mute'},'Hides the Play Store on this phone. App updates you send from here still work.')),
  sw(blocked,async function(v){
   if(v)dv.config.apps[PKG]={mode:'block',label:'Google Play Store'};else delete dv.config.apps[PKG];
   try{await saveConfigFor(dv,v?'The Play Store closes within about 5 minutes.':'The Play Store is no longer blocked.')}catch(e){snack(e.message,1)}render()})));
 const send=function(label,type,args){return btn(label,'tonal',async function(){await call('POST','/api/devices/'+dv.id+'/command',{type:type,args:args||{}});snack(label+' queued. The phone does it at its next check-in (within about 5 minutes).');load()})};
 card.append(h('div',{style:'font-weight:500;margin-top:12px'},'Open it for a while'),
  h('div',{class:'row',style:'margin-top:8px'},send('15 minutes','openStore',{minutes:15}),send('1 hour','openStore',{minutes:60}),send('2 hours','openStore',{minutes:120}),send('Until I close it','openStore',{minutes:0}),send('Close now','closeStore')));
 const out=h('div',{style:'margin-top:10px'});
 const mins=h('select',null,new Option('15 minutes','15'),new Option('30 minutes','30'),new Option('1 hour','60'),new Option('2 hours','120'));mins.value='60';
 card.append(h('div',{style:'font-weight:500;margin-top:12px'},'One-time code'),h('div',{class:'mute'},'He taps "Open Google Play Store" on the phone and types it. Works once, on this phone only, within the next hour.'),
  h('div',{class:'row',style:'margin-top:8px'},mins,btn('Get a code','',async function(){
   const r=await call('POST','/api/codes',{type:'playstore',deviceId:dv.id,minutes:parseInt(mins.value,10)});
   out.textContent='';out.append(h('div',{class:'code'},r.code),h('div',{class:'mute small'},'Opens the Play Store for '+r.minutes+' minutes.'))})),out);
 card.append(h('div',{class:'mute small',style:'margin-top:10px'},'While it is open, the phone also lifts "block installing apps" if you had that on, and puts it back afterwards. With "approve new apps" on, anything he installs stays hidden until you approve it.'));
 m.append(card)}

/* ---------- which buttons the person sees on the phone's own menu ---------- */
function renderUserMenu(m,dv){
 const labels=state.userMenu||{};
 const card=h('div',{class:'card'},h('h2',null,'Phone menu'),h('div',{class:'mute'},'The buttons on the phone\'s own "User" screen (open the MDM Agent app, then User). Switch off anything he should not see.'));
 for(const k in labels){
  const on=!dv.config.userMenu||dv.config.userMenu[k]!==false;
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},labels[k]),sw(on,async function(v){
   dv.config.userMenu=dv.config.userMenu||{};dv.config.userMenu[k]=v;
   try{await saveConfigFor(dv,'Saved. The phone\'s menu changes within about 5 minutes.')}catch(e){snack(e.message,1)}})))}
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},'"Refresh" always shows. Requests he sends arrive on this phone\'s Overview and, if alerts are set up, on your own phone.'));
 m.append(card)}

/** Optional: the phone gets its own certificate and a local proxy, so browsers' secure pages can be filtered by their content. */
function renderInspection(m,dv){
 if(dv.isBrowser){m.append(h('div',{class:'card'},h('div',{class:'mute'},'This only applies to phones managed by the Agent app.')));return}
 const on=dv.config.tlsInspectionOff!==true;
 const card=h('div',{class:'card'},h('div',{class:'row'},h('h2',{class:'grow'},'Browser inspection (advanced)'),h('span',{class:'chip '+(on?'warn':'')},on?'On':'Off')),
  h('div',{class:'mute'},'Lets this phone filter what is on a web page, not just which site it is, in Chrome and other browsers (not only our own). Today that means Restricted YouTube everywhere: set it under Browser → Filter level, and it then also applies in Chrome.'));
 const row=h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Look inside browsers\' secure pages on this phone'),
  h('div',{class:'mute'},'On by default, so the filter can always look inside browsers\' secure pages (what it does with them is set under Filter level). The Agent gives this phone its own certificate and runs a small filter inside the Agent. While it is on the child cannot remove the certificate (Android then also stops them changing their own screen lock: set it from Security → Screen lock). Android shows a "network may be monitored" notice; apps cannot hide it.')),
  sw(on,async function(v){
   if(v&&!confirm('Turn on browser inspection for this phone?\n\n• The Agent installs a certificate of its own, made on this phone. Its key cannot be copied off the phone.\n• While on, the filter can see everything in browsers on this phone, including passwords typed on web pages, except sites on the skip list below.\n• Some sites and apps may stop working or show certificate warnings. Apps other than browsers are mostly not affected.\n• Turning it off removes the certificate again.\n\nThis is untested on real phones so far: try it on one phone first.')){render();return}
   dv.config.tlsInspection=v;dv.config.tlsInspectionOff=!v;
   try{await saveConfigFor(dv,v?'Saved. The Agent turns it on at its next check-in.':'Saved. The Agent turns it off and removes the certificate at its next check-in.')}catch(e){snack(e.message,1)}
   render()}));
 card.append(row);
 card.append(h('div',{class:'setting',style:'margin-top:10px'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Do not look inside Google (stops "are you a robot?")'),
  h('div',{class:'mute small'},'Through the filter Google can think the phone is a robot and keep asking. With this on, Google (search, Gmail, Drive) is left alone. SafeSearch is still forced and a bad search is still caught on the phone\'s screen. Switch it off if you need the Images tab blocked in Chrome.')),
  sw(dv.config.inspectSkipGoogle!==false,async function(v){dv.config.inspectSkipGoogle=v;try{await saveConfigFor(dv,v?'Google is left alone.':'Google is looked inside again.')}catch(e){snack(e.message,1)}render()})));
 const ta=h('textarea',{rows:4,style:'width:100%;margin-top:8px',placeholder:'one site per line, e.g. mybank.com'});
 ta.value=(dv.config.inspectBypass||[]).join('\n');
 card.append(h('div',{style:'font-weight:500;margin-top:12px'},'Never look inside these sites'),
  h('div',{class:'mute'},'Banks, payment sites and anything that stops working when inspected. Common bank and payment sites (Chase, Bank of America, Wells Fargo, PayPal, Venmo, Zelle, IRS and others) are already skipped; add more here.'),ta,
  h('div',{style:'margin-top:8px'},btn('Save list','tonal',async function(){
   dv.config.inspectBypass=ta.value.split(/\s+/).map(function(x){return x.trim().toLowerCase()}).filter(Boolean);
   await saveConfigFor(dv,'Saved. The Agent picks it up at its next check-in.');render()})));
 card.append(h('div',{class:'mute small',style:'margin-top:10px'},'Needs the phone to be managed by the Agent (device owner). Check Activity → Phone log for "Browser inspection turned on". If browsing ever fails on the phone, turn this off.'));
 m.append(card)}
function renderSitesEditor(m,dv){
 const top=h('div',{class:'card'},h('h2',null,'Allowed sites'),
  h('div',{class:'mute'},'This device\'s own list — other phones and browsers are unaffected. Anything not here shows a "Request access" prompt instead. A domain covers its subpages and subdomains; an exact page covers only that one link.'));
 const url=h('input',{type:'text',placeholder:'Site or link, e.g. khanacademy.org or https://example.com/page',class:'grow'});
 const type=h('select',null,new Option('Whole site','domain'),new Option('Exact page only','exact'));
 top.append(h('div',{class:'row',style:'margin-top:8px'},url,type,btn('Add','tonal',async function(){
  const link=url.value.trim();if(!link)return;
  const channel=ytChannelOf(link);
  if(channel){await addSite(dv,{type:'youtube',url:'https://www.youtube.com/'+channel,id:channel,label:'YouTube channel '+channel});url.value='';return}
  if(isYoutubeUrl(link)&&type.value==='domain'&&!confirm('This would allow ALL of YouTube, including its home page, search and every video. To allow only one channel, paste the channel\'s @handle link instead; to allow one video, paste the video link and choose "Exact page only". Allow all of YouTube anyway?'))return;
  await addSite(dv,{type:type.value,url:link,label:hostOfUrl(link)});url.value=''})),allowForPicker());
 top.append(h('div',{class:'mute small',style:'margin-top:8px'},'YouTube: paste a channel link like youtube.com/@channelname to allow that channel\'s videos only, or a video link (with "Exact page only") to allow just that video. Anything else on YouTube stays blocked.'));
 m.append(top);

 const list=h('div',{class:'card'});
 const sites=dv.config.sites||{};
 const keys=Object.keys(sites);
 if(!keys.length)list.append(h('div',{class:'mute'},'No sites allowed yet. Add one above, or approve a request.'));
 for(const key of keys){
  const s=sites[key];
  const row=h('div',{class:'app'});
  row.append(h('div',{class:'grow'},h('div',{style:'font-weight:500'},s.label||s.url),
   h('div',{class:'mute small',style:'word-break:break-all'},(s.type==='youtube'?'YouTube channel: ':s.type==='domain'?'Whole site: ':'Exact page: ')+s.url),
   h('div',null,h('span',{class:'chip'},s.type==='youtube'?'this channel\'s videos only':s.type==='domain'?'subpages + subdomains':'this page only'),
    s.until?h('span',{class:'chip warn'},untilText(s.until)):null,
    s.blockImages?h('span',{class:'chip warn'},'images blocked'):null,
    s.installable===false?h('span',{class:'chip'},'no home-screen shortcut'):null)));
  const col=h('div',{style:'display:flex;flex-direction:column;gap:6px;align-items:flex-end'});
  if(s.type!=='youtube')col.append(h('label',{class:'row small'},h('input',{type:'checkbox',checked:s.blockImages?true:false,onchange:async function(e){s.blockImages=e.target.checked;await saveConfigFor(dv,'Saved.')}}),'Block images'));
  if(s.type!=='youtube')col.append(h('label',{class:'row small'},h('input',{type:'checkbox',checked:s.installable===false?false:true,onchange:async function(e){s.installable=e.target.checked;await saveConfigFor(dv,'Saved.')}}),'Allow home-screen shortcut'));
  col.append(btn('Remove','danger',async function(){if(!confirm('Remove '+(s.label||s.url)+' from the allowlist?'))return;delete dv.config.sites[key];await saveConfigFor(dv,'Removed.')}));
  row.append(col);list.append(row)}
 m.append(list);
}
function renderSites(m,dv,part){
 if(part==='sites'){renderSitesPart(m,dv);return}
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Browser add-on'),
  h('div',{class:'mute'},'A lighter Browser built right into the agent app, its own icon, nothing extra installed. Opens only the sites on this same allowlist. The separate standalone Browser app below is untouched either way.')),
  sw(dv.config.browserAddonEnabled,async function(on){dv.config.browserAddonEnabled=on;try{await saveConfigFor(dv,on?'Browser add-on on. Its icon is on the phone now.':'Browser add-on off.')}catch(e){snack(e.message,1)}}))));
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Make this the only browser'),
  h('div',{class:'mute'},'Replaces Chrome (and any other browser) as the phone\'s handler for links, so every web link opens the agent\'s own browser instead — the one that only opens sites from the list below. You still need to Block Chrome itself on the App rules box so it can\'t be opened directly.')),
  sw(dv.config.restrictBrowsing,async function(on){dv.config.restrictBrowsing=on;try{await saveConfigFor(dv,on?'This is now the only browser. Phones apply it within about 5 minutes.':'Chrome and other browsers can be used again.')}catch(e){snack(e.message,1)}}))));
 if(part==='apps'){renderChromeLock(m,dv);return}
 renderSitesPart(m,dv);
}
function renderSitesPart(m,dv){
 renderRequestsCard(m,(dv.siteRequests||[]).map(function(r){return{path:'/api/devices/'+dv.id+'/site-requests',deviceName:dv.name,url:r.url,at:r.at,note:r.note}}),dv);
 renderCloneCard(m,dv);
 renderSitesEditor(m,dv);
}
/** YouTube: one choice for how it works, and the channels and videos that are allowed. */
const YT_CHOICES=[
 ['auto','Follow the web level','Moderate: restricted YouTube. Strict or Approved sites only: only the channels and videos below. Open: YouTube as it is.'],
 ['open','Open','YouTube as it is, with no limits from here.'],
 ['restricted','Restricted','Search and watch. No home feed, no Shorts, no suggestions or comments. YouTube\'s own filtering is not perfect.'],
 ['channels','Only what I allow','Only the channels and videos listed below. Everything else on YouTube is blocked.'],
 ['blocked','Off','YouTube does not open at all.']];
function renderYoutube(m,dv){
 const c=dv.config,cur=c.youtubeMode||'auto';
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('h2',null,'YouTube'));
 const seg=h('div',{class:'seg'});
 for(const [v,label] of YT_CHOICES){const x=h('button',{class:v===cur?'on':''},label);x.onclick=function(){c.youtubeMode=v;save('YouTube: '+label+'.')};seg.append(x)}
 card.append(seg,h('div',{class:'mute',style:'margin-top:10px'},(YT_CHOICES.find(function(x){return x[0]===cur})||YT_CHOICES[0])[2]));
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},dv.isBrowser?'In this Browser.':'In our Browser, and in Chrome, Samsung Internet and Edge when Inspection is on. The YouTube app itself is not affected: block it under Apps if needed.'));
 m.append(card);
 m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Let them search YouTube and ask for videos'),
  h('div',{class:'mute'},'With "Only what I allow": YouTube search works. Videos that are not allowed show only their title, no picture, with an "Ask for access" button that sends you a request. Off: search is blocked.')),
  sw(!c.youtubeSearchOff,function(on){c.youtubeSearchOff=!on;save(on?'They can search YouTube and ask for videos.':'YouTube search is blocked.')})),
  c.youtubeSearchOff?null:(function(){const seg=h('div',{class:'seg',style:'margin-top:10px'});
   for(const [v,l] of [['hide','Hide their pictures'],['skin','Show pictures with skin covered']]){const b=h('button',{type:'button',class:(c.youtubeSearchPictures||'hide')===v?'on':''},l);
    b.onclick=function(){c.youtubeSearchPictures=v;save(v==='skin'?'Search pictures show with skin covered.':'Search pictures are hidden.')};seg.append(b)}
   return h('div',null,h('div',{class:'mute small',style:'margin-top:10px'},'Pictures of the videos in search results that are not allowed:'),seg)})()));
 if(!dv.isWindows)m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Allow signing in to YouTube'),
  h('div',{class:'mute'},'Off by default: the Sign in button leads to "This part of YouTube is not allowed". Turn it on only when the child may sign in with their account (in our Browser and the YouTube icon).')),
  sw(c.youtubeSignIn===true,function(on){c.youtubeSignIn=on;save(on?'Signing in to YouTube is allowed.':'Signing in to YouTube is not allowed.')}))));
 if(!dv.isBrowser&&!dv.isWindows)m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'YouTube app on the phone'),
  h('div',{class:'mute'},'A YouTube icon on the phone, built into the MDM app, so YouTube works even when browsers are blocked. It follows everything on this page: the choice above, your channels and videos, ad blocking and picture covering. When YouTube is Off it shows your message below.')),
  sw(c.youtubeAddon,function(on){c.youtubeAddon=on;save(on?'The YouTube icon appears on the phone at its next check-in.':'The YouTube icon is removed at the next check-in.')}))));
 const msg=h('input',{type:'text',class:'grow',maxlength:'200',placeholder:'Sorry, YouTube is not available right now. Ask your administrator to open it.',value:c.youtubeOffMessage||''});
 m.append(h('div',{class:'card'},h('h2',null,'When YouTube is off'),h('div',{class:'mute'},'What the child sees instead of YouTube. Leave empty for the standard message.'),
  h('div',{class:'row',style:'margin-top:8px'},msg,btn('Save','tonal',function(){c.youtubeOffMessage=msg.value.trim();save('Saved.')}))));
 renderYoutubeSearch(m,dv);
 const sites=c.sites||{},keys=Object.keys(sites).filter(function(k){const s=sites[k];return s.type==='youtube'||isYoutubeUrl(s.url||'')});
 const lc=h('div',{class:'card'},h('h2',null,'Allowed channels and videos ('+keys.length+')'));
 lc.append(h('div',{class:'mute'},'Paste a channel link (youtube.com/@name), type @name, or paste a video link. Used when YouTube is on "Only what I allow" (or follows the level on Strict or Approved sites only).'));
 const inp=h('input',{type:'text',placeholder:'youtube.com/@channel, @channel or a video link',class:'grow'});
 const add=async function(){let v=inp.value.trim();if(!v)return;if(/^@[A-Za-z0-9._-]{3,30}$/.test(v))v='https://www.youtube.com/'+v;
  const ch=ytChannelOf(v);
  if(ch){await addSite(dv,{type:'youtube',url:'https://www.youtube.com/'+ch,id:ch,label:'YouTube channel '+ch});return}
  if(isYoutubeUrl(v)&&/[?&]v=|youtu\.be\//.test(v)){await addSite(dv,{type:'exact',url:v.includes('://')?v:'https://'+v,label:'YouTube video'});return}
  snack('That is not a YouTube channel or video link.',1)};
 lc.append(h('div',{class:'row',style:'margin-top:8px'},inp,btn('Add','tonal',add)),allowForPicker());
 for(const k of keys){const s=sites[k];
  lc.append(h('div',{class:'row',style:'margin:8px 0'},s.thumb?h('img',{src:s.thumb,alt:'',loading:'lazy',style:s.type==='youtube'?'width:40px;height:40px;border-radius:50%;object-fit:cover':'width:72px;height:40px;border-radius:6px;object-fit:cover'}):null,
   h('div',{class:'grow'},h('div',{style:'font-weight:500'},s.type==='youtube'?((s.label||'').replace(/^YouTube channel /,'')||s.id):(s.label&&s.label!=='YouTube video'?s.label:'Video')),h('div',{class:'mute small',style:'word-break:break-all'},(s.type==='youtube'?s.id:s.url)+(s.until?' · '+untilText(s.until):''))),
   btn('Remove','outline',async function(){delete c.sites[k];await save('Removed.')})))}
 if(!keys.length)lc.append(h('div',{class:'mute small',style:'margin-top:8px'},'None yet.'));
 m.append(lc)}
/** Find a YouTube channel or video by name and allow it with one tap. */
let ytResults=null,ytQuery='',ytKind='channel';
/** From a request: open this device's YouTube section and search for what the child asked for. */
async function findOnYoutube(dv,text){
 ytQuery=text;ytKind='channel';ytResults='loading';
 if(dv.isBrowser){browserSection='youtube';try{sessionStorage.setItem('browserSection','youtube')}catch(e){}render()}else goTo('browser','youtube');
 try{ytResults=(await call('GET','/api/youtube/search?type=channel&q='+encodeURIComponent(text))).results}catch(e){ytResults=[]}render()}
function renderYoutubeSearch(m,dv){
 const card=h('div',{class:'card'},h('h2',null,'Find on YouTube'));
 const inp=h('input',{type:'text',placeholder:'Search YouTube, e.g. a channel or a topic',class:'grow',value:ytQuery});
 const go=async function(kind){const q=inp.value.trim();if(!q)return;ytQuery=q;ytKind=kind;ytResults='loading';render();
  try{ytResults=(await call('GET','/api/youtube/search?type='+kind+'&q='+encodeURIComponent(q))).results}catch(e){ytResults=[]}render()};
 inp.onkeydown=function(e){if(e.key==='Enter')go(ytKind)};
 card.append(h('div',{class:'row',style:'margin-top:8px;flex-wrap:wrap'},inp,btn('Channels',ytKind==='channel'?'':'tonal',function(){go('channel')}),btn('Videos',ytKind==='video'?'':'tonal',function(){go('video')})),allowForPicker());
 const have=new Set(Object.values(dv.config.sites||{}).map(function(s){return s.type==='youtube'?s.id:((/[?&]v=([A-Za-z0-9_-]{11})/.exec(s.url||'')||[])[1]||'')}));
 const allowCh=async function(r){if(!confirm('Allow the channel "'+r.title+'" ('+r.handle+')? Its videos will open.'))return;
  await addSite(dv,{type:'youtube',url:'https://www.youtube.com/'+r.handle,id:r.handle,label:r.title,thumb:r.thumb})};
 if(ytResults==='loading')card.append(h('div',{class:'mute',style:'margin-top:10px'},'Searching…'));
 else if(Array.isArray(ytResults)){
  if(!ytResults.length)card.append(h('div',{class:'mute',style:'margin-top:10px'},'Nothing found. You can also paste a link below.'));
  for(const r of ytResults){
   if(r.type==='channel'){const on=have.has(r.handle);
    card.append(h('div',{class:'row',style:'margin:10px 0'},h('img',{src:r.thumb,alt:'',loading:'lazy',style:'width:48px;height:48px;border-radius:50%;object-fit:cover'}),
     h('div',{class:'grow'},h('div',{style:'font-weight:500'},r.title),h('div',{class:'mute small'},r.handle+(r.subs?' · '+r.subs:''))),
     on?h('span',{class:'chip'},'Allowed'):btn('Allow','',function(){allowCh(r)})))}
   else{const on=have.has(r.id),chOn=r.handle&&have.has(r.handle);
    card.append(h('div',{class:'row',style:'margin:10px 0;align-items:flex-start'},h('img',{src:r.thumb,alt:'',loading:'lazy',style:'width:120px;height:68px;border-radius:8px;object-fit:cover'}),
     h('div',{class:'grow'},h('div',{style:'font-weight:500'},r.title),h('div',{class:'mute small'},[r.channel,r.length,r.views].filter(Boolean).join(' · ')),
      h('div',{class:'row',style:'margin-top:6px;flex-wrap:wrap'},
       on?h('span',{class:'chip'},'Video allowed'):btn('Allow this video','',async function(){if(!confirm('Allow the video "'+r.title+'"?'))return;
        await addSite(dv,{type:'exact',url:'https://www.youtube.com/watch?v='+r.id,label:r.title,thumb:r.thumb})}),
       r.handle?(chOn?h('span',{class:'chip'},'Channel allowed'):btn('Allow the whole channel','outline',function(){allowCh({handle:r.handle,title:r.channel,thumb:''})})):null))))}}}
 m.append(card)}
/** How long an allow lasts. Back to "Always" after each one, so a later allow is not short by mistake. */
let allowFor='always',customD=0,customH=1,customM=0;
const ALLOW_FOR=[['1h','1 hour'],['2h','2 hours'],['today','Rest of today'],['custom','Custom'],['always','Always']];
/** The end time for the chosen length: 0 = always, -1 = a custom length of nothing. */
function allowUntil(){const n=Date.now();if(allowFor==='1h')return n+3600e3;if(allowFor==='2h')return n+2*3600e3;
 if(allowFor==='today'){const d=new Date();d.setHours(24,0,0,0);return d.getTime()}
 if(allowFor==='custom'){const ms=((customD||0)*1440+(customH||0)*60+(customM||0))*60e3;return ms>0?n+ms:-1}
 return 0}
function repaintAllowFor(){document.querySelectorAll('.allow-for').forEach(function(x){if(x.__paint)x.__paint()})}
function allowForPicker(){const seg=h('div',{class:'seg'});
 const inputs=[];
 const num=function(get,set,max,label){const i=h('input',{type:'number',min:'0',max:String(max),inputmode:'numeric',style:'width:72px'});
  i.oninput=function(){let v=Math.floor(Number(i.value));if(!(v>=0))v=0;if(v>max)v=max;set(v);repaintAllowFor()};inputs.push([i,get]);
  return h('label',{class:'row small',style:'gap:6px;align-items:center'},i,label)};
 const custom=h('div',{class:'row',style:'margin-top:8px;flex-wrap:wrap;gap:14px'},
  num(function(){return customD},function(v){customD=v},365,'days'),num(function(){return customH},function(v){customH=v},23,'hours'),num(function(){return customM},function(v){customM=v},59,'minutes'));
 const until=h('div',{class:'mute small',style:'margin-top:6px'});
 const paint=function(){[...seg.children].forEach(function(b,i){b.className=ALLOW_FOR[i][0]===allowFor?'on':''});
  custom.style.display=allowFor==='custom'?'':'none';
  for(const [i,get] of inputs)if(document.activeElement!==i)i.value=String(get());
  const u=allowUntil();until.textContent=u>0?'Allowed '+untilText(u)+', then blocked again by itself.':u<0?'Choose how long: days can be 0, with some hours or minutes.':'Allowed with no end.'};
 for(const [v,label] of ALLOW_FOR){const b=h('button',{type:'button'},label);b.onclick=function(){allowFor=v;repaintAllowFor()};seg.append(b)}
 const wrap=h('div',{class:'allow-for',style:'margin-top:10px'},h('div',{class:'mute small',style:'margin-bottom:6px'},'How long to allow it'),seg,custom,until);
 wrap.__paint=paint;paint();return wrap}
function untilText(t){const d=new Date(t),now=new Date(),time=d.toLocaleTimeString([],{hour:'numeric',minute:'2-digit'});
 return 'until '+(d.toDateString()===now.toDateString()?time:d.toLocaleDateString([],{weekday:'short',month:'short',day:'numeric'})+' '+time)}
async function addSite(dv,entry){
 const until=allowUntil();if(until<0){snack('Choose how long to allow it (at least one minute).',1);return}allowFor='always';
 const key=entry.type==='youtube'?'youtube:'+entry.id:(entry.type)+':'+hostOfUrl(entry.url)+(entry.type==='exact'?':'+Date.now():'');
 dv.config.sites=dv.config.sites||{};
 dv.config.sites[key]=entry.type==='youtube'?{type:'youtube',kind:'channel',id:entry.id,url:entry.url,label:entry.label,blockImages:false,installable:false,thumb:entry.thumb}:{type:entry.type,url:entry.url,label:entry.label,blockImages:false,installable:true,thumb:entry.thumb};
 if(until)dv.config.sites[key].until=until;
 await saveConfigFor(dv,'Added '+(entry.label||entry.url)+(until?', '+untilText(until):'')+'. Phones pick it up within about 5 minutes.');
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
const BROWSER_GROUPS=[
 {key:'overview',label:'Overview',icon:'home'},
 {key:'web',label:'Web',icon:'globe'},
 {key:'youtube',label:'YouTube',icon:'apps'},
 {key:'pictures',label:'Pictures & video',icon:'apps'},
 {key:'sites',label:'Allowed sites',icon:'shield'},
 {key:'settings',label:'Settings',icon:'device'}];
let browserSection=(function(){try{return sessionStorage.getItem('browserSection')||'overview'}catch(e){return 'overview'}})();
/** "At a glance": every rule in force on this Browser, one plain line each. */
function renderBrowserGlance(m,b){
 const c=b.config||{},fs=(b.info&&b.info.filter)||null,rows=[];
 const level=c.openWeb?'Open: no filter':{off:'Approved sites only',moderate:'Moderate filter',strict:'Strict filter'}[c.browserFilter||'off'];
 rows.push(['Web',level+(fs&&(fs.tier==='moderate'||fs.tier==='strict')?(fs.ready?' · lists ready':' · lists not ready yet'):'')]);
 rows.push(['Search',c.searchOff?'Off':'Google'+(c.safeSearchOff?'':', SafeSearch on')]);
 rows.push(['Adult sites',c.adultListOff?'Built-in list off':'Built-in list blocks them']);
 const pics=c.blockImages?'No pictures at all':{off:'All shown',nudity:'Possible nudity hidden',skin:'Skin covered ('+({all:'everyone',women:'women only',men:'men only'}[c.skinWho||'all'])+'), possible nudity hidden'}[c.pictureGuard||'off'];
 rows.push(['Pictures',pics+((c.noPictureSites||[]).length?' · no pictures on '+c.noPictureSites.length+' site'+(c.noPictureSites.length===1?'':'s'):'')]);
 rows.push(['Videos',(c.pictureGuard==='skin'&&!c.blockImages)?'Skin covered':'Shown']);
 rows.push(['YouTube',{auto:'Follows the level',channels:'Allowed channels only',restricted:'Restricted (search, no feeds)'}[c.youtubeMode||'auto']]);
 if((c.blockedWords||[]).length)rows.push(['Blocked words',String(c.blockedWords.length)]);
 if(c.blockImageSearch)rows.push(['Image search','Off']);
 rows.push(['Allowed sites',String(Object.keys(c.sites||{}).length)]);
 const card=h('div',{class:'card'},h('h2',null,'At a glance'));
 for(const [k,v] of rows)card.append(kv(k,v));
 const line=fs?'Last check-in '+ago(b.lastSeen)+'.':'The Browser has not reported yet (it needs the newest version, opened once).';
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},line));
 m.append(card)}
function renderBrowserDetail(m,b){
 m.append(h('div',{class:'pagehead'},h('div',{class:'avatar'},svgIcon('globe','currentColor')),h('div',{class:'grow'},h('h1',null,b.name),h('div',{class:'mute small'},'Browser · last check-in '+ago(b.lastSeen))),btn('Refresh','tonal',load)));
 const nReq=(b.siteRequests||[]).length;
 const sec=BROWSER_GROUPS.some(function(g){return g.key===browserSection})?browserSection:'overview';
 const side=h('nav',{class:'side'});
 for(const g of BROWSER_GROUPS){
  const x=h('button',{class:'nav'+(g.key===sec?' on':'')},svgIcon(g.icon,'currentColor'),g.label);
  const n=(g.key==='overview'||g.key==='sites')?nReq:0;if(n)x.append(h('span',{class:'badge'},String(n)));
  x.onclick=function(){browserSection=g.key;try{sessionStorage.setItem('browserSection',g.key)}catch(e){}render()};side.append(x)}
 const content=h('div',{class:'content'});
 const requests=function(){renderRequestsCard(content,(b.siteRequests||[]).map(function(r){return{path:'/api/browsers/'+b.id+'/site-requests',deviceName:b.name,url:r.url,at:r.at,note:r.note}}),b)};
 if(sec==='overview'){renderBrowserGlance(content,b);requests();renderBrowserSitesOpened(content,b)}
 else if(sec==='web'){renderWebAccess(content,b);renderBrowserSwitches(content,b);renderBlockAds(content,b);renderAdsCovered(content,b);renderBlockedWords(content,b);renderImageSearch(content,b)}
 else if(sec==='youtube')renderYoutube(content,b);
 else if(sec==='pictures'){renderSearchPictures(content,b);renderNoPictureSites(content,b)}
 else if(sec==='sites'){requests();renderSitesEditor(content,b)}
 else{
  renderBrowserLook(content,b);
  const controls=h('div',{class:'card'},h('h2',null,'This Browser'));
  controls.append(h('div',{class:'row',style:'margin-top:8px'},
   btn('Rename','outline',async function(){const name=prompt('Name for this browser',b.name);if(!name)return;await call('PUT','/api/browsers/'+b.id,{name:name});load()}),
   btn('Disconnect','danger',async function(){if(!confirm('Disconnect '+b.name+'? It goes back to needing a code, and opens nothing until then.'))return;await call('DELETE','/api/browsers/'+b.id);location.hash='devices'})));
  content.append(controls);
  if(state.session.role==='admin'){
   const ownerCard=h('div',{class:'card'},h('h2',null,'Owner'),h('div',{class:'mute'},'Which client account (if any) this browser belongs to. They see only their own devices; the administrator sees all.'));
   const sel=h('select',{style:'width:100%;margin-top:8px'});
   sel.append(h('option',{value:''},'Admin only (no client)'));
   for(const c of clients)sel.append(h('option',{value:c.id},c.name+' ('+c.id+')'));
   sel.value=b.ownerId||'';
   sel.onchange=async function(){try{await call('PUT','/api/browsers/'+b.id+'/owner',{ownerId:sel.value||null});b.ownerId=sel.value||null;snack('Saved.');load()}catch(e){snack(e.message,1);sel.value=b.ownerId||''}};
   ownerCard.append(sel);content.append(ownerCard)}
  renderCloneCard(content,b)}
 m.append(h('div',{class:'shell'},side,content));
}

let winTab='filter';
// Same as EMAIL_SERVICES in src/index.js: what each email service needs to load and sign in.
const EMAIL_HOSTS={gmail:['mail.google.com','accounts.google.com','apis.google.com','ogs.google.com','contacts.google.com','gstatic.com','googleusercontent.com','googleapis.com'],
 outlook:['outlook.live.com','outlook.office.com','outlook.office365.com','login.live.com','account.live.com','login.microsoftonline.com','office.net','msauth.net','msftauth.net','live.net'],
 yahoo:['mail.yahoo.com','login.yahoo.com','yimg.com']};
function adminWarning(w){
 const a0=(w.info&&w.info.accounts)||null;if(!a0||!a0.admins)return null;const a={users:a0.users||[],admins:a0.admins.filter(function(x){return !(a0.users||[]).length||(a0.users||[]).indexOf(x)>=0})};if(!a.admins.length)return null; // switched-off accounts (like the built-in Administrator) do not count
 const others=a.users.filter(function(u){return a.admins.indexOf(u)<0});
 return h('div',{class:'card',style:'border-color:var(--warn)'},h('h2',null,'Administrator accounts on this computer'),
  h('div',{class:'mute'},'These Windows accounts are administrators: '+a.admins.join(', ')+'. Anyone using one of them can remove any filter, this one included. The child\'s account should be a Standard user: on the computer, Settings → Accounts → Other users → the account → Change account type → Standard User. Keep the administrator password to yourself.'),
  others.length?h('div',{class:'mute small',style:'margin-top:6px'},'Standard accounts: '+others.join(', ')+'.'):null)}
/** "See the computer's screen": one picture, asked for here; the child is told on the computer. */
function renderWinScreenshots(m,w){
 const card=h('div',{class:'card'},h('h2',null,'See the computer\'s screen'),
  h('div',{class:'mute'},'Takes one picture of whatever is on the computer\'s screen and shows it here. The computer then shows "A picture of this screen was sent to your parent". Pictures are kept for 7 days (the last 10). Nothing is taken while the screen is locked or nobody is signed in.'));
 card.append(h('div',{style:'margin-top:10px'},btn('Take a screenshot now','',async function(){
  try{await call('POST','/api/windevices/'+w.id+'/command',{type:'screenshot'});snack('Asked the computer. The picture shows up here in a minute or two (tap Refresh).')}catch(e){snack(e.message,1)}load()})));
 const why=(w.events||[]).slice().reverse().find(function(e){return /^Screenshot/.test(e.m||'')});
 if(why)card.append(h('div',{class:'mute small',style:'margin-top:8px;color:var(--warn)'},why.m+' ('+ago(why.at)+')'));
 const shots=(w.shots||[]).slice().reverse();
 const grid=h('div',{style:'display:flex;flex-wrap:wrap;gap:12px;margin-top:10px'});
 for(const s of shots){const src='/api/windevices/'+w.id+'/shots/'+s.id;
  grid.append(h('a',{href:src,target:'_blank',rel:'noopener',style:'display:block;width:200px;text-decoration:none;color:inherit'},h('img',{src:src,alt:'Screenshot',loading:'lazy',style:'width:200px;border-radius:10px;border:1px solid var(--outline);display:block'}),h('div',{class:'mute small'},ago(s.at))))}
 if(shots.length)card.append(grid);
 m.append(card)}
/** Screen time on a computer: daily limit and bedtime for the child's (standard) Windows accounts. */
function renderWinScreenTime(m,w){
 const win=w.win,st=(w.info&&w.info.screenTime)||null,today=new Date().toISOString().slice(0,10);
 const save=async function(msg){try{await saveConfigFor(w,msg)}catch(e){snack(e.message,1)}render()};
 const used=h('div',{class:'card'},h('h2',null,'Today'));
 used.append(h('div',{style:'font-size:28px;font-weight:600;margin:4px 0'},st&&st.day?(st.minutes+' min'):'Not reported yet'),
  h('div',{class:'mute small'},'Time a child\'s account was signed in with the screen unlocked, by the computer\'s own clock. Administrator accounts (yours) are never counted or limited.'));
 if(w.info&&w.info.screenTimeError)used.append(h('div',{class:'mute small',style:'color:var(--warn)'},'Problem on the computer: '+w.info.screenTimeError));
 m.append(used);
 const card=h('div',{class:'card'},h('h2',null,'Daily limit'));
 card.append(h('div',{class:'mute'},'When the time is up, the child gets a message 5 minutes before, then the computer goes back to the Windows sign-in screen. Nothing is closed. Signing in again works only for an administrator account until tomorrow.'));
 const row=h('div',{class:'row',style:'margin-top:8px;flex-wrap:wrap'});
 for(const [mins,label] of [[0,'No limit'],[60,'1 hour'],[90,'1½ hours'],[120,'2 hours'],[180,'3 hours'],[240,'4 hours']])
  row.append(btn(label,(win.screenLimit||0)===mins?'':'tonal',function(){win.screenLimit=mins;save(mins?'Limit: '+label+' a day.':'No daily limit.')}));
 card.append(row);
 if(win.screenLimit>0){
  const ex=h('div',{class:'row',style:'margin-top:10px;flex-wrap:wrap'},h('span',{class:'mute small'},'Extra time today:'));
  for(const mins of [15,30,60])ex.append(btn('+'+mins+' min','outline',function(){const same=win.extraAt&&new Date(win.extraAt).toDateString()===new Date().toDateString();win.extraMinutes=(same?win.extraMinutes||0:0)+mins;win.extraAt=Date.now();save('+'+mins+' minutes for today.')}));
  if(win.extraAt&&new Date(win.extraAt).toDateString()===new Date().toDateString()&&win.extraMinutes)ex.append(h('span',{class:'mute small'},'(given today: '+win.extraMinutes+' min)'));
  card.append(ex)}
 m.append(card);
 const bed=h('div',{class:'card'},h('h2',null,'Bedtime'));
 bed.append(h('div',{class:'mute'},'Between these times the child\'s account cannot be used (a warning comes 5 minutes before). Leave both empty for no bedtime.'));
 const from=h('input',{type:'time',value:win.bedFrom||''}),to=h('input',{type:'time',value:win.bedTo||''});
 bed.append(h('div',{class:'row',style:'margin-top:8px'},h('span',null,'From'),from,h('span',null,'to'),to,
  btn('Save','',function(){win.bedFrom=from.value||'';win.bedTo=to.value||'';save(win.bedFrom&&win.bedTo?'Bedtime '+win.bedFrom+'–'+win.bedTo+'.':'No bedtime.')}),
  btn('No bedtime','outline',function(){win.bedFrom='';win.bedTo='';save('No bedtime.')})));
 m.append(bed)}
/** "What's on for this computer": every rule in force, one plain line each. */
function renderWinWhatsOn(m,w){
 const c=w.config||{},win=w.win||{},now=Date.now(),rows=[];
 if(!w.filterOn)rows.push('🌐  Web filter: off');
 else rows.push('🌐  Web filter: '+({off:'approved sites only',moderate:'Moderate',strict:'Strict'}[c.browserFilter||'off'])+(c.safeSearch?', SafeSearch forced':'')+(c.youtubeDns&&c.youtubeDns!=='off'?', YouTube restricted':''));
 if((c.pauseUntil||0)>now)rows.push('⏸  Paused until '+hhmm(c.pauseUntil));
 if((c.internetOffUntil||0)>now)rows.push('📵  Internet off until '+hhmm(c.internetOffUntil));
 const fs2=c.filterSchedule;if(fs2&&fs2.on)rows.push('🕘  '+fs2.level+' from '+fs2.from+' to '+fs2.to);
 const rules=Object.values(win.programRules||{}),noNet=rules.filter(function(x){return x==='noInternet'}).length,blk=rules.filter(function(x){return x==='blocked'}).length+(win.blockedPrograms||[]).length;
 if(blk)rows.push('🚫  '+blk+' program'+(blk===1?'':'s')+' closed when started');
 if(noNet)rows.push('🔌  '+noNet+' program'+(noNet===1?'':'s')+' with no internet');
 if(win.blockOtherBrowsers!==false)rows.push('🧭  Other browsers closed (Chrome, Edge, Brave, Firefox stay)');
 if(win.blockVpnApps!==false)rows.push('🛡  VPN programs closed');
 if(w.enabled)rows.push('🔒  Only chosen programs may use the internet');
 rows.push(win.protectFromAdmins!==false?'🔐  Administrators cannot remove LockGuard':'🔓  Administrators can manage LockGuard');
 if(win.screenLimit>0)rows.push('⏳  Screen time: '+(win.screenLimit>=60?(win.screenLimit/60)+' h':win.screenLimit+' min')+' a day for the child\'s account');
 if(win.bedFrom&&win.bedTo)rows.push('🌙  Bedtime '+win.bedFrom+'–'+win.bedTo);
 if(win.unlockName&&win.unlockText)rows.push('🗝  Unlock file set'+(w.info&&w.info.unlocked?' and IN USE right now: everything is off':''));
 const card=h('div',{class:'card'},h('h2',null,"What's on for this computer"));
 for(const r of rows)card.append(h('div',{class:'act'},r));
 m.append(card)}
function renderWinDetail(m,w){
 w.isWindows=true;w.packages=w.packages||[];w.config=w.config||{};w.win=w.win||{};
 const dv=w,f=(w.info&&w.info.filter)||null;
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 m.append(h('div',{class:'pagehead'},h('div',{class:'avatar'},svgIcon('device','currentColor')),h('div',{class:'grow'},h('h1',null,w.name),h('div',{class:'mute small'},(w.info.hostname||'Windows computer')+' · last check-in '+ago(w.lastSeen))),btn('Refresh','tonal',load)));
 if(state.session&&state.session.viewOnly)m.append(h('div',{class:'card',style:'border-color:var(--warn)'},h('div',{class:'mute'},'This login can only look. Changes have to be made from a full login.')));
 const tabs=h('div',{class:'row',style:'margin:0 0 12px;flex-wrap:wrap'});
 for(const [k,l] of [['filter','Web filter'],['programs','Programs & browsers'],['time','Screen time'],['history','Sites visited'],['computer','Computer']])tabs.append(btn(l,winTab===k?'':'tonal',function(){winTab=k;render()}));
 m.append(tabs);
 if(winTab==='filter'){
  renderWinWhatsOn(m,w);
  if((w.siteRequests||[]).length){
   const rq=h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Asked for on the computer ('+w.siteRequests.length+')'),allowForPicker());
   for(const r of w.siteRequests.slice().reverse()){
    const host=hostOfUrl(r.url);
    rq.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},host||r.url),r.note?h('div',{style:'margin-top:2px'},'“'+r.note+'”'):null,h('div',{class:'mute small'},r.url+' · '+ago(r.at))),
     btn('Allow','',async function(){const until=allowUntil();if(until<0){snack('Choose how long to allow it (at least one minute).',1);return}allowFor='always';
      if(until)w.config.dnsAllowTimed=[...(w.config.dnsAllowTimed||[]).filter(function(t){return t.host!==host}),{host:host,until:until}];
      else w.config.dnsAllowlist=[...new Set([...(w.config.dnsAllowlist||[]),host])];
      await saveConfigFor(w,host+' allowed'+(until?' '+untilText(until):'')+'.');await call('DELETE','/api/windevices/'+w.id+'/site-requests?url='+encodeURIComponent(r.url));load()}),
     btn('Dismiss','outline',async function(){await call('DELETE','/api/windevices/'+w.id+'/site-requests?url='+encodeURIComponent(r.url));load()})))}
   m.append(rq)}
  let status='The filter is off: every website opens.';
  if(w.filterOn){
   if(!f)status='Waiting for the computer to check in (it needs the latest LockGuard).';
   else if(f.error)status='Problem on the computer: '+f.error;
   else if(!f.on)status='Waiting for the computer to switch the filter on (within a minute).';
   else if(!f.listsReady)status='On. Downloading the category lists (about 13 MB); until they are in, only essential sites open.';
   else status='On and working.';}
  const aw=adminWarning(w);if(aw)m.append(aw);
  m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Filter websites on this computer'),h('div',{class:'mute small'},status)),
   sw(w.filterOn,function(on){w.filterOn=on;save(on?'The filter switches on within a minute.':'The filter switches off within a minute; the computer\'s network settings go back to how they were.')}))));
  if(w.filterOn){
   const lv=w.config.browserFilter||'moderate',card=h('div',{class:'card'},h('h2',null,'Filter level'));
   for(const [v,l,d] of [['moderate','Moderate','Blocks adult sites, gambling, drugs and vaping, scams and phishing, and piracy.'],['strict','Strict','Everything in Moderate, plus social media (Facebook, Instagram, TikTok, X).'],['off','Approved sites only','Nothing opens except the allowed sites below (and the dashboard). Many programs that need the internet will stop working; Windows updates too.']]){
    const r=h('input',{type:'radio',name:'winlevel'});r.checked=lv===v;r.onchange=function(){w.config.browserFilter=v;save('Saved. Applies within a minute.')};
    card.append(h('label',{class:'setting',style:'cursor:pointer'},r,h('div',{class:'grow',style:'margin-left:10px'},h('div',{style:'font-weight:500'},l),h('div',{class:'mute small'},d))))}
   m.append(card);
   const em=h('div',{class:'card'},h('h2',null,'Email only'),h('div',{class:'mute'},'One tap: nothing opens except the email services ticked here (and the sites in "Always allowed" below). The browsers open on a page with a big button for each.'));
   const picks={};
   for(const [k,l] of [['gmail','Gmail'],['outlook','Outlook / Hotmail'],['yahoo','Yahoo Mail']]){const cb=h('input',{type:'checkbox'});cb.checked=k!=='yahoo';picks[k]=cb;em.append(h('label',{class:'row',style:'cursor:pointer;margin:4px 0'},cb,l))}
   em.append(h('div',{style:'margin-top:8px'},btn('Switch to email only','',function(){
    const chosen=Object.keys(picks).filter(function(k){return picks[k].checked});if(!chosen.length){snack('Tick at least one.',1);return}
    if(!confirm('Switch '+w.name+' to email only? Every other website stops opening.'))return;
    const set=new Set(w.config.dnsAllowlist||[]);for(const k of chosen)for(const hh of EMAIL_HOSTS[k])set.add(hh);
    w.config.dnsAllowlist=[...set];w.config.browserFilter='off';save('Switched to email only. Applies within a minute.')})));
   if(lv==='off')em.append(h('div',{class:'mute small',style:'margin-top:8px'},'Approved sites only is on now. To open the web again choose Moderate or Strict above.'));
   m.append(em);
   renderPause(m,dv);renderInternetOff(m,dv);renderFilterSchedule(m,dv);
   const yt=h('select',null,new Option('Off','off'),new Option('Moderate','moderate'),new Option('Strict','strict'));yt.value=w.config.youtubeDns||'off';yt.onchange=function(){w.config.youtubeDns=yt.value;save('Saved.')};
   m.append(h('div',{class:'card'},h('h2',null,'Safe search and pictures'),
    h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Force SafeSearch'),h('div',{class:'mute small'},'Google, Bing and DuckDuckGo show filtered results only, in every browser.')),sw(w.config.safeSearch,function(on){w.config.safeSearch=on;save('Saved.')})),
    h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'YouTube Restricted Mode'),h('div',{class:'mute small'},'In every browser and the YouTube app. Strict hides more than Moderate; both hide comments.')),yt),
    h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Block pictures on web pages'),h('div',{class:'mute small'},'In Chrome, Edge and Brave.')),sw(w.config.blockImages,function(on){w.config.blockImages=on;save('Saved.')}))));
   const al=h('textarea',{rows:4,style:'width:100%',placeholder:'one site per line, e.g. chabad.org'});al.value=(w.config.dnsAllowlist||[]).join('\n');
   const bl=h('textarea',{rows:4,style:'width:100%',placeholder:'one site per line, e.g. tiktok.com'});bl.value=(w.config.dnsBlocklist||[]).join('\n');
   const lines=function(t){return t.value.split(/\n+/).map(function(x){return x.trim().toLowerCase().replace(/^https?:\/\//,'').replace(/\/.*$/,'')}).filter(Boolean)};
   m.append(h('div',{class:'card'},h('h2',null,'Sites'),h('div',{style:'font-weight:500;margin-top:6px'},'Always allowed'),h('div',{class:'mute small'},'Open whatever the level. A site includes everything under it (chabad.org also allows www.chabad.org).'),al,
    h('div',{style:'font-weight:500;margin-top:12px'},'Always blocked'),bl,h('div',{style:'margin-top:8px'},btn('Save sites','',function(){w.config.dnsAllowlist=lines(al);w.config.dnsBlocklist=lines(bl);save('Saved.')}))));
   const timed=(w.config.dnsAllowTimed||[]).filter(function(t){return t.until>Date.now()});
   const tc=h('div',{class:'card'},h('h2',null,'Allowed for a while'),h('div',{class:'mute small'},'Each one is blocked again by itself when its time is up (within a minute).'));
   const tin=h('input',{type:'text',class:'grow',placeholder:'a site, e.g. khanacademy.org'});
   tc.append(h('div',{class:'row',style:'margin-top:8px'},tin,btn('Allow','tonal',function(){const host=tin.value.trim().toLowerCase().replace(/^https?:\/\//,'').replace(/\/.*$/,'');if(!host)return;
    const until=allowUntil();if(until<=0){snack(until<0?'Choose how long to allow it (at least one minute).':'For no end, use "Always allowed" above. Choose how long here.',1);return}allowFor='always';
    w.config.dnsAllowTimed=[...timed.filter(function(t){return t.host!==host}),{host:host,until:until}];save(host+' allowed '+untilText(until)+'.')})),allowForPicker());
   for(const t of timed)tc.append(h('div',{class:'row',style:'margin:6px 0'},h('span',{class:'grow'},t.host),h('span',{class:'chip warn'},untilText(t.until)),
    btn('End now','outline',function(){w.config.dnsAllowTimed=timed.filter(function(x){return x.host!==t.host});save(t.host+' is blocked again.')})));
   if(!timed.length)tc.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing right now.'));
   m.append(tc);
   renderBlockAds(m,dv);
   renderBlockedWords(m,dv);
  }
 }
 else if(winTab==='programs'){
  const row=function(get,set,title,desc){return h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},title),h('div',{class:'mute small'},desc)),sw(get(),function(on){set(on);save('Saved. Applies within a minute.')}))};
  m.append(h('div',{class:'card'},h('h2',null,'Browsers'),
   row(function(){return !!(w.config.chromePolicy||{}).noIncognito},function(on){w.config.chromePolicy=Object.assign({},w.config.chromePolicy,{noIncognito:on})},'No incognito or private windows','Chrome, Edge, Brave and Firefox. Also turns off guest windows.'),
   row(function(){return !!w.win.blockExtensions},function(on){w.win.blockExtensions=on},'No browser extensions','Chrome, Edge and Brave. Extensions already installed are switched off too (VPN extensions get around filters).'),
   row(function(){return w.win.blockOtherBrowsers!==false},function(on){w.win.blockOtherBrowsers=on},'Close other browsers','Tor, Opera, Vivaldi and other browsers the filter cannot lock are closed the moment they start. Chrome, Edge, Brave and Firefox stay.'),
   row(function(){return w.win.blockVpnApps!==false},function(on){w.win.blockVpnApps=on},'Close VPN and proxy programs','About 50 known VPN programs (NordVPN, ProtonVPN, Psiphon, Cloudflare WARP...) are closed the moment they start.'),
   h('div',{class:'mute small',style:'margin-top:8px'},'Check what Chrome received by typing chrome://policy in its address bar (edge://policy in Edge).')));
  // Every program installed on the computer, each with its own choice (like the phones' app list).
  const rules=w.win.programRules=w.win.programRules||{};
  const progs=(w.programs||[]).slice().sort(function(a,b){return a.name.localeCompare(b.name)});
  const pc=h('div',{class:'card'},h('h2',null,'Programs on this computer ('+progs.length+')'),
   h('div',{class:'mute'},'Choose for each program: Allowed, No internet (it opens but cannot go online), or Blocked (it is closed the moment it starts). Applies within a minute.'+(w.programsAt?' List from '+ago(w.programsAt)+'.':' The list appears after the computer checks in with the latest LockGuard.')));
  const pq=h('input',{type:'text',placeholder:'Search programs…',style:'width:100%;margin:8px 0'});pc.append(pq);
  const plist=h('div');pc.append(plist);
  const drawProgs=function(){plist.textContent='';const q=pq.value.toLowerCase();
   for(const p of progs){if(q&&!p.name.toLowerCase().includes(q)&&!p.path.toLowerCase().includes(q))continue;
    const key=p.path.toLowerCase();const sel=h('select',null,new Option('Allowed',''),new Option('No internet','noInternet'),new Option('Blocked','blocked'));sel.value=rules[key]||'';
    sel.onchange=function(){if(sel.value)rules[key]=sel.value;else delete rules[key];save(sel.value==='blocked'?p.name+' is closed whenever it starts.':sel.value==='noInternet'?p.name+' has no internet.':p.name+' is allowed.')};
    plist.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},p.name),h('div',{class:'mute small'},p.path)),sel))}};
  pq.oninput=drawProgs;drawProgs();
  m.append(pc);
  const bp=h('textarea',{rows:4,style:'width:100%',placeholder:'one program per line, e.g. steam.exe'});bp.value=(w.win.blockedPrograms||[]).join('\n');
  m.append(h('div',{class:'card'},h('h2',null,'Other programs to close (by file name)'),h('div',{class:'mute'},'For programs that are not in the list above (portable programs, games without a Start menu entry). Use the program\'s file name: find it in Task Manager → Details.'),bp,
   h('div',{style:'margin-top:8px'},btn('Save programs','',function(){w.win.blockedPrograms=bp.value.split(/\n+/).map(function(x){return x.trim().toLowerCase()}).filter(Boolean);save('Saved.')}))));
  const controls=h('div',{class:'card'},h('h2',null,'Internet only for chosen programs'),h('div',{class:'mute'},'Stricter than the web filter: every program except the ones listed below is cut off from the internet altogether (they still open). Leave off unless you need it.'));
  controls.append(h('div',{class:'setting'},h('div',{class:'grow'},'Only the programs below may use the internet'),sw(w.enabled,async function(on){
   await call('PUT','/api/windevices/'+w.id+'/config',{enabled:on});snack(on?'Applies within a minute.':'Turned off.');load()})));
  if(w.enabled!==w.reportedEnabled)controls.append(h('div',{class:'mute small',style:'margin-top:4px'},'⏳ Waiting for the computer to apply this.'));
  m.append(controls);
  const progCard=h('div',{class:'card'},h('h2',null,'Allowed programs'),
  h('div',{class:'mute'},'Everything else on this computer stays installed and opens normally — it just can\'t reach the internet. Full path to the .exe, e.g. C:\\Program Files\\Outlook\\outlook.exe'));
 const list=h('div',{style:'margin-top:8px'});
 (w.allowedPrograms||[]).forEach(function(p,i){
  list.append(h('div',{class:'row',style:'align-items:center'},h('div',{class:'grow mono small'},p),
   btn('Remove','outline',async function(){const next=w.allowedPrograms.filter(function(_,j){return j!==i});await call('PUT','/api/windevices/'+w.id+'/config',{allowedPrograms:next});load()})))});
 if(!(w.allowedPrograms||[]).length)list.append(h('div',{class:'mute small'},'Nothing allowed yet.'));
 progCard.append(list,h('div',{style:'margin-top:10px'},btn('Add a program','',async function(){
  const path=prompt('Full path to the program\'s .exe on that computer');if(!path)return;
  const next=[...(w.allowedPrograms||[]),path];await call('PUT','/api/windevices/'+w.id+'/config',{allowedPrograms:next});load()})));
 m.append(progCard);
 }
 else if(winTab==='time')renderWinScreenTime(m,dv);
 else if(winTab==='history')renderSitesVisited(m,dv);
 else{
  renderWinScreenshots(m,w);
  const pr=w.win.protectFromAdmins!==false;
  m.append(h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Protect LockGuard from administrators'),
   h('div',{class:'mute small'},'Even an administrator account on the computer cannot stop, change or uninstall LockGuard: only the code (in LockGuard Setup) or "Uninstall from this computer" below removes it. An expert could still get around it, as with any filter. '+(w.info.protectedFromAdmins?'Active on the computer.':w.info.protectError?'Problem on the computer: '+w.info.protectError:pr?'Waiting for the computer (it needs the latest LockGuard).':''))),
   sw(pr,function(on){w.win.protectFromAdmins=on;save(on?'Protection on within a minute.':'Protection off within a minute: administrators can manage LockGuard again.')}))));
  // A removal code for uninstalling on the computer itself, made here.
  const rc=h('div',{class:'card'},h('h2',null,'Removal code'),
   h('div',{class:'mute'},'To uninstall LockGuard on the computer itself (Windows Settings → Apps → LockGuard → Uninstall): it opens LockGuard, which asks for a code. Make one here; it works on this computer for 48 hours. The computer needs to have checked in once after you make it.'));
  const rcOut=h('div');
  rc.append(h('div',{style:'margin-top:8px'},btn('Make a removal code','tonal',async function(){
   if(!confirm('Make a code that removes LockGuard from '+w.name+'? Give it only to whoever should remove it.'))return;
   const r=await call('POST','/api/windevices/'+w.id+'/removal-code',{});rcOut.textContent='';
   rcOut.append(h('div',{class:'code',style:'margin-top:8px;font-size:22px;letter-spacing:2px'},r.code),h('div',{class:'mute small'},'Works until '+new Date(r.until).toLocaleString()+'. Shown only now.'))})),rcOut);
  m.append(rc);
  // The unlock file: everything off while a secret file is in Downloads.
  const un=h('div',{class:'card'},h('h2',null,'Unlock file'),
   h('div',{class:'mute'},'While a file with this name, holding this exact text, is in the Downloads folder on the computer, every restriction is off (the filter, blocked programs, everything). Delete the file and everything is back on within a few seconds. Keep the text secret: anyone with the file has a free computer.'));
  const unName=h('input',{type:'text',placeholder:'File name, e.g. lockguard-off.txt',value:w.win.unlockName||'',style:'width:100%;margin-top:8px'});
  const unText=h('input',{type:'text',placeholder:'Secret text inside the file',value:w.win.unlockText||'',style:'width:100%;margin-top:8px'});
  un.append(unName,unText,h('div',{class:'row',style:'margin-top:8px'},
   btn('Save','',function(){w.win.unlockName=unName.value.trim();w.win.unlockText=unText.value.trim();if(w.win.unlockName&&!/^[A-Za-z0-9 ._-]{3,60}$/.test(w.win.unlockName)){snack('Use 3 to 60 letters, numbers, spaces, dots, dashes or underscores for the file name.',1);return}save(w.win.unlockName&&w.win.unlockText?'Saved. The computer knows the file within a minute.':'Unlock file turned off.')}),
   btn('Turn off','outline',function(){w.win.unlockName='';w.win.unlockText='';unName.value='';unText.value='';save('Unlock file turned off.')})),
   w.info.unlocked?h('div',{style:'margin-top:8px;color:var(--warn);font-weight:500'},'The unlock file is on the computer right now: everything is off.'):null);
  m.append(un);
  const controls=h('div',{class:'card'},h('h2',null,'This computer'),kv('Name on the computer',w.info.hostname||'unknown'),kv('Last check-in',ago(w.lastSeen)),kv('LockGuard build',w.info.build?String(w.info.build):'not numbered (built on a computer)'));
  controls.append(h('div',{class:'row',style:'margin-top:12px;flex-wrap:wrap'},
   btn('Rename','outline',async function(){const name=prompt('Name for this computer',w.name);if(!name)return;await call('PUT','/api/windevices/'+w.id,{name:name});load()}),
   btn('Uninstall from this computer','danger',async function(){if(!confirm('Remove LockGuard from '+w.name+' entirely? It puts the network settings, firewall and browser settings back, next time it checks in.'))return;await call('POST','/api/windevices/'+w.id+'/command',{type:'uninstall'});snack('Queued.');load()}),
   btn('Remove from this list','outline',async function(){if(!confirm('Remove '+w.name+' from the dashboard? Only do this if it is already gone. Otherwise use "Uninstall from this computer" first.'))return;await call('DELETE','/api/windevices/'+w.id);load()})));
  m.append(controls);
  const ll=h('div',{class:'card'},h('h2',null,'Computer log'));
  if(!(w.events||[]).length)ll.append(h('div',{class:'mute small'},'Nothing logged yet.'));
  for(const e of (w.events||[]).slice().reverse())ll.append(h('div',{class:'act'},e.m+' · '+ago(e.at)));
  m.append(ll)}
}

/* ---------- codes ---------- */
const CODE_INFO={
 enroll:['With a computer instead (advanced)','Only if you set phones up with a computer and adb, or type a code into the MDM Agent app by hand. The QR code above is easier. Valid 1 hour, one use.'],
 browser:['Browser connect code','Connects a standalone Browser app directly to this dashboard — no agent, no device owner, nothing else installed. Until it\'s connected (or set up on its own with a master code), that Browser allows nothing at all. Valid 1 hour, one use.']};
function renderCodes(m){
 m.append(h('div',{class:'card',style:'border-color:var(--primary)'},h('h2',null,'Add a phone: step by step'),
  h('ol',{style:'margin:8px 0 0 18px;line-height:1.7'},
   h('li',null,'The phone must be new, or reset to factory settings (Settings → System → Reset → Erase all data). Back up anything on it first.'),
   h('li',null,'Turn it on. On the first "Welcome" screen, tap the same empty spot 6 times quickly. A camera opens to scan a QR code.'),
   h('li',null,'Below, under "Set up with a QR code", type your Wi-Fi name and password, then press "Generate enrollment QR".'),
   h('li',null,'Scan the QR code with the phone. It downloads MDM Agent and sets itself up. Just tap Next / Accept on the phone.'),
   h('li',null,'When it is done, the phone shows up on the Devices list (Refresh). Open it, go to Overview → Ready-made setups, and pick one.'),
   h('li',null,'Open Safety → Health check on the dashboard and do anything it shows in red (usually one switch on the phone).')),
  h('div',{class:'mute small',style:'margin-top:8px'},'The tapping step works on almost every Android phone from Android 7 on. If no camera opens, the phone may need its own Wi-Fi first: some phones ask for Wi-Fi before the scanner appears.')));
 for(const type of ['enroll','browser']){
  const card=h('div',{class:'card'},h('h2',null,CODE_INFO[type][0]),h('div',{class:'mute'},CODE_INFO[type][1]));
  const out=h('div');
  card.append(h('div',{style:'margin-top:10px'},btn('Generate new code','',async function(){
   const r=await call('POST','/api/codes',{type:type});out.textContent='';
   out.append(h('div',{class:'code'},r.code),h('div',{class:'mute small'},'Valid for 1 hour. Shown once; generate another any time.'));
   if(type==='enroll'){
    out.append(h('div',{class:'mute',style:'margin-top:8px'},'Advanced, only if you set phones up with a computer. On the computer, with the phone connected and USB debugging on:'),
     h('pre',{class:'cmd'},'adb install mdm-agent-enroll.apk\nadb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver\nadb shell am start -n com.familymdm.agent/.MainActivity --es code '+r.code),
     h('div',{class:'mute small'},'Get mdm-agent-enroll.apk from GitHub → Releases → "Latest agent build" (it\'s the same app with a couple of permissions it doesn\'t need yet left out, so it looks less suspicious to Play Protect on a first install — it switches itself to the full build right after enrolling). If this build has the dashboard address baked in, that\'s all you need to type — otherwise tap "Use a different dashboard" on the phone once and enter '+r.server+'. No Google account needed for either command.'),
     h('div',{class:'mute small',style:'margin-top:8px'},'Prefer no computer at all? Use the QR code below instead — scan it on a brand-new or freshly reset phone, nothing to type.'))}
   if(type==='browser'){
    out.append(h('div',{class:'mute',style:'margin-top:8px'},'In the Browser app (no agent needed), tap "Connect to a dashboard" and enter:'),
     h('pre',{class:'cmd'},'Dashboard address: '+r.server+'\nCode: '+r.code),
     h('div',{class:'mute small'},'Get mdm-browser.apk from GitHub → Releases → "Latest agent build", same place as the agent. It shows up on the Devices list once connected.'))}
  })),out);
  const later=card;
  if(type!=='enroll')m.append(card);
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
   m.append(qrCard,later)}}
 m.append(h('div',{class:'card'},h('div',{class:'mute small'},'Codes to install or remove apps, or to browse freely for a while, are made on each phone\'s own page, so they only work on that one phone.')))
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
  h('div',{class:'mute'},'Get told when something needs attention. Easiest: press "Get notifications on this device" on your own phone. You can also (or instead) paste a notification-service address below.'));
 const pushRow=h('div',{class:'row',style:'margin-top:10px'},btn('Get notifications on this device','',enablePush),btn('Stop them on this device','outline',disablePush));
 card.append(pushRow,h('div',{class:'mute small',style:'margin-top:6px'},'Android: works in Chrome; installing the dashboard to the home screen (browser menu → Add to Home screen) is best. iPhone: first tap Share → Add to Home Screen, open the dashboard from that new icon, then press the button. Each phone or computer signs up once.'),
  h('div',{class:'mute small',style:'margin-top:10px'},'Other ways, for advanced use: the free ntfy app (address https://ntfy.sh/your-private-topic) or a Discord, Slack or Teams webhook address.'));
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
  opt('tamper','Something security related: a phone restarted, its SIM card removed or changed, or a blocked attempt');
  opt('places','A phone arrived at or left a place you saved (Device → Location)');
  opt('messages','Worrying words in a child\'s chats or searches (phones with Safety → Message alerts on)');
  opt('apps','A new app was installed on a phone');
  if(a.pushCount)body.prepend(h('div',{class:'mute small',style:'margin-bottom:8px'},a.pushCount+(a.pushCount===1?' phone or computer gets':' phones or computers get')+' notifications.'));
  opt('weekly','Weekly report, Sunday evening: screen time, sites, calls, requests for each phone');
  opt('daily','A short summary of today, every evening at 8: screen time, blocked sites, worrying words, requests');
  card.append(h('div',{class:'mute small',style:'margin-top:8px'},'When someone taps "I need help" on a phone, you always get an alert.'));
 }).catch(function(e){body.textContent='';body.append(h('div',{class:'mute'},e.message))})}


/* ---------- tap to block: parts of apps picked on the phone ---------- */
/** Whole screens blocked with ✓ Block this screen: listed here, and removed here or on the phone. */
function renderBlockedScreens(m,dv){
 const c=dv.config,list=c.blockedScreens||[];
 const card=h('div',{class:'card'},h('h2',null,'Blocked screens ('+list.length+')'),
  h('div',{class:'mute'},'Screens picked on the phone with ✓ Block this screen (a Settings page, a screen of an app). Each closes the moment it opens. Remove one here and the phone opens it again at its next check-in.'));
 if(!list.length)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'None yet.'));
 for(const s of list){const p=s.split('\u001f');
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},p.slice(1).join(' › ')),h('div',{class:'mute small'},appLabelOf(dv,p[0]))),
   btn('Remove','outline',async function(){c.blockedScreens=list.filter(function(x){return x!==s});try{await saveConfigFor(dv,'Removed. The phone opens it again at its next check-in.')}catch(e){snack(e.message,1)}render()})))}
 m.append(card)}
function renderBlockedParts(m,dv){
 const c=dv.config,list=c.blockedParts||[];
 const card=h('div',{class:'card'},h('h2',null,'Parts of apps ('+list.length+')'),
  h('div',{class:'mute'},'Block one part of an app instead of the whole app: a button, a tab, a feed (for example YouTube Shorts or Instagram Reels). You pick it on the phone itself: Administrator → Apps → Tap to block, open the app, tap the part. "Covered" puts a Blocked box over it so it cannot be seen or tapped; "Screen closed" leaves the screen whenever it appears. Needs the accessibility switch on the phone (Safety → Health check).'));
 // Quick blocks: known parts of popular apps, found by the name of their button (English).
 const quick=[
  {key:'yt-shorts',label:'YouTube: Shorts',pkg:'com.google.android.youtube',desc:'Shorts',mode:'cover'},
  {key:'ig-reels',label:'Instagram: Reels',pkg:'com.instagram.android',desc:'Reels',mode:'cover'},
  {key:'ig-explore',label:'Instagram: Search and explore',pkg:'com.instagram.android',desc:'Search and explore',mode:'cover'},
  {key:'fb-reels',label:'Facebook: Reels',pkg:'com.facebook.katana',desc:'Reels',mode:'cover'},
  {key:'fb-video',label:'Facebook: Video',pkg:'com.facebook.katana',desc:'Video',mode:'cover'},
  {key:'maps-explore',label:'Google Maps: Explore',pkg:'com.google.android.apps.maps',desc:'Explore',mode:'cover'},
  {key:'snap-spotlight',label:'Snapchat: Spotlight',pkg:'com.snapchat.android',desc:'Spotlight',mode:'cover'}];
 const qrow=h('div',{class:'row',style:'margin-top:10px;flex-wrap:wrap'});
 for(const q of quick){const has=list.some(function(p){return p.pkg===q.pkg&&p.desc===q.desc});
  qrow.append(btn((has?'✓ ':'')+q.label,has?'outline':'tonal',async function(){
   if(has)c.blockedParts=list.filter(function(p){return !(p.pkg===q.pkg&&p.desc===q.desc)});
   else c.blockedParts=list.concat([{pkg:q.pkg,desc:q.desc,mode:q.mode,label:q.label}]);
   try{await saveConfigFor(dv,has?q.label+' allowed again.':q.label+' covered. If it still shows on the phone (another language or a new app version), use Tap to block on the phone instead.')}catch(e){snack(e.message,1)}render()}))}
 card.append(h('div',{style:'margin-top:10px;font-weight:500'},'Quick blocks'),h('div',{class:'mute small'},'One tap covers that tab in the app on this phone. They find the button by its English name; if one does not catch it, use Tap to block on the phone.'),qrow);
 if(!list.length)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Nothing picked yet.'));
 for(const p of list){
  card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},p.label),
   h('div',{class:'mute small'},(appLabelOf(dv,p.pkg))+' · '+(p.mode==='close'?'Screen closed':'Covered')+(p.viewId?' · '+p.viewId.replace(/^.*:id\//,''):''))),
   btn(p.mode==='close'?'Cover instead':'Close screen instead','outline',async function(){p.mode=p.mode==='close'?'cover':'close';try{await saveConfigFor(dv,'Saved.')}catch(e){snack(e.message,1)}render()}),
   btn('Remove','outline',async function(){c.blockedParts=list.filter(function(x){return x.id!==p.id});try{await saveConfigFor(dv,'Removed. The phone stops blocking it at its next check-in.')}catch(e){snack(e.message,1)}render()})))}
 m.append(card)}
function appLabelOf(dv,pkg){const p=(dv.packages||[]).find(function(x){return x.p===pkg});return p?(p.l||pkg):pkg}

/* ---------- Safety: health check, message alerts, screenshots ---------- */
const GROUP_HELP={
 overview:'How the phone is doing, a ready-made setup to start from, and messages the phone sent you.',
 safety:'Is everything working on the phone, alerts about worrying chats, and a look at the screen.',
 apps:'Which apps the phone can open, and the home screen.',
 time:'How long the phone was used, and limits like bedtime.',
 browser:'Which websites can open, and how strict the filter is.',
 calls:'Who can call the phone and whom it can call, contacts, and the call and text lists.',
 internet:'Wi-Fi, blocking or allowing single sites by name, and which apps may use the internet.',
 security:'Screen lock, a PIN for the MDM app, your administrator code, and restrictions so the phone cannot be changed or reset.',
 device:'Location, buttons on the phone, updates, and controls like restart.'};
/** What is not working on the phone, in plain words with how to fix it. level: bad (must fix) / warn (should fix). */
function healthProblems(d){
 const out=[],i=d.info||{},c=d.config||{};
 const add=function(level,what,fix){out.push({level:level,what:what,fix:fix})};
 if(d.syncPaused)add('bad','The phone is not connected to the dashboard (turned off on the phone itself).','On the phone: open MDM Agent → Administrator, and turn the connection back on.');
 else if(!isOnline(d))add(d.lastSeen&&Date.now()-d.lastSeen<6*3600000?'warn':'bad','The phone has not checked in for '+ago(d.lastSeen).replace(' ago','')+'.','Make sure the phone is on and has Wi-Fi or mobile data. Everything you set keeps working on the phone meanwhile.');
 if(i.deviceOwner===false)add('bad','MDM Agent is not in full control of the phone, so most settings cannot work.','Set the phone up again with the QR code (Add device → step by step).');
 const needFilter=(c.browserFilter&&c.browserFilter!=='off')||c.safeSearch||(c.youtubeDns&&c.youtubeDns!=='off')||c.internetOnlyApps;
 if(needFilter&&i.filterRunning===false)add('bad','The website filter is not running on the phone.','Restart the phone. If this stays red, update MDM Agent (Device → Updates).');
 const needA11y=c.messageAlerts||(c.inAppBrowsers&&c.inAppBrowsers!=='off')||c.blockWaStatus||c.blockWaChannels||(d.shots&&d.shots.length)||(d.config&&d.config.wholeAppBlocklist&&d.config.wholeAppBlocklist.length);
 if(i.accessibility===false&&needA11y)add('bad','One switch on the phone is still off, so message alerts, screenshots, WhatsApp and in-app web page controls cannot work.','On the phone: Settings → Accessibility → MDM Agent (sometimes under "Installed apps" or "Downloaded apps") → turn it on. The phone also shows a notification that opens this screen. Needed once.');
 if(c.locationEvery&&i.locationPerm===false)add('bad','Location is not allowed for MDM Agent, so location history cannot work.','On the phone: Settings → Location → turn on. Then Settings → Apps → MDM Agent → Permissions → Location → Allow all the time.');
 const stc=c.screenTime||{};
 if(i.usageAccess===false&&(stc.dailyLimit||Object.keys(stc.appLimits||{}).length))add('bad','Usage access is off on the phone, so the daily limit and app limits cannot count minutes.','On the phone: Settings → Apps → Special app access → Usage access → MDM Agent → allow. (Administrator → Rules → Safety on the phone has a Fix it button.)');
 if(i.batteryUnrestricted===false)add('warn','The phone\'s battery saver may put MDM Agent to sleep, so it checks in late.','On the phone: Settings → Apps → MDM Agent → Battery → Unrestricted (or "Don\'t optimize").');
 if(needsUpdate(d))add('warn','A newer MDM Agent is available.','Device → Updates → Update agent. It installs by itself.');
 if(i.battery&&i.battery.pct<=15&&!i.battery.charging)add('warn','The battery is at '+i.battery.pct+'%.','Charge the phone.');
 if(i.screenLock===false)add('warn','The phone has no screen lock, so anyone who picks it up can use it.','Security → Screen lock → Set PIN.');
 if(i.filterRunning===undefined&&i.deviceOwner)add('warn','This phone has an older MDM Agent that does not report everything the health check looks at.','Device → Updates → Update agent.');
 return out}
/** "What's on for this phone": every rule in force, one plain line each, each opening its own tab. */
function renderWhatsOn(box,d){
 const c=d.config||{},st=c.screenTime||{},now=Date.now(),rows=[];
 const add=function(icon,text,g,s){rows.push([icon,text,g,s])};
 const lvl={off:'Approved sites only',moderate:'Moderate filter (adult, gambling, drugs, scams, piracy blocked)',strict:'Strict filter (Moderate plus social media)'}[c.browserFilter||'off'];
 add('🌐','Websites: '+lvl+(c.safeSearch?', SafeSearch forced':'')+(c.youtubeDns&&c.youtubeDns!=='off'?', YouTube restricted':''),'browser','access');
 if((c.pauseUntil||0)>now)add('⏸','Web filtering paused until '+hhmm(c.pauseUntil),'overview','status');
 if((c.internetOffUntil||0)>now)add('📵','Internet off until '+hhmm(c.internetOffUntil),'overview','status');
 if(c.internetOnlyApps)add('🔌','Internet only for '+(c.internetApps||[]).length+' chosen apps','internet','appnet');
 if(st.dailyLimit)add('⏱','Daily screen time: '+fmtMin(st.dailyLimit)+(st.weekendLimit?' ('+fmtMin(st.weekendLimit)+' on chosen days)':''),'time','limits');
 if(st.bedtime)add('🌙','Bedtime '+st.bedtime.from+' to '+st.bedtime.to,'time','limits');
 if(st.schoolTime)add('🏫','School time '+st.schoolTime.from+' to '+st.schoolTime.to,'time','limits');
 const al=Object.keys(st.appLimits||{}).length;if(al)add('⏳',al+' app'+(al===1?' has':'s have')+' its own time limit','time','limits');
 const groups=(c.blockAppGroups||[]).map(function(g){return ((state.appGroups||{})[g]||{}).label||g});if(groups.length)add('🚫','Blocked groups: '+groups.join(', '),'apps','regular');
 const blockedApps=Object.values(c.apps||{}).filter(function(a){return a.mode==='block'}).length;if(blockedApps)add('🚫',blockedApps+' app'+(blockedApps===1?'':'s')+' blocked one by one','apps','regular');
 if(c.approveNew)add('✋','New apps wait for you to allow them','apps','regular');
 const parts=(c.blockedParts||[]).length;if(parts)add('🧩',parts+' part'+(parts===1?'':'s')+' of apps blocked (Tap to block)','apps','blocking');
 if(c.blockWaStatus||c.blockWaChannels)add('💬','WhatsApp: '+[c.blockWaStatus?'Status closed':'',c.blockWaChannels?'Channels closed':''].filter(Boolean).join(', '),'apps','regular');
 if(c.inAppBrowsers&&c.inAppBrowsers!=='off')add('🔗','Web pages inside apps: '+(c.inAppBrowsers==='all'?'all closed':'browser pages closed'),'browser','apps');
 if(c.callsIn&&c.callsIn!=='all'||c.callsOut&&c.callsOut!=='all')add('📞','Calls limited to '+(c.callsIn==='list'||c.callsOut==='list'?'allowed numbers':'contacts and allowed numbers'),'calls','rules');
 if(c.messageAlerts)add('🔔','Message alerts on (the child is told)','safety','msgalerts');
 if(c.recordSearches)add('🔎','Searches are kept (the child is told)','browser','history');
 if(c.recordSites)add('📄','Sites visited are kept','browser','history');
 if(c.locationEvery)add('📍','Location noted every '+c.locationEvery+' minutes','device','location');
 const card=h('div',{class:'card'},h('h2',null,"What's on for this phone"));
 for(const r of rows){const line=h('div',{class:'act',style:'cursor:pointer'},r[0]+'  '+r[1]);line.onclick=function(){goTo(r[2],r[3])};card.append(line)}
 card.append(h('div',{class:'mute small',style:'margin-top:6px'},'Tap a line to change it.'));
 box.append(card)}
function renderHealthSummary(box,d){
 const p=healthProblems(d);if(!p.length)return;
 const bad=p.filter(function(x){return x.level==='bad'}).length;
 box.append(h('div',{class:'card',style:'border-color:var(--'+(bad?'error':'warn')+')'},h('h2',null,bad?bad+(bad===1?' thing needs fixing':' things need fixing'):'Something to look at'),
  h('div',{class:'mute'},p[0].what),h('div',{style:'margin-top:8px'},btn('Open the health check','tonal',function(){goTo('safety','health')}))))}
function renderHealth(m,d){
 const p=healthProblems(d);
 const card=h('div',{class:'card'},h('h2',null,'Health check'),h('div',{class:'mute'},'Everything that has to work on the phone for your settings to work. Anything red needs fixing; it says how.'));
 if(!p.length)card.append(h('div',{style:'margin-top:10px;font-weight:500;color:var(--ok)'},'✅ Everything is working.'));
 for(const x of p)card.append(h('div',{class:'setting',style:'align-items:flex-start'},h('div',{style:'font-size:20px;margin-right:8px'},x.level==='bad'?'🔴':'🟡'),
  h('div',{class:'grow'},h('div',{style:'font-weight:500'},x.what),h('div',{class:'mute small'},'How to fix: '+x.fix))));
 const i=d.info||{},okRow=function(on,label){if(on)card.append(h('div',{class:'mute small',style:'margin-top:4px'},'✅ '+label))};
 card.append(h('div',{style:'margin-top:12px;font-weight:500'},'Working'));
 okRow(isOnline(d),'Connected (checked in '+ago(d.lastSeen)+')');okRow(i.deviceOwner,'MDM Agent is in full control');okRow(i.filterRunning,'Website filter running');
 okRow(i.accessibility,'Accessibility switch on');okRow(i.batteryUnrestricted,'Battery saver leaves it alone');okRow(i.screenLock,'Screen lock set');okRow(i.usageAccess,'Usage access on (screen time can count)');
 m.append(card)}

/** Photo alerts: new photos are checked on the phone; only a line of text comes here, never the photo. */
function renderNudityAlerts(m,dv){
 const c=dv.config;
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Photo alerts (nudity)'),
  h('div',{class:'mute small'},'Every new photo saved on the phone (camera, WhatsApp and other chats, downloads, screenshots) is checked on the phone itself. If one may show nudity you get an alert saying when and in which folder. The photo is never sent to you or anyone: sending it would mean sending a nude picture of a child.')),
  sw(c.nudityAlerts,function(on){c.nudityAlerts=on;save(on?'Photo alerts on. The phone tells the child that this is on.':'Photo alerts off.')})));
 card.append(h('div',{class:'mute small',style:'margin-top:6px'},'Good to know: the child is told this is on. Only photos saved from now on are checked, not videos, and not photos that are only looked at without being saved. Like every such checker it is sometimes wrong both ways. A dashboard screenshot that may show nudity is never sent, whether this is on or not.'));
 const list=(dv.nudityAlerts||[]).slice().reverse();
 if(list.length){card.append(h('div',{style:'margin-top:10px;font-weight:500'},'Alerts ('+list.length+')'));
  for(const x of list.slice(0,50))card.append(h('div',{class:'act'},'A photo that may show nudity'+(x.folder?' in '+x.folder:'')+' · '+ago(x.at)))}
 else if(c.nudityAlerts)card.append(h('div',{class:'mute small',style:'margin-top:8px'},'No alerts.'));
 m.append(card)}
function renderMsgAlerts(m,dv){
 const c=dv.config,groups=state.messageWordGroups||{};
 if(!Array.isArray(c.messageGroups))c.messageGroups=Object.keys(groups);
 const save=async function(msg){try{await saveConfigFor(dv,msg)}catch(e){snack(e.message,1)}render()};
 const card=h('div',{class:'card'},h('div',{class:'setting'},h('div',{class:'grow'},h('div',{style:'font-weight:500'},'Message alerts'),
  h('div',{class:'mute small'},'You get an alert when worrying words show up in a chat on the phone, with the sentence they were in. It looks at chats in WhatsApp, Instagram, Snapchat, Messenger, Telegram, Signal, Discord, TikTok, Viber and text messages. Nothing else is sent: not the other messages, not other apps.')),
  sw(c.messageAlerts,function(on){c.messageAlerts=on;save(on?'Message alerts on. The phone tells the child that this is on.':'Message alerts off.')})));
 card.append(h('div',{class:'mute small',style:'margin-top:6px'},'Good to know: the child sees a notice on the phone that message alerts are on. It only sees messages that appear on the screen (a chat that is opened), not pictures or voice notes. Needs one switch on the phone (Safety → Health check shows it if it is off). Alerts reach you by notification if you set that up (Settings → Alerts).'));
 if(c.messageAlerts){
  card.append(h('div',{style:'margin-top:12px;font-weight:500'},'Look for'));
  for(const g of Object.keys(groups)){
   card.append(h('div',{class:'setting'},h('div',{class:'grow'},h('div',null,groups[g].label),h('div',{class:'mute small'},'For example: '+groups[g].words.slice(0,4).join(', '))),
    sw(c.messageGroups.includes(g),function(on){c.messageGroups=on?c.messageGroups.concat([g]):c.messageGroups.filter(function(x){return x!==g});save('Saved.')})))}
  const own=h('textarea',{rows:3,style:'width:100%;margin-top:8px',placeholder:'Your own words or phrases, separated by commas. For example: a name, a place, a word.'});own.value=(c.messageWords||[]).join(', ');
  card.append(h('div',{style:'margin-top:12px;font-weight:500'},'Your own words'),own,h('div',{style:'margin-top:8px'},btn('Save my words','tonal',function(){c.messageWords=own.value.split(/[,\n]/).map(function(x){return x.trim()}).filter(Boolean);save('Saved. The phone gets them at its next check-in.')})))}
 m.append(card);
 const list=dv.msgAlerts||[];
 const lc=h('div',{class:'card'},h('h2',null,'Found ('+list.length+')'));
 if(!list.length)lc.append(h('div',{class:'mute'},c.messageAlerts?'Nothing found yet.':'Message alerts are off.'));
 for(const a of list.slice().reverse())lc.append(h('div',{class:'act'},h('div',null,h('b',null,'"'+a.word+'"'),' in '+(CHAT_NAMES[a.app]||SEARCH_NAMES[a.app]||a.app)+(a.who?' ('+a.who+')':'')+' · '+ago(a.at)),h('div',{class:'mute small'},a.text)));
 if(list.length)lc.append(h('div',{style:'margin-top:8px'},btn('Clear the list','outline',async function(){if(!confirm('Clear the list of found messages?'))return;await call('DELETE','/api/devices/'+dv.id+'/msg-alerts');load()})));
 m.append(lc)}
const SEARCH_NAMES={'com.android.chrome':'Chrome','org.mozilla.firefox':'Firefox','com.sec.android.app.sbrowser':'Samsung Internet','com.brave.browser':'Brave','com.microsoft.emmx':'Edge','com.google.android.youtube':'YouTube','com.google.android.googlequicksearchbox':'Google','com.familymdm.browser':'our Browser','com.pinterest':'Pinterest'};
const CHAT_NAMES={'com.whatsapp':'WhatsApp','com.whatsapp.w4b':'WhatsApp Business','org.telegram.messenger':'Telegram','com.instagram.android':'Instagram','com.facebook.orca':'Messenger','com.snapchat.android':'Snapchat','org.thoughtcrime.securesms':'Signal','com.discord':'Discord','com.zhiliaoapp.musically':'TikTok','com.google.android.apps.messaging':'Messages','com.samsung.android.messaging':'Samsung Messages','com.viber.voip':'Viber',texts:'a text message'};

function renderScreenshots(m,dv){
 const i=dv.info||{};
 const card=h('div',{class:'card'},h('h2',null,'See the phone\'s screen'),
  h('div',{class:'mute'},'Takes one picture of whatever is on the phone\'s screen right now and shows it here. The phone shows "A screenshot was sent to your parent". Pictures are kept for 7 days (the last 10).'));
 if(i.canScreenshot===false)card.append(h('div',{class:'mute small',style:'margin-top:6px;color:var(--warn)'},'This phone\'s Android is too old for this (it needs Android 11 or later).'));
 card.append(h('div',{style:'margin-top:10px'},btn('Take a screenshot now','',async function(){
  await call('POST','/api/devices/'+dv.id+'/command',{type:'screenshot',args:{}});snack('Asked the phone. The picture shows up here in a minute or two (tap Refresh).');load()})));
 const last=(dv.results||[]).slice().reverse().find(function(r){return r.type==='screenshot'});
 if(last&&!last.ok)card.append(h('div',{class:'mute small',style:'margin-top:8px;color:var(--error)'},'Last try did not work: '+last.msg));
 card.append(h('div',{class:'mute small',style:'margin-top:8px'},'Needs one switch on the phone: Safety → Health check shows it if it is off. Some apps (banking, some video apps) block screenshots; then the picture is black.'));
 m.append(card);
 const shots=(dv.shots||[]).slice().reverse();
 const g=h('div',{class:'card'},h('h2',null,'Pictures ('+shots.length+')'));
 if(!shots.length)g.append(h('div',{class:'mute'},'None yet.'));
 const grid=h('div',{style:'display:flex;flex-wrap:wrap;gap:12px;margin-top:8px'});
 for(const s of shots){const src='/api/devices/'+dv.id+'/shots/'+s.id;
  grid.append(h('a',{href:src,target:'_blank',rel:'noopener',style:'display:block;width:160px;text-decoration:none;color:inherit'},h('img',{src:src,alt:'Screenshot',loading:'lazy',style:'width:160px;border-radius:10px;border:1px solid var(--outline);display:block'}),
   h('div',{class:'mute small'},ago(s.at)+(s.app?' · '+(CHAT_NAMES[s.app]||s.app):''))))}
 g.append(grid);m.append(g)}

/* ---------- notifications on this phone or computer (Web Push) ---------- */
async function enablePush(){
 try{
  if(!('serviceWorker' in navigator)||!('PushManager' in window)||!('Notification' in window)){snack('This browser cannot show notifications. On iPhone: Share → Add to Home Screen first, then open the dashboard from that icon.',1);return}
  const reg=await navigator.serviceWorker.register('/sw.js');await navigator.serviceWorker.ready;
  const perm=await Notification.requestPermission();
  if(perm!=='granted'){snack('Notifications were not allowed. Allow them for this site in the browser settings and try again.',1);return}
  const k=await call('GET','/api/push/key');let b=k.publicKey.replace(/-/g,'+').replace(/_/g,'/');while(b.length%4)b+='=';
  const key=Uint8Array.from(atob(b),function(ch){return ch.charCodeAt(0)});
  let sub=await reg.pushManager.getSubscription();
  if(!sub)sub=await reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:key});
  await call('POST','/api/push/subscribe',{endpoint:sub.endpoint});
  snack('Notifications are on for this device. Press "Send a test" to try.');render()
 }catch(e){snack('Could not turn on notifications: '+(e.message||e),1)}}
async function disablePush(){
 try{const reg=await navigator.serviceWorker.getRegistration('/sw.js');const sub=reg&&await reg.pushManager.getSubscription();
  if(!sub){snack('This device was not getting notifications.');return}
  await call('POST','/api/push/unsubscribe',{endpoint:sub.endpoint});await sub.unsubscribe();snack('Notifications stopped on this device.');render()
 }catch(e){snack(e.message||String(e),1)}}

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
 if(state.session.role==='admin'){if(!state.session.helper)renderTwoStep(m);renderAlerts(m)}
 if(!state.session.helper)renderHelpers(m);
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
