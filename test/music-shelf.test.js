import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

// Runs the YouTube Music shelf script (android/sitepolicy/.../youtube_shelf.js) against a stand-in page and player.
const src = fs.readFileSync(new URL("../android/sitepolicy/src/main/resources/com/familymdm/agent/inspect/youtube_shelf.js", import.meta.url), "utf8");

function page(songIds, at, { downloads = [], dl = false } = {}) {
  const timers = [];
  const listeners = {};
  const video = { paused: false, currentTime: 50, listeners: {}, pause() { this.paused = true; }, play() { this.played = (this.played || 0) + 1; }, addEventListener(t, fn) { (this.listeners[t] = this.listeners[t] || []).push(fn); } };
  const created = [];
  const mk = (tag) => { const e = { tag, children: [], style: {}, className: "", textContent: "", appendChild(c) { this.children.push(c); return c; }, setAttribute() {}, getAttribute() { return null; }, remove() {}, isConnected: true, getBoundingClientRect: () => ({ bottom: 48, top: 0 }) }; created.push(e); return e; };
  const moves = [];
  const location = { pathname: "/watch", origin: "https://music.youtube.com", search: "?v=" + at, replace(u) { moves.push(u); } };
  const doc = { createElement: mk, getElementById: () => null, querySelector: (s) => (s === "video" ? video : null), body: mk("body"), head: mk("head"), documentElement: { hasAttribute: () => false }, addEventListener(t, fn) { (listeners[t] = listeners[t] || []).push(fn); } };
  const data = { channels: [], videos: songIds.map((v) => ({ v, t: "Song " + v, i: "" })), ask: true, downloads, music: true, dl, nopics: false };
  const ctx = vm.createContext({ document: doc, window: {}, location, innerHeight: 800, matchMedia: () => ({ matches: true }), setInterval: (fn) => timers.push(fn), __MDM_DATA__: data });
  vm.runInContext(src.replace("__MDM_DATA__", "JSON.parse(" + JSON.stringify(JSON.stringify(data)) + ")"), ctx);
  return {
    moves, video, created,
    tick() { for (const t of timers) t(); },
    ended() { for (const f of video.listeners.ended || []) f({ stopImmediatePropagation() {} }); },
    click(cls) { let stopped = false; for (const f of listeners.click || []) f({ target: { closest: (s) => (s.includes(cls) ? { className: cls } : null) }, preventDefault() {}, stopPropagation() { stopped = true; } }); return stopped; },
    at(id) { location.search = "?v=" + id; },
  };
}

test("YouTube Music: a finished song is followed by the next approved one, and the list goes round and round", () => {
  const p = page(["AAAAAAAAAAA", "BBBBBBBBBBB", "CCCCCCCCCCC"], "AAAAAAAAAAA");
  p.tick();
  p.ended();
  assert.equal(p.moves.pop(), "https://music.youtube.com/watch?v=BBBBBBBBBBB");
  p.at("CCCCCCCCCCC"); p.ended();
  assert.equal(p.moves.pop(), "https://music.youtube.com/watch?v=AAAAAAAAAAA", "after the last one it starts again");
});

test("YouTube Music: Next and Previous stay inside the approved songs", () => {
  const p = page(["AAAAAAAAAAA", "BBBBBBBBBBB", "CCCCCCCCCCC"], "AAAAAAAAAAA");
  assert.equal(p.click("next-button"), true, "the player's own Next is taken over");
  assert.equal(p.moves.pop(), "https://music.youtube.com/watch?v=BBBBBBBBBBB");
  p.click("previous-button");
  assert.equal(p.moves.pop(), "https://music.youtube.com/watch?v=CCCCCCCCCCC", "Previous from the first goes to the last");
});

test("YouTube Music: a song that is not approved is stopped and replaced by an approved one", () => {
  const p = page(["AAAAAAAAAAA", "BBBBBBBBBBB"], "ZZZZZZZZZZZ");
  p.tick();
  assert.equal(p.video.paused, true);
  assert.equal(p.moves.pop(), "https://music.youtube.com/watch?v=AAAAAAAAAAA");
});

test("YouTube Music: with one approved song it plays again and again", () => {
  const p = page(["AAAAAAAAAAA"], "AAAAAAAAAAA");
  p.tick(); p.ended();
  assert.equal(p.video.currentTime, 0);
  assert.equal(p.video.played, 1);
  assert.equal(p.moves.length, 0, "no page change");
});

test("downloaded items can be deleted from the phone even when new downloads are off", () => {
  const p = page(["AAAAAAAAAAA"], "AAAAAAAAAAA", { downloads: [{ v: "DDDDDDDDDDD", t: "Saved song", i: "" }], dl: false });
  p.tick();
  const links = p.created.filter((e) => e.tag === "a");
  assert.ok(links.some((a) => a.className === "dl del"), "a Delete link sits under the downloaded item");
});

