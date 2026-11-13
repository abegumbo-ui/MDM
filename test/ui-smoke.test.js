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
    siteRequests: [{ url: "https://example.com/page" }],
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
  const groups = vm.runInContext("JSON.stringify(GROUPS.map(function(g){return [g.key,g.subs.map(function(s){return s[0]})]}))", ctx);
  for (const [g, subs] of JSON.parse(groups)) {
    for (const sub of subs) {
      seen[g + "/" + sub] = tryRender(g + "/" + sub, `tab='devices';openId=devices[0].id;deviceSection='${g}';subs['${g}']='${sub}';render()`);
    }
  }
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
  assert.match(seen["browser/apps"], /No incognito tabs/);
  assert.match(seen["browser/apps"], /Also locked in Chrome from the settings above: SafeSearch, YouTube Restricted Mode, no pictures/);
  assert.match(seen["browser/summary"], /What this phone can open/);
  assert.match(seen["browser/summary"], /Moderate/);
  assert.match(seen["browser/summary"], /He can search YouTube and watch/);
  assert.match(seen["browser/summary"], /browser category filter/, "the phone's own filter log line is shown");
  assert.match(seen["browser/access"], /What can they open on the web/, "one page: the level, with what each closes");
  assert.match(seen["browser/access"], /Also close these in every browser/, "the News / Shopping / Games ... boxes are on the Legacy page too");
  assert.match(seen["browser/access"], /Safe browsing/);
  assert.match(seen["browser/access"], /SafeSearch/);
  assert.match(seen["browser/access"], /YouTube Restricted Mode everywhere/);
  assert.match(seen["browser/access"], /Block pictures on web pages/);
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
  assert.match(seen["browser/sites"], /Site requests \(1\)/);
  assert.match(seen["browser/sites"], /YouTube channel/);
  assert.match(seen["browser/apps"], /Make this the only browser/);
  assert.match(seen["browser/inspect"], /Never look inside these sites/);
  assert.match(seen["device/usermenu"], /Open Google Play Store/);
  assert.match(seen["br/web"], /Web access/, "a standalone Browser still has its filter card");
  assert.match(seen.list, /Pixel/);
  assert.match(seen["browser/access"], /Blocked words \(2\)/);
  assert.match(seen["browser/access"], /Block image search/);
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
  assert.match(seen.list, /Add device/);
  assert.match(seen.list, /Screen time today: 2 h 14 min of 3 h/, "the front page is one card for each device");
  assert.match(seen.list, /1 request|3 requests|4 requests/);
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
  const legacyOff = tryRender("legacy: everything is off", "devices[0].config.restrictionsOffUntil=Date.now()+3600000;devices[0].config.restrictionsOffSince=Date.now();uiMode='legacy';deviceSection='overview';subs['overview']='status';tab='devices';openId=devices[0].id;render()");
  tryRender("back to the new look", "devices[0].config.restrictionsOffUntil=0;uiMode='new'");
  assert.match(legacy, /Turn off all restrictions for a while/, "the timed all-off card asks how long");
  assert.match(legacyOff, /All restrictions are OFF/);
  assert.match(legacyOff, /Turn everything back on now/);
  assert.match(legacyBrowser, /What can they open on the web/, "Legacy has the one clear web page too");
  assert.match(legacyBrowser, /Also close these in every browser/);
  assert.match(legacyBrowser, /Strict/);
  assert.doesNotMatch(legacyBrowser, /Web access/, "the old pile of cards is folded into one page");
  assert.match(legacyApps, /Internet for this app/, "per-app internet switch right on the Apps list");
  assert.match(legacy, /Ready-made setups/);
  assert.match(legacy, /Status/);
  assert.match(seen["overview/status"], /Ready-made setups/);
  assert.match(seen["overview/status"], /What.s on for this phone/);
  assert.match(seen["overview/status"], /Websites: Moderate filter/);
  assert.match(seen["overview/status"], /Kosher phone/);
  assert.match(seen["safety/health"], /Health check/);
  assert.match(seen["safety/msgalerts"], /Message alerts/);
  assert.match(seen["safety/screenshots"], /Take a screenshot now/);
  assert.match(seen["overview/status"], /Pause web filtering/);
  assert.match(seen["internet/appnet"], /Internet only for chosen apps/);
  assert.match(seen["device/location"], /School · within 200 m/);
  assert.match(seen["calls/rules"], /Mom · \+12125551234/);
  assert.match(seen["calls/rules"], /Not active yet/);
  assert.match(seen["calls/calllog"], /Send the call log/);
  assert.match(seen["calls/texts"], /Include what the messages say/);
  assert.match(seen["browser/access"], /Filter schedule/);
  assert.match(seen["browser/apps"], /Web pages inside apps/);
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
