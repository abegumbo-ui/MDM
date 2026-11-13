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