// ---- YouTube Music clean-up and Ask buttons (music_clean.js, music_ask.js) against a small stand-in page ----
const readJs = (name) => fs.readFileSync(new URL("../android/sitepolicy/src/main/resources/com/familymdm/agent/inspect/" + name, import.meta.url), "utf8");
class El {
  constructor(tag, text = "", parent = null, attrs = {}) { this.tag = tag; this.textContent = text; this.parentNode = parent; this.attrs = { ...attrs }; this.children = []; this.className = ""; this.style = { setProperty: (k, v) => { this.style[k] = v; }, display: "" }; this.href = attrs.href || ""; if (parent) parent.children.push(this); }
  get tagName() { return this.tag.toUpperCase(); }
  getAttribute(k) { return k in this.attrs ? this.attrs[k] : null; }
  setAttribute(k, v) { this.attrs[k] = v; }
  appendChild(c) { c.parentNode = this; this.children.push(c); return c; }
  insertBefore(n, ref) { n.parentNode = this; const i = this.children.indexOf(ref); this.children.splice(i < 0 ? this.children.length : i, 0, n); return n; }
  get nextSibling() { const i = this.parentNode ? this.parentNode.children.indexOf(this) : -1; return i >= 0 ? this.parentNode.children[i + 1] || null : null; }
  closest(sel) { const tags = sel.split(",").map((s) => s.trim().split(/\s+/).pop()).filter((s) => /^[a-z-]+$/.test(s)); for (let e = this; e; e = e.parentNode) if (tags.includes(e.tag)) return e; return null; }
  querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }
  querySelectorAll(sel) { const tags = sel.split(",").map((s) => s.trim()).filter((s) => /^[a-z-]+$/.test(s)); const hrefs = sel.includes("[href") ? [...sel.matchAll(/\[href\*="([^"]+)"\]/g)].map((m) => m[1]) : []; const out = []; const walk = (e) => { for (const c of e.children) { if (tags.includes(c.tag) || (c.tag === "a" && hrefs.some((h) => c.href.includes(h)))) out.push(c); walk(c); } }; walk(this); return out; }
}
function stand(path = "/", build) {
  const root = new El("html"); const timers = [];
  const doc = { createElement: (t) => new El(t), getElementById: (id) => { const f = (e) => (e.attrs.id === id || e.id === id ? e : e.children.map(f).find(Boolean)); return f(root) || null; }, querySelectorAll: (s) => root.querySelectorAll(s), querySelector: (s) => root.querySelector(s), head: new El("head", "", root), documentElement: root, body: root };
  const win = {}; const loc = { pathname: path, search: "", hash: "", origin: "https://music.youtube.com", href: "" };
  build(root, win);
  return { root, win, loc, doc, timers, ctx: vm.createContext({ document: doc, window: win, location: loc, setInterval: (fn) => timers.push(fn), innerHeight: 800 }), run(name) { vm.runInContext(readJs(name), this.ctx); }, tick() { this.timers.forEach((t) => t()); } };
}
const hidden = (e) => e.style.display === "none";

test("YouTube Music: the three-dot menu loses Help, Terms and Premium; Settings stays", () => {
  let items;
  const s = stand("/", (root) => { items = ["Help & feedback", "Terms & privacy policy", "Get YouTube Premium", "Settings"].map((t) => new El("ytmusic-menu-navigation-item-renderer", t, root)); });
  s.run("music_clean.js"); s.tick();
  assert.deepEqual(items.map(hidden), [true, true, true, false]);
});

test("YouTube Music: Settings keeps General and gets Downloads; Privacy and data and About are gone, but only in Settings", () => {
  let rows, list;
  const s = stand("/music_settings", (root) => { list = new El("div", "", root); rows = ["General", "Privacy and data", "About"].map((t) => new El("ytmusic-setting-category-collection-entry-renderer", t, list)); });
  s.run("music_clean.js"); s.tick();
  assert.deepEqual(rows.map(hidden), [false, true, true]);
  const dl = list.children.find((c) => c.attrs.id === "mdm-dl-row" || c.id === "mdm-dl-row");
  assert.ok(dl, "a Downloads row was added");
  assert.equal(dl.href, "mdmguard://downloads");
  const artist = stand("/channel/UCabcdefghijklmnopqrstuv", (root) => { rows = [new El("ytmusic-setting-category-collection-entry-renderer", "About", root)]; });
  artist.run("music_clean.js"); artist.tick();
  assert.equal(hidden(rows[0]), false, "an artist's own About section is not touched");
});

test("YouTube Music: no Explore tab, Home and Library stay", () => {
  let tabs;
  const s = stand("/", (root) => { tabs = ["Home", "Explore", "Library"].map((t) => new El("ytmusic-pivot-bar-item-renderer", t, root)); });
  s.run("music_clean.js"); s.tick();
  assert.deepEqual(tabs.map(hidden), [false, true, false]);
});

test("YouTube Music: Restricted mode is switched on when the parent forces it, and cannot be turned off there", () => {
  let row, toggle;
  const build = (root, win) => { row = new El("ytmusic-setting-boolean-renderer", "Restricted mode Off", root); toggle = new El("tp-yt-paper-toggle-button", "", row); toggle.checked = false; toggle.clicked = 0; toggle.click = () => { toggle.clicked++; toggle.checked = true; }; };
  const on = stand("/music_settings", (root, win) => { build(root, win); win.__mdmRestrict = true; });
  on.run("music_clean.js"); on.tick(); on.tick();
  assert.equal(toggle.clicked, 1, "switched on once");
  assert.equal(row.style["pointer-events"], "none");
  const off = stand("/music_settings", build);
  off.run("music_clean.js"); off.tick();
  assert.equal(toggle.clicked, 0, "left alone when the parent has not forced it");
});

test("YouTube Music: an Ask button under each song, artist and album that is not approved, sent without leaving the page", () => {
  let list, song, approvedSong, artist;
  const s = stand("/results", (root, win) => {
    list = new El("div", "", root);
    song = new El("ytmusic-responsive-list-item-renderer", "", list); new El("a", "", song, { href: "https://music.youtube.com/watch?v=ZZZZZZZZZZZ&list=RDAMVM" }); new El("yt-formatted-string", "Some song", song);
    approvedSong = new El("ytmusic-responsive-list-item-renderer", "", list); new El("a", "", approvedSong, { href: "https://music.youtube.com/watch?v=AAAAAAAAAAA" });
    artist = new El("ytmusic-two-row-item-renderer", "", list); new El("a", "", artist, { href: "https://music.youtube.com/channel/UCabcdefghijklmnopqrstuv" }); new El("yt-formatted-string", "Some Artist", artist);
    win.__mdmApprovedIds = ["AAAAAAAAAAA"];
  });
  s.run("music_ask.js"); s.tick();
  const rowAfterSong = list.children[list.children.indexOf(song) + 1];
  assert.equal(rowAfterSong.className, "mdm-askrow", "list items get their button row right under them");
  const songBtn = rowAfterSong.children[0];
  assert.equal(songBtn.textContent, "Ask for this song");
  assert.equal(list.children.filter((c) => c.className === "mdm-askrow").length, 1, "the approved song has no button");
  const artistBtn = artist.children.find((c) => c.className === "mdm-ask");
  assert.equal(artistBtn.textContent, "Ask for this artist");
  songBtn.onclick({ preventDefault() {}, stopPropagation() {} });
  assert.match(s.loc.href, /^mdmguard:\/\/request\?u=https%3A%2F%2Fmusic\.youtube\.com%2Fwatch%3Fv%3DZZZZZZZZZZZ&note=Asked%20from%20YouTube%20Music%3A%20Some%20song&stay=1$/);
  assert.equal(songBtn.textContent, "Asked ✓");
});

test("YouTube Music: the song list stops above the bar of the song that is playing, and Library shows the downloaded songs", () => {
  const timers = [];
  const created = [];
  const mk = (tag) => { const e = { tag, children: [], style: {}, className: "", textContent: "", appendChild(c) { this.children.push(c); return c; }, setAttribute() {}, getAttribute() { return null; }, remove() {}, isConnected: true, getBoundingClientRect: () => ({ bottom: 48, top: 0, height: 50 }) }; created.push(e); return e; };
  const body = mk("body");
  const bars = { "ytmusic-pivot-bar-renderer": { top: 740, height: 60 }, "ytmusic-player-bar": { top: 650, height: 90 } };
  const doc = { createElement: mk, getElementById: () => null, querySelector: (s) => (bars[s] ? { getBoundingClientRect: () => bars[s] } : null), body, head: mk("head"), documentElement: { hasAttribute: () => false }, addEventListener() {} };
  const data = { channels: [], videos: [{ v: "AAAAAAAAAAA", t: "A", i: "" }], ask: true, downloads: [{ v: "DDDDDDDDDDD", t: "Saved", i: "" }], music: true, dl: false, nopics: false };
  const ctx = vm.createContext({ document: doc, window: {}, location: { pathname: "/library", origin: "https://music.youtube.com", search: "" }, innerHeight: 800, matchMedia: () => ({ matches: true }), setInterval: (fn) => timers.push(fn), __MDM_DATA__: data });
  vm.runInContext(src.replace("__MDM_DATA__", "JSON.parse(" + JSON.stringify(JSON.stringify(data)) + ")"), ctx);
  const box = created.find((e) => e.id === "mdm-shelf");
  assert.ok(box, "the panel is built on the Library page");
  assert.equal(box.style.bottom, "150px", "800 - 650: above the higher of the two bars");
  assert.ok(created.some((e) => e.tag === "a" && e.className === "dl del"), "the downloaded song is listed, with Delete");
});
