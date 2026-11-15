import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import worker from "../src/index.js";
import { dashboardPage } from "../src/ui.js";

// Connecting a Browser needs a code made on the dashboard.
async function enrollBrowser(fetcher, cookie, info) {
  const { code } = await (await fetcher("/api/codes", { method: "POST", headers: { "content-type": "application/json", cookie }, body: JSON.stringify({ type: "browser" }) })).json();
  return fetcher("/browser/enroll", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code, info }) });
}

// Runs the dashboard's real page script against a small stand-in for the browser's page, signed in
// to a real (in-memory) copy of the server with a phone that has a bit of everything, and opens
// every group and every tab. A mistake that only shows when a tab is opened (a misspelt name, a
// missing function) fails here instead of leaving a blank screen in front of the administrator.

class El {
  constructor(tag, reg) { this.tagName = tag; this.children = []; this.style = {}; this.attrs = {}; this.className = ""; this._text = ""; this._reg = reg; this.nodeType = 1; this.files = []; this.value = ""; this.classList = { add() {}, remove() {}, toggle() {} }; }
  append(...xs) { for (const x of xs) if (x != null) this.children.push(x); }
  appendChild(x) { this.children.push(x); return x; }
  insertBefore(n, ref) { const i = this.children.indexOf(ref); if (i < 0) this.children.push(n); else this.children.splice(i, 0, n); return n; }
  replaceWith() {}
  remove() {}
  get lastChild() { return this.children[this.children.length - 1] || null; }
  get firstChild() { return this.children[0] || null; }
  set textContent(v) { this.children = []; this._text = String(v); }
  get textContent() { return this._text + this.children.map((c) => (typeof c === "string" ? c : c.textContent || "")).join(" "); }
  setAttribute(k, v) { this.attrs[k] = v; if (k === "id") this._reg.set(v, this); }
  getAttribute(k) { return this.attrs[k]; }
  addEventListener() {}
  dispatchEvent() {}
  querySelector() { return null; }
  querySelectorAll() { return []; }
  focus() {} click() {} showModal() {} close() {} setSelectionRange() {} scrollIntoView() {}
}

