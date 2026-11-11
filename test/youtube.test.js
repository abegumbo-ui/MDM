import test from "node:test";
import assert from "node:assert/strict";
import { normalizeSite, normalizeSites, siteAllowed, youtubeChannelHandle, youtubeVideoId } from "../src/policy.js";

const V = "dQw4w9WgXcQ";

test("every way of writing one YouTube video gives the same id", () => {
  for (const u of [
    `https://www.youtube.com/watch?v=${V}`,
    `https://youtube.com/watch?v=${V}&t=42s&list=PLabc`,
    `https://m.youtube.com/watch?v=${V}`,
    `https://youtu.be/${V}`,
    `https://youtu.be/${V}?si=zzz`,
    `https://www.youtube.com/shorts/${V}`,
    `https://www.youtube.com/embed/${V}`,
    `https://www.youtube-nocookie.com/embed/${V}`,
    `https://www.youtube.com/live/${V}?feature=share`,
  ]) assert.equal(youtubeVideoId(u), V, u);
  for (const u of ["https://www.youtube.com/", "https://www.youtube.com/watch", "https://www.youtube.com/watch?v=short", "https://www.youtube.com/results?search_query=x", "https://example.com/watch?v=" + V, "https://www.youtube.com/@someone"]) {
    assert.equal(youtubeVideoId(u), null, u);
  }
});

test("channel pages are recognised by their @handle, case-insensitively", () => {
  assert.equal(youtubeChannelHandle("https://www.youtube.com/@AishDotCom"), "@aishdotcom");
  assert.equal(youtubeChannelHandle("https://m.youtube.com/@AishDotCom/videos"), "@aishdotcom");
  assert.equal(youtubeChannelHandle("youtube.com/@a.b-c_d/featured?x=1"), "@a.b-c_d");
  for (const u of ["https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx", "https://www.youtube.com/c/Name", "https://www.youtube.com/user/Name", "https://example.com/@handle", "https://www.youtube.com/@ab", "https://www.youtube.com/"]) {
    assert.equal(youtubeChannelHandle(u), null, u);
  }
});

test("allowing one video's page allows only that video, not every video", () => {
  const sites = normalizeSites({ a: { type: "exact", url: `https://www.youtube.com/watch?v=${V}` } });
  assert.equal(sites.length, 1);
  assert.ok(siteAllowed(`https://www.youtube.com/watch?v=${V}`, sites));
  assert.ok(siteAllowed(`https://youtu.be/${V}`, sites), "the same video written another way");
  assert.ok(siteAllowed(`https://m.youtube.com/watch?v=${V}&t=9`, sites));
  assert.ok(!siteAllowed("https://www.youtube.com/watch?v=AAAAAAAAAAA", sites), "a different video");
  assert.ok(!siteAllowed("https://www.youtube.com/", sites));
});

test("a YouTube channel entry is accepted only with a real @handle, and never matches by address", () => {
  const ok = normalizeSite({ type: "youtube", url: "https://www.youtube.com/@AishDotCom/videos" });
  assert.equal(ok.type, "youtube");
  assert.equal(ok.id, "@aishdotcom");
  assert.equal(ok.url, "https://www.youtube.com/@aishdotcom");
  assert.equal(normalizeSite({ type: "youtube", id: "@Chabad" }).id, "@chabad");
  for (const bad of [{ type: "youtube", url: "https://www.youtube.com/" }, { type: "youtube", url: "https://example.com/@handle" }, { type: "youtube", id: "nohandle" }, { type: "youtube" }]) {
    assert.equal(normalizeSite(bad), null, JSON.stringify(bad));
  }
  const sites = normalizeSites({ a: { type: "youtube", url: "https://www.youtube.com/@AishDotCom" }, b: { type: "youtube", id: "@aishdotcom" } });
  assert.equal(sites.length, 1, "the same channel twice is one entry");
  assert.ok(!siteAllowed("https://www.youtube.com/@aishdotcom", sites), "address matching never allows it: the Browser decides");
  assert.ok(!siteAllowed(`https://www.youtube.com/watch?v=${V}`, sites));
});

test("the video info lookup validates the link, names the channel, and survives YouTube being unreachable", async () => {
  const { default: worker } = await import("../src/index.js");
  const kv = new Map();
  const env = { ADMIN_PASSWORD: "pw", STATE: { get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null), put: async (k, v) => void kv.set(k, v), delete: async (k) => void kv.delete(k), list: async () => ({ keys: [], list_complete: true }) } };
  const call = (p, c) => worker.fetch(new Request("https://x.test" + p, { headers: { cookie: c } }), env);
  const login = await worker.fetch(new Request("https://x.test/login", { method: "POST", body: new URLSearchParams({ password: "pw" }) }), env);
  const cookie = login.headers.get("set-cookie").split(";")[0];
  assert.equal((await call("/api/youtube-info?url=" + encodeURIComponent("https://example.com/watch?v=dQw4w9WgXcQ"), cookie)).status, 400, "not a YouTube link");
  const realFetch = globalThis.fetch;
  try {
    let asked = "";
    globalThis.fetch = async (u) => { asked = String(u); return new Response(JSON.stringify({ title: "A Torah class", author_name: "Aish", author_url: "https://www.youtube.com/@AishDotCom" })); };
    const ok = await (await call("/api/youtube-info?url=" + encodeURIComponent("https://youtu.be/dQw4w9WgXcQ?si=x"), cookie)).json();
    assert.deepEqual(ok, { videoId: "dQw4w9WgXcQ", title: "A Torah class", author: "Aish", handle: "@aishdotcom" });
    assert.ok(asked.startsWith("https://www.youtube.com/oembed") && asked.includes("dQw4w9WgXcQ"), "asks YouTube for just that video, nothing else the caller supplied");
    globalThis.fetch = async () => new Response("nope", { status: 404 });
    assert.ok((await (await call("/api/youtube-info?url=" + encodeURIComponent("https://youtu.be/dQw4w9WgXcQ"), cookie)).json()).error, "a private/removed video gives a message, not a crash");
    globalThis.fetch = async () => { throw new Error("offline"); };
    assert.ok((await (await call("/api/youtube-info?url=" + encodeURIComponent("https://youtu.be/dQw4w9WgXcQ"), cookie)).json()).error, "YouTube being down gives a message, not a crash");
  } finally {
    globalThis.fetch = realFetch;
  }
});
