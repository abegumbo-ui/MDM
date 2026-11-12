import test from "node:test";
import assert from "node:assert/strict";
import { parseYoutubeSearch, youtubeSearch } from "../src/youtube.js";

// A small page shaped like YouTube's search results (the real ones carry the same fields).
const page = (data) => `<html><script>var ytInitialData = ${JSON.stringify(data)};</script></html>`;
const channelPage = page({ contents: { a: [{ channelRenderer: {
  channelId: "UCLA", title: { simpleText: "NASA" },
  navigationEndpoint: { browseEndpoint: { canonicalBaseUrl: "/@NASA" } },
  thumbnail: { thumbnails: [{ url: "//yt3.ggpht.com/a=s88" }, { url: "//yt3.ggpht.com/a=s176" }] },
  subscriberCountText: { simpleText: "@NASA" }, videoCountText: { simpleText: "15.1M subscribers" } } },
  { channelRenderer: { title: { simpleText: "Old style" }, navigationEndpoint: { browseEndpoint: { canonicalBaseUrl: "/channel/UCx" } } } }] } });
const videoPage = page({ x: { y: [{ videoRenderer: {
  videoId: "pAKqJxuxDgk", title: { runs: [{ text: "Artemis " }, { text: "Acceleration" }] },
  ownerText: { runs: [{ text: "NASA", navigationEndpoint: { browseEndpoint: { canonicalBaseUrl: "/@NASA" } } }] },
  lengthText: { simpleText: "2:05:15" }, viewCountText: { simpleText: "245,750 views" } } }, { videoRenderer: { videoId: "bad id!" } }] } });

test("YouTube search: channels are read with their @name, picture and subscribers", () => {
  const r = parseYoutubeSearch(channelPage, "channel");
  assert.equal(r.length, 1, "channels without an @name are left out");
  assert.deepEqual(r[0], { type: "channel", handle: "@nasa", title: "NASA", thumb: "https://yt3.ggpht.com/a=s176", subs: "15.1M subscribers" });
});

test("YouTube search: videos are read with title, channel, picture and length", () => {
  const r = parseYoutubeSearch(videoPage, "video");
  assert.equal(r.length, 1, "a broken id is left out");
  assert.equal(r[0].title, "Artemis Acceleration");
  assert.equal(r[0].handle, "@nasa");
  assert.equal(r[0].thumb, "https://i.ytimg.com/vi/pAKqJxuxDgk/mqdefault.jpg");
  assert.equal(r[0].length, "2:05:15");
});

test("YouTube search: asks YouTube's own channel or video search, and survives a changed page", async () => {
  let asked = "";
  const fake = async (u) => { asked = u; return new Response(channelPage, { status: 200 }); };
  const r = await youtubeSearch("nasa space", "channel", fake);
  assert.match(asked, /search_query=nasa%20space&sp=EgIQAg%3D%3D/);
  assert.equal(r[0].handle, "@nasa");
  assert.deepEqual(parseYoutubeSearch("<html>nothing here</html>", "video"), []);
  assert.deepEqual(await youtubeSearch("", "video", fake), []);
});