async function setUp() {
  const kv = new Map();
  const env = { ADMIN_PASSWORD: "pw", STATE: {
    get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
    put: async (k, v) => void kv.set(k, v), delete: async (k) => void kv.delete(k),
    list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }) } };
  const call = (path, init) => worker.fetch(new Request("https://x.test" + path, init), env);
  const login = await call("/login", { method: "POST", body: new URLSearchParams({ password: "pw" }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const J = { "content-type": "application/json" };
  const { code } = await (await call("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await call("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Pixel", manufacturer: "Test" } }) })).json();
  await call("/agent/sync", { method: "POST", headers: { ...J, authorization: "Bearer " + token }, body: JSON.stringify({
    packages: [{ p: "com.google.android.apps.maps", l: "Maps", s: false, h: false, c: 6 }, { p: "com.instagram.android", l: "Instagram", s: false, h: true }, { p: "com.example.puzzle", l: "Puzzle Fun", s: false, h: false, c: 0 }, { p: "com.android.vending", l: "Google Play Store", s: true, h: false }],
    info: { deviceOwner: true, android: "14", versionCode: 400, battery: { pct: 80, charging: false }, wifi: { transport: "wifi", ssid: "Home" }, storeUntil: -1 },
    downloads: [{ id: "abcdefghijk", t: "Test song", k: "music", at: 1700000000000, bytes: 5242880 }, { id: "lmnopqrstuv", t: "Test video", k: "video", at: 1700000001000, bytes: 9437184 }],
    downloadsAt: 1700000002000,
    siteRequests: [{ url: "https://example.com/page" }, { url: "https://music.youtube.com/search?q=Avraham+Fried", note: "can I have his songs please" }, { url: "https://www.youtube.com/@NASA", note: "for my science homework" }, { url: "https://music.youtube.com/browse/MPREb_abc123", note: "Asked from YouTube Music: Greatest Album" }],
    appRequests: [{ kind: "store" }, { kind: "app", pkg: "com.instagram.android", label: "Instagram" }, { kind: "time" }],
    usage: { day: "2026-11-10", total: 134, ok: true, apps: [{ p: "com.google.android.apps.maps", l: "Maps", m: 90 }, { p: "com.instagram.android", l: "Instagram", m: 44 }] },
    messages: [{ msg: "hello" }],
    events: [{ k: "restriction", m: "DNS/TLS filter turned on (0 host(s) blocked, plus the browser category filter)" }] }) });
  const dev = (await (await call("/api/devices", { headers: { cookie } })).json())[0];
  await call(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { ...J, cookie }, body: JSON.stringify({
    screenTime: { dailyLimit: 180, bedtime: { days: [0, 1, 2, 3, 4], from: "22:00", to: "07:00" }, appLimits: { "com.instagram.android": 30 }, exempt: ["com.google.android.apps.maps"] },
    chromePolicy: { noIncognito: true }, blockedWords: ["casino", "sex"], blockImageSearch: true, locationEvery: 30, places: [{ name: "School", lat: 40.7, lon: -74, r: 200 }], internetOnlyApps: true, callsIn: "list", callNumbers: [{ name: "Mom", number: "+1 212 555 1234" }], callLog: true, textLog: true, browserFilter: "moderate", blockAppGroups: ["social"], tlsInspection: true, safeSearch: true, youtubeDns: "strict", blockImages: true, imagesAllow: ["maps.google.com"], userMenu: { store: false },
    sites: { a: { type: "domain", url: "example.org" }, b: { type: "youtube", id: "@somechannel" } },
    apps: { "com.android.vending": { mode: "block", label: "Google Play Store" } } }) });
  await call("/api/templates", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ name: "Teen phone", deviceId: dev.id }) });
  const winReg = await (await call("/windows/register", { method: "POST", headers: J, body: JSON.stringify({ info: { hostname: "KIDS-PC" } }) })).json();
  await call("/windows/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${winReg.token}` }, body: JSON.stringify({ info: { hostname: "KIDS-PC", tz: 120, filter: { on: true, running: true, listsReady: true }, accounts: { users: ["Dad", "Kid"], admins: ["Dad"] } }, trafficLog: [{ kind: "dns", host: "www.wikipedia.org", time: Date.now(), blocked: false }] }) });
  await (await enrollBrowser(call, cookie, { model: "B" })).json();
  return { call, cookie };
}

test("every dashboard page, group and tab opens without an error", async () => {
  const { call, cookie } = await setUp();
  const reg = new Map();
  const mk = (tag) => new El(tag, reg);
  for (const id of ["main", "snack", "crumbs", "who", "tabs", "srv", "mode"]) mk("div").setAttribute("id", id);
  const errors = [];
  const store = new Map();
  const ctx = vm.createContext({
    console, URL, URLSearchParams, TextEncoder, TextDecoder, Date, Math, JSON, Promise, crypto: globalThis.crypto,
    setTimeout, clearTimeout, setInterval: () => 0, clearInterval: () => {},
    document: { createElement: mk, createElementNS: (ns, tag) => mk(tag), createTextNode: (t) => String(t), getElementById: (id) => reg.get(id) || null, body: mk("body"), querySelector: () => null, addEventListener() {} },
    location: { hash: "#devices", origin: "https://x.test", reload() {} },
    sessionStorage: { getItem: (k) => (store.has(k) ? store.get(k) : null), setItem: (k, v) => void store.set(k, String(v)), removeItem: (k) => void store.delete(k) },
    localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
    confirm: () => true, prompt: () => null, alert() {},
    Option: function (text, value) { const o = mk("option"); o._text = String(text); o.value = value; return o; },
    CustomEvent: function () {},
    qrcode: () => ({ addData() {}, make() {}, createDataURL: () => "data:," }),
    fetch: (path, init = {}) => call(path, { ...init, headers: { ...(init.headers || {}), cookie } }),
    scrollTo() {}, addEventListener() {},
  });
  ctx.window = ctx;
  const html = dashboardPage();
  const script = html.match(/<script>([\s\S]*)<\/script>\s*<\/body>/)[1];
  process.on("unhandledRejection", (e) => errors.push("unhandled: " + (e && e.stack ? e.stack.split("\n").slice(0, 2).join(" | ") : e)));
  vm.runInContext(script, ctx);
  for (let i = 0; i < 100 && !vm.runInContext("typeof state!=='undefined'&&state&&devices.length", ctx); i++) await new Promise((r) => setTimeout(r, 20));
  assert.ok(vm.runInContext("devices.length", ctx) === 1, "the page loaded its data");

  const tryRender = (label, code) => {
    try {
      vm.runInContext(code, ctx);
    } catch (e) {
      errors.push(label + ": " + e.message);
    }
    return reg.get("main").textContent;
  };
  const seen = {};
  vm.runInContext("uiMode='new'", ctx); // Legacy is the default look now; the checks below are for the New look, the Legacy look is checked near the end
  seen.list = tryRender("device list", "tab='devices';openId=null;openBrowserId=null;openWinId=null;render()");
  for (const page of ["codes", "settings", "clients", "audit", "templates"]) seen[page] = tryRender(page, `tab='${page}';openId=null;render()`);
  // The first time a phone is opened its page goes back to its start (the home): open it once, then visit every group.
  seen.home = tryRender("phone home (new look)", "tab='devices';openId=devices[0].id;openBrowserId=null;openWinId=null;render()");
  seen.server = tryRender("server page", "tab='server';openId=null;render()");
  seen.badgeCloudflare = reg.get("srv").textContent;
  // The same page on the Raspberry Pi: health, Wi-Fi, the networks it remembers, the backup, restart and log.
  seen.serverPi = tryRender("server page on the Pi", "var __s0=state.server;state.server={runtime:'pi',role:'main',primary:'https://pi.example',backup:'https://cf.example',outage:false};" +
    "piInfo={role:'main',version:'abc1234',serverUptimeSeconds:99,entries:5,publicUrl:'https://pi.example',backupUrl:'https://cf.example',polling:{idleSeconds:300,dashboardOpenSeconds:3,afterChangeSeconds:3}," +
    "health:{model:'Raspberry Pi Zero 2 W',uptimeSeconds:3600,cpuTempC:41,memoryTotalMB:416,memoryFreeMB:200,diskTotalGB:30,diskFreeGB:25,power:{ok:true}},wifi:{state:'connected',ssid:'Home',signal:70,ip:'192.168.0.5/24'}," +
    "tailscale:{installed:true,state:'Running',name:'x.ts.net'},replication:{configured:true,pending:2,lastCopyAt:Date.now(),lastCopyCount:3,backupReachable:true,outagesRecovered:0}};" +
    "piWifi={saved:[{name:'Home',priority:10,autoconnect:true,active:true},{name:'Guest',priority:5,autoconnect:true,active:false}],visible:[{ssid:'Cafe',signal:50,security:'WPA2',active:false}]};tab='server';render()");
  seen.badgePi = reg.get("srv").textContent;
  seen.homeOnPi = tryRender("phone home with the Pi badge", "tab='devices';openId=devices[0].id;render()");
  // The Cloudflare page says what is RUNNING: the Raspberry Pi when it answers, even though this page is the backup copy.
  tryRender("badge: Cloudflare page, Pi running", "state.server={runtime:'cloudflare',role:'backup',primary:'https://pi.example',backup:'https://cf.example',primaryUp:true,outage:false};tab='devices';openId=null;render()");
  seen.badgeCfPiUp = reg.get("srv").textContent;
  tryRender("badge: Cloudflare page, Pi off", "state.server={runtime:'cloudflare',role:'backup',primary:'https://pi.example',backup:'https://cf.example',primaryUp:false,outage:true};tab='devices';openId=null;render()");
  seen.badgeCfPiDown = reg.get("srv").textContent;  tryRender("back to Cloudflare", "state.server=__s0;piInfo=null;piWifi=null");
  tryRender("back to the phone", "tab='devices';openId=devices[0].id;render()");
  const groups = vm.runInContext("JSON.stringify(GROUPS.map(function(g){return [g.key,subsFor(g).map(function(s){return s[0]})]}))", ctx);
  for (const [g, subs] of JSON.parse(groups)) {
    for (const sub of subs) {
      seen[g + "/" + sub] = tryRender(g + "/" + sub, `tab='devices';openId=devices[0].id;deviceSection='${g}';subs['${g}']='${sub}';render()`);
    }
  }
  // In the New look YouTube Music has its own page (the YouTube group only has the YouTube tab).
  seen["youtube/music"] = tryRender("YouTube Music page (new look)", "tab='devices';openId=devices[0].id;deviceSection='music';subs['music']='music';render()");
  // The Legacy look keeps every tab it ever had (the New look folds some together): walk them all in Legacy mode.
  vm.runInContext("uiMode='legacy'", ctx);
  const legacyGroups = vm.runInContext("JSON.stringify(GROUPS.map(function(g){return [g.key,g.subs.map(function(s){return s[0]})]}))", ctx);
  for (const [g, subs] of JSON.parse(legacyGroups)) {
    for (const sub of subs) {
      seen["L:" + g + "/" + sub] = tryRender("legacy " + g + "/" + sub, `tab='devices';openId=devices[0].id;deviceSection='${g}';subs['${g}']='${sub}';render()`);
    }
  }
  vm.runInContext("uiMode='new'", ctx);
  seen.browserDevice = tryRender("standalone browser page", "tab='devices';openId=null;openBrowserId=browsers[0].id;browserSection='overview';render()");
  for (const s of ["web", "youtube", "pictures", "sites", "settings"]) seen["br/" + s] = tryRender("standalone browser: " + s, `tab='devices';openId=null;openBrowserId=browsers[0].id;browserSection='${s}';render()`);
  await new Promise((r) => setTimeout(r, 150)); // let the cards that fetch their own data finish
  // Pages that fill themselves in after asking the server: open them again and read them once that is done.
  for (const page of ["templates", "settings"]) {
    tryRender(page, `tab='${page}';openId=null;openBrowserId=null;render()`);
    await new Promise((r) => setTimeout(r, 200));
    seen[page] = reg.get("main").textContent;
  }
  assert.deepEqual(errors, [], "no tab threw an error");

  // A few things that must actually be on the screen, not just "no crash".
  assert.match(seen["overview/status"], /Requests from the phone \(3\)/);
  assert.match(seen["overview/status"], /More screen time/);
  assert.match(seen["time/report"], /2 h 14 min/);
  assert.match(seen["time/report"], /of 3 h allowed today/);
  assert.match(seen["time/report"], /Instagram/);
  assert.match(seen["time/report"], /44 min of 30 min/);
  assert.match(seen["time/report"], /Give 15 minutes/);
  assert.match(seen["time/limits"], /Locked from 22:00 to 07:00/);
  assert.match(seen["time/limits"], /Does not count toward the daily limit/);
  assert.match(seen["overview/status"], /Play Store open/);
  assert.match(seen["apps/regular"], /Block social media apps/);
  assert.match(seen["apps/regular"], /On this phone: Instagram/);
  assert.match(seen["apps/store"], /Keep the Play Store closed/);
  assert.match(seen["apps/regular"], /Block games/);
  assert.match(seen["apps/regular"], /On this phone: Puzzle Fun/, "a game is found by the category it declares");
  assert.match(seen["L:browser/apps"], /No incognito tabs/);
  assert.match(seen["L:browser/apps"], /Also locked in Chrome from the settings above: SafeSearch, YouTube Restricted Mode, no pictures/);
  assert.match(seen["browser/summary"], /What this phone can open/);
  assert.match(seen["browser/summary"], /Moderate/);
  assert.match(seen["browser/summary"], /He can search YouTube and watch/);
  assert.match(seen["browser/summary"], /browser category filter/, "the phone's own filter log line is shown");
  assert.match(seen["L:browser/access"], /What can they open on the web/, "one page: the level, with what each closes");
  assert.match(seen["L:browser/access"], /Also close these in every browser/, "the News / Shopping / Games ... boxes are on the Legacy page too");
  assert.match(seen["L:browser/access"], /Safe browsing/);
  assert.match(seen["L:browser/access"], /SafeSearch/);
  assert.match(seen["L:browser/access"], /YouTube Restricted Mode everywhere/);
  assert.match(seen["L:browser/access"], /Block pictures on web pages/);
  assert.match(seen["browser/summary"], /SafeSearch is forced on Google, Bing and DuckDuckGo/);
  assert.match(seen["browser/summary"], /YouTube Restricted Mode \(strict\) is forced everywhere/);
  assert.match(seen["browser/summary"], /Hidden on web pages in our browsers, in Chrome, and in other browsers/);
  assert.match(seen["br/pictures"], /Block pictures on web pages/);
  assert.match(seen["br/pictures"], /No pictures on these sites/);
  assert.match(seen.browserDevice, /At a glance/);
  assert.match(seen["br/settings"], /Look/);
  assert.match(seen["br/youtube"], /Only what I allow/);
  assert.match(seen["br/youtube"], /Allowed channels and videos/);
  assert.doesNotMatch(seen["br/web"], /On unless you switch it off here/, "a standalone Browser has no phone-wide filter to force it with");
  assert.match(seen["browser/sites"], /Site requests \(4\)/);
  assert.match(seen["browser/sites"], /YouTube channel/);
  assert.match(seen["L:browser/apps"], /Make this the only browser/);
  assert.match(seen["L:browser/inspect"], /Never look inside these sites/);
  assert.match(seen["device/usermenu"], /Open Google Play Store/);
  assert.match(seen["br/web"], /Web access/, "a standalone Browser still has its filter card");
  assert.match(seen.list, /Pixel/);
  assert.match(seen["L:browser/access"], /Blocked words \(2\)/);
  assert.match(seen["L:browser/access"], /Block image search/);
  // The new look: a home with what is waiting, quick buttons and one line per part; Browser has its own simple page.
  assert.match(seen.home, /Requests from the phone \(3\)/);
  assert.match(seen.home, /Quick actions/);
  assert.match(seen.home, /Lock screen/);
  assert.match(seen.home, /Screen time today/);
  assert.match(seen.home, /Browser and web/);
  assert.match(seen.home, /Moderate filter · 1 site approved · SafeSearch on/);
  assert.match(seen.home, /3 h a day|3 h/);
  assert.doesNotMatch(seen.home, /Ready-made setups/, "the long overview page is not on the home");
  assert.match(seen["browser/summary"], /What can they open on the web\?/);
  assert.match(seen["browser/summary"], /Approved sites only/);
  assert.match(seen["browser/summary"], /Strict/);
  assert.match(seen["browser/summary"], /Medium/);
  assert.match(seen["browser/summary"], /Closed in every browser: adult sites, gambling/);
  assert.match(seen["browser/summary"], /Also close these in every browser/);
  assert.match(seen["browser/summary"], /Social media/);
  assert.match(seen["browser/summary"], /Wall Street Journal/);
  assert.match(seen["browser/summary"], /Temu/);
  assert.match(seen["browser/summary"], /Safe browsing/);
  assert.match(seen["browser/summary"], /Approved sites \(1\)/);
  assert.match(seen["browser/summary"], /More settings/);
  assert.match(seen["browser/summary"], /What this phone can open/, "the old tabs are still there under More settings");
  // New look, simplified: Right now, the five-line status, the folded tabs.
  for (const t of [/Right now/, /Pause web filter/, /All restrictions off/, /Give more time/, /Internet off/]) assert.match(seen.home, t, "the Right now card: " + t);
  assert.doesNotMatch(seen.home, /\+15 minutes|Internet off for 1 hour/, "those two buttons moved into Right now");
  assert.match(seen["overview/status"], /What is on for this phone/);
  for (const t of [/Web · /, /Apps · /, /Time · /, /Calls and texts · /, /Safety · /]) assert.match(seen["overview/status"], t, "status line " + t);
  assert.doesNotMatch(seen["overview/status"], /Turn off all restrictions for a while/, "the all-off card is only in Right now in the New look");
  assert.match(seen["browser/summary"], /At a glance\s+Schedule and words\s+Sites visited\s+Allowed sites\s+Advanced/, "Browser has five tabs");
  assert.doesNotMatch(seen["browser/summary"], /Inspection \(advanced\)|Browser apps/, "the two rarely used tabs are folded into Advanced");
  assert.match(seen["browser/advanced"], /Never look inside these sites/, "Advanced holds Inspection");
  assert.match(seen["browser/advanced"], /Rarely needed/);
  assert.equal((seen["browser/access"].match(/What can they open on the web\?/g) || []).length, 1, "the level choice is not repeated under More settings");
  assert.match(seen["browser/access"], /Blocked words \(2\)/, "the extras are still there");
  assert.match(seen["browser/access"], /Internet, Blocked and allowed addresses/, "it says where single addresses are blocked");
  assert.match(seen["apps/blocking"], /Hidden system apps \(advanced\)/, "system apps are inside Blocked");
  assert.match(seen["apps/blocking"], /Scan for hidden system apps/);
  assert.match(seen["internet/dns"], /Blocked and allowed addresses/);
  assert.match(seen["internet/dns"], /Browser and web, the level at the top/, "it says where whole kinds of site are closed");
  assert.equal(vm.runInContext("goTo('browser','inspect');subs['browser']", ctx), "advanced", "an old link to Inspection opens Advanced");
  assert.equal(vm.runInContext("goTo('apps','system');subs['apps']", ctx), "blocking", "an old link to System apps opens Blocked");
  vm.runInContext("goTo('browser','youtube')", ctx);
  assert.equal(vm.runInContext("deviceSection+'/'+subs['youtube']", ctx), "youtube/yt", "the Browser > YouTube link goes to the YouTube group");
  // Right now, when things are on: it says so and offers to turn them back.
  const rn = tryRender("right now, all on", "var __c=devices[0].config;__c.pauseUntil=Date.now()+3600000;__c.restrictionsOffUntil=Date.now()+3600000;__c.internetOffUntil=Date.now()+3600000;deviceSection=null;tab='devices';openId=devices[0].id;render()");
  tryRender("right now, back to normal", "var __c=devices[0].config;__c.pauseUntil=0;__c.restrictionsOffUntil=0;__c.internetOffUntil=0;render()");
  for (const t of [/Web filter paused until/, /Web filter back on/, /ALL restrictions are off until/, /Turn everything back on/, /Internet off until/, /Internet back on/]) assert.match(rn, t, "Right now while on: " + t);
  // Which YouTube do they use: regular, regular filtered, or ours; for YouTube and for YouTube Music, in both looks.
  for (const [page, L] of [["youtube/yt", "YouTube"], ["youtube/music", "YouTube Music"]]) {
    assert.match(seen[page], new RegExp("Which " + L + " do they use\\?"), page + ": the three-way choice is on the page");
    assert.match(seen[page], new RegExp("Regular " + L + ", no filter"));
    assert.match(seen[page], new RegExp("Regular " + L + ", filtered"));
    assert.match(seen[page], new RegExp("Our " + L));
  }
  assert.doesNotMatch(seen["youtube/yt"], /Which YouTube Music do they use\?/, "the YouTube page is only about YouTube");
  assert.doesNotMatch(seen["youtube/yt"], /YouTube Music icon|Allowed songs/, "no Music switches or links on the YouTube page");
  assert.match(seen["youtube/music"], /What can they play in our YouTube Music\?/, "YouTube Music has its own page with its own strictness");
  assert.match(seen["youtube/music"], /Same as YouTube/);
  assert.doesNotMatch(seen["youtube/music"], /What can they watch on YouTube\?/, "and none of the YouTube page");
  assert.match(seen["youtube/music"], /Approve music/, "approving songs is on the Music page");
  assert.match(seen.home, /YouTube\s+Regular, filtered · 1 channel.*YouTube Music\s+Regular, filtered/, "the home page has a row for each, saying which one they use");
  const ytTest = vm.runInContext("(function(){var out={},MU='com.google.android.apps.youtube.music',YT='com.google.android.youtube',c=JSON.parse(JSON.stringify(devices[0].config));" +
    "c.apps=c.apps||{};delete c.apps[MU];delete c.apps[YT];c.filterSkipApps=[];c.youtubeDns='off';c.youtubeAddon=false;c.youtubeMusicAddon=false;c.networkBlocklist={};" +
    "out.start=ytWhich(c,'music')+'/'+ytWhich(c,'yt');" +
    "setYtWhich(c,'music','ours');out.oursMode=c.apps[MU].mode;out.oursAddon=c.youtubeMusicAddon;out.oursWhich=ytWhich(c,'music');out.oursSkip=c.filterSkipApps.indexOf(MU)>=0;out.ytUntouched=!c.apps[YT]&&c.youtubeAddon===false;" +
    "setYtWhich(c,'music','free');out.freeMode=c.apps[MU].mode;out.freeAddon=c.youtubeMusicAddon;out.freeWhich=ytWhich(c,'music');out.freeSkip=c.filterSkipApps.indexOf(MU)>=0;out.freeDns=c.youtubeDns;" +
    "setYtWhich(c,'yt','filtered');out.filtMode=c.apps[YT].mode;out.filtWhich=ytWhich(c,'yt');out.filtSkip=c.filterSkipApps.indexOf(YT)>=0;out.filtDns=c.youtubeDns;out.musicStillFree=ytWhich(c,'music');" +
    "c.youtubeDns='strict';setYtWhich(c,'yt','filtered');out.strictKept=c.youtubeDns;" +
    "c.networkBlocklist[YT]={paused:false};setYtWhich(c,'yt','free');out.netCleared=!c.networkBlocklist[YT];" +
    "setYtWhich(c,'yt','ours');out.ytOurs=ytWhich(c,'yt');c.youtubeAddon=false;out.bothOff=ytWhich(c,'yt');" +
    "return JSON.stringify(out)})()", ctx);
  const yt = JSON.parse(ytTest);
  assert.equal(yt.start, "free/free", "a phone with nothing set uses the regular apps");
  assert.deepEqual([yt.oursMode, yt.oursAddon, yt.oursWhich, yt.oursSkip, yt.ytUntouched], ["block", true, "ours", false, true], "Our YouTube Music: real app blocked, our icon on, YouTube untouched");
  assert.deepEqual([yt.freeMode, yt.freeAddon, yt.freeWhich, yt.freeSkip], ["allow", false, "free", true], "Regular, no filter: real app allowed, our icon off, the filter skips it");
  assert.deepEqual([yt.filtMode, yt.filtWhich, yt.filtSkip, yt.filtDns, yt.musicStillFree], ["allow", "filtered", false, "moderate", "free"], "Regular, filtered: Restricted Mode goes on, and the other app keeps its own choice");
  assert.equal(yt.strictKept, "strict", "a stricter Restricted Mode is never turned down by choosing filtered");
  assert.equal(yt.netCleared, true, "a leftover no-internet rule is cleared when the real app is chosen");
  assert.equal(yt.ytOurs, "ours");
  assert.equal(yt.bothOff, "off", "real app blocked and our icon off is shown as off");
  assert.match(tryRender("youtube choice, legacy look", "uiMode='legacy';deviceSection='youtube';subs['youtube']='music';tab='devices';openId=devices[0].id;render()"), /Which YouTube Music do they use\?/, "the Legacy look has it too");
  tryRender("back to the new look", "uiMode='new'");
  // Apps: a list or tiles (switch at the top); tapping an app shows every setting for it.
  const appsList = tryRender("apps, list", "appsView='list';appSel=null;deviceSection='apps';subs['apps']='regular';tab='devices';openId=devices[0].id;render()");
  const appsTiles = tryRender("apps, tiles", "appsView='tiles';appSel=null;render()");
  const appFirst = vm.runInContext("dv=devices[0];dv.packages.filter(function(a){return a.p!==SELF_PACKAGE&&!a.s})[0].p", ctx);
  const appOpen = tryRender("apps, one app opened", "appSel='" + appFirst + "';render()");
  tryRender("apps, back to the list", "appSel=null;appsView='list';render()");
  assert.deepEqual(errors, [], "no error while showing the apps list, tiles and one opened app");
  assert.match(appsList, /Apps on this phone \(\d+\)/);
  assert.match(appsList, /List\s*Tiles/, "the layout switch is at the top");
  assert.match(appsTiles, /Puzzle Fun/, "tiles show the app names");
  assert.ok(vm.runInContext("document.querySelectorAll?1:1", ctx));
  for (const t of [/Can they open it\?/, /Time per day/, /Only at set times/, /Internet for this app/, /On, filtered/, /Close/]) assert.match(appOpen, t, "the opened app shows: " + t);
  // A custom time: a drop-down with a Custom choice, and the app's own time limit offers it.
  const ts = vm.runInContext("(function(){var n=timeSelect([[0,'No limit'],[60,'1 hour']],90,function(){});var sel=n.children[0],o=[];for(var i=0;i<sel.children.length;i++)o.push(sel.children[i]._text);return JSON.stringify(o)})()", ctx);
  assert.deepEqual(JSON.parse(ts), ["No limit", "1 hour", "1 h 30 min", "Custom…"], "a drop-down of times ends with Custom, and shows a time that is already set");
  assert.match(appOpen, /Custom…/, "the app's time per day has a Custom choice");
  // Music has its own strictness setting, saved as musicMode.
  assert.equal(vm.runInContext("(function(){var c=devices[0].config;c.musicMode='channels';var r=c.musicMode;delete c.musicMode;return r})()", ctx), "channels");
  assert.match(seen.list, /Add device/);
  assert.match(seen.list, /Screen time today: 2 h 14 min of 3 h/, "the front page is one card for each device");
  assert.match(seen.list, /\d+ requests?/);
  assert.match(seen.clients, /Sign-ups/, "the Clients page has the sign-ups card (open or close the page, approve or deny)");
  assert.match(seen["apps/regular"], /New apps wait for my approval/);
  assert.match(seen["apps/regular"], /Apps on this phone/);
  assert.match(seen["apps/regular"], /Puzzle Fun/);
  assert.match(seen["time/report"], /Screen time per day/);
  assert.match(seen["time/report"], /Bedtime/);
  assert.match(seen["youtube/yt"], /What can they watch on YouTube\?/);
  assert.match(seen["youtube/yt"], /Only what I allow/);
  assert.match(seen["youtube/yt"], /YouTube Restricted Mode is on \(strict\)/);
  assert.match(seen["youtube/yt"], /Use Moderate instead/);
  assert.match(seen.server, /This page comes from Cloudflare/);
  assert.match(seen.badgeCloudflare, /Cloudflare/);
  assert.match(seen.badgePi, /Raspberry Pi/);
  assert.match(seen.badgeCfPiUp, /Raspberry Pi is running/);
  assert.match(seen.badgeCfPiDown, /Raspberry Pi is off/);
  assert.match(seen.serverPi, /This page comes from your Raspberry Pi/);
  assert.match(seen.serverPi, /Networks the Pi remembers/);
  assert.match(seen.serverPi, /Guest/);
  assert.match(seen.serverPi, /Add a network/);
  assert.match(seen.serverPi, /every 3 seconds/);
  assert.match(seen.serverPi, /every 5 minutes/);
  assert.match(seen.serverPi, /Cloudflare backup/);
  assert.match(seen.serverPi, /Restart the Pi/);
  assert.match(seen.serverPi, /via Cloudflare/, "each phone says which server it used");
  assert.match(seen.homeOnPi, /via Cloudflare/);
  // The old look is still there, exactly as it was, behind the switch.
  const legacy = tryRender("legacy look", "uiMode='legacy';deviceSection='overview';subs['overview']='status';tab='devices';openId=devices[0].id;render()");
  const legacyBrowser = tryRender("legacy: web protection page", "uiMode='legacy';deviceSection='browser';subs['browser']='access';tab='devices';openId=devices[0].id;render()");
  const legacyApps = tryRender("legacy: apps", "uiMode='legacy';deviceSection='apps';subs['apps']='regular';tab='devices';openId=devices[0].id;render()");
  tryRender("a screen blocked on the phone", "devices[0].config.blockedComponents=['com.example.app/com.example.app.PlayerActivity']");
  const legacyBlocking = tryRender("legacy: blocking apps and screens", "uiMode='legacy';deviceSection='apps';subs['apps']='blocking';tab='devices';openId=devices[0].id;render()");
  const legacyTime = tryRender("legacy: time limits", "uiMode='legacy';deviceSection='time';subs['time']='limits';tab='devices';openId=devices[0].id;render()");
  const legacyOpen = tryRender("legacy: Open level", "devices[0]._bf=devices[0].config.browserFilter;devices[0].config.browserFilter='custom';devices[0].config.filterKinds=['adult','news'];uiMode='legacy';deviceSection='browser';subs['browser']='access';tab='devices';openId=devices[0].id;render()");
  tryRender("back to the earlier level", "devices[0].config.browserFilter=devices[0]._bf;uiMode='new'");
  const legacyOff = tryRender("legacy: everything is off", "devices[0].config.restrictionsOffUntil=Date.now()+3600000;devices[0].config.restrictionsOffSince=Date.now();uiMode='legacy';deviceSection='overview';subs['overview']='status';tab='devices';openId=devices[0].id;render()");
  tryRender("back to the new look", "devices[0].config.restrictionsOffUntil=0;uiMode='new'");
  assert.match(legacy, /Turn off all restrictions for a while/, "the timed all-off card asks how long");
  assert.match(legacy, /What's on for this phone/, "Legacy keeps its long status card");
  assert.equal(vm.runInContext("GROUPS.find(function(g){return g.key==='browser'}).subs.length", ctx), 7, "Legacy Browser keeps its 7 tabs");
  assert.equal(vm.runInContext("GROUPS.find(function(g){return g.key==='apps'}).subs.length", ctx), 6, "Legacy Apps keeps its 6 tabs");
  assert.match(seen["L:browser/youtube"], /Only what I allow|YouTube/, "Legacy still has Browser > YouTube");
  assert.match(seen["L:apps/system"], /Scan for hidden system apps/, "Legacy still has the System apps tab");
  assert.match(seen["L:internet/dns"], /Website filter/, "Legacy keeps the old tab name");
  assert.match(legacyOff, /All restrictions are OFF/);
  assert.match(legacyOff, /Turn everything back on now/);
  assert.match(legacyBrowser, /What can they open on the web/, "Legacy has the one clear web page too");
  assert.match(legacyBrowser, /Also close these in every browser/);
  assert.match(legacyBrowser, /Strict/);
  assert.doesNotMatch(legacyBrowser, /Web access/, "the old pile of cards is folded into one page");
  assert.match(legacyApps, /Internet for this app/, "per-app internet switch right on the Apps list");
  assert.match(legacyBlocking, /Block a screen by its name/, "a screen can be blocked by typing its name");
  assert.match(legacyBlocking, /Look it up/);
  assert.match(legacyBlocking, /Player/, "a screen blocked on the phone by its name is listed, to remove from here");
  assert.match(legacyBlocking, /com\.example\.app\.PlayerActivity/);
  assert.match(legacyTime, /Time on a site, every day/, "a daily allowance can be given to a site");
  assert.match(legacyTime, /every day \(not 2 hours from when you approved it\)/);
  assert.match(legacyOpen, /Open: close only what I choose/, "the new lowest level is offered");
  assert.match(legacyOpen, /Close only these kinds of site/, "with a switch for each kind of site");
  assert.match(legacyOpen, /Adult sites/);
  assert.doesNotMatch(legacyOpen, /Also close these in every browser/, "the Medium/Strict extras card is not shown for it");
  assert.match(legacyBlocking, /Blocked screens/, "and the list of blocked screens, with Remove, stays below it");
  assert.match(seen["youtube/music"], /Approve music/, "YouTube Music: search and approve songs, artists and albums");
  assert.match(seen["youtube/music"], /Albums and playlists/);
  assert.match(seen["youtube/music"], /Only for songs you approved/);
  assert.match(seen["youtube/music"], /Allowed music/);
  assert.match(seen["youtube/yt"], /Find on YouTube/);
  assert.match(seen["youtube/music"], /Downloaded on the phone \(1\)/, "the songs downloaded on the phone are listed");
  assert.match(seen["youtube/music"], /Test song/);
  assert.match(seen["youtube/music"], /Delete all 1 song from the phone/);
  assert.doesNotMatch(seen["youtube/music"], /Test video/, "videos are on the YouTube tab");
  assert.match(seen["youtube/yt"], /Downloaded on the phone \(1\)/);
  assert.match(seen["youtube/yt"], /Test video/);
  assert.match(seen["youtube/yt"], /Delete from the phone/);
  assert.match(seen["youtube/music"], /Asked for in YouTube Music \(2\)/, "what the phone asked for in Music is on the Music tab");
  assert.match(seen["youtube/music"], /Their message: “can I have his songs please”/, "with the message the child wrote");
  assert.doesNotMatch(seen["youtube/music"], /for my science homework/, "YouTube requests stay on the YouTube tab");
  assert.match(seen["youtube/music"], /YouTube Music album or playlist: Greatest Album/, "an album the child asked for from a search result, by its title");
  assert.match(seen["youtube/yt"], /Asked for in YouTube \(1\)/);
  assert.match(seen["youtube/yt"], /Their message: “for my science homework”/);
  assert.match(seen["overview/status"], /Asked for in YouTube Music/, "and on the Overview, with the other things waiting");
  assert.match(legacy, /Ready-made setups/);
  assert.match(legacy, /Status/);
  assert.match(seen["overview/status"], /Ready-made setups/);
  assert.match(seen["L:overview/status"], /What.s on for this phone/, "Legacy keeps its long status card");
  assert.match(seen["overview/status"], /Websites: Moderate filter/);
  assert.match(seen["overview/status"], /Kosher phone/);
  assert.match(seen["safety/health"], /Health check/);
  assert.match(seen["safety/msgalerts"], /Message alerts/);
  assert.match(seen["safety/screenshots"], /Take a screenshot now/);
  assert.match(seen["L:overview/status"], /Pause web filtering/, "Legacy keeps the pause card on its status page");
  assert.match(seen["internet/appnet"], /Internet only for chosen apps/);
  assert.match(seen["device/location"], /School · within 200 m/);
  assert.match(seen["calls/rules"], /Mom · \+12125551234/);
  assert.match(seen["calls/rules"], /Not active yet/);
  assert.match(seen["calls/calllog"], /Send the call log/);
  assert.match(seen["calls/texts"], /Include what the messages say/);
  assert.match(seen["browser/access"], /Filter schedule/);
  assert.match(seen["L:browser/apps"], /Web pages inside apps/);
  assert.match(seen["calls/contacts"], /Show the phone's contacts here/);
  assert.match(seen["apps/regular"], /Close Status/);
  assert.match(seen.settings, /Other parents/);
  assert.match(seen.settings, /Get notifications on this device/);
  // The Windows computer's page, every tab.
  for (const wt of ["filter", "programs", "history", "computer"]) {
    seen["win/" + wt] = tryRender("win/" + wt, `tab='devices';openId=null;openBrowserId=null;openWinId=windevices[0].id;winTab='${wt}';render()`);
  }
  assert.match(seen["win/filter"], /On and working/);
  assert.match(seen["win/filter"], /Approved sites only/);
  assert.match(seen["win/filter"], /Switch to email only/);
  assert.match(seen["win/filter"], /administrators: Dad/);
  assert.match(seen["win/programs"], /Close VPN and proxy programs/);
  assert.match(seen["win/history"], /wikipedia\.org/);
  assert.match(seen["win/computer"], /KIDS-PC/);
  assert.match(seen["win/filter"], /What.s on for this computer/);
  assert.match(seen["win/computer"], /Protect LockGuard from administrators/);
  assert.match(seen["browser/history"], /Keep a list of the sites/);
  assert.match(seen["internet/appnet"], /Filtering per app/);
  assert.match(seen.settings, /Weekly report/);
  assert.match(seen.settings, /arrived at or left a place/);
  assert.match(seen["device/device"], /Save as template/);
  assert.match(seen.templates, /Teen phone/);
  assert.match(seen.templates, /Differs from a fresh phone in: .*Browser filter: moderate/, "the template says how it differs from a fresh phone");
  assert.match(seen.settings, /SIM card removed or changed/);
});
