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

// ---- artists, albums and playlists (YouTube's newer "lockup" cards) ----
import { parseLockupVideos, youtubeChannelVideos, youtubePlaylistVideos } from "../src/youtube.js";
const lockup = (id, type, title, rows) => ({ lockupViewModel: { contentId: id, contentType: type, contentImage: { thumbnailViewModel: { image: { sources: [{ url: `https://i.ytimg.com/vi/${id}/hqdefault.jpg` }] } } },
  metadata: { lockupMetadataViewModel: { title: { content: title }, metadata: { contentMetadataViewModel: { metadataRows: rows.map((r) => ({ metadataParts: r.map((t) => ({ text: { content: t } })) })) } } } } } });
const songsPage = page({ a: [lockup("9xUHOpqeYLU", "LOCKUP_CONTENT_TYPE_VIDEO", "Niggun", [["Avraham Fried"], ["938K views", "5y ago"]]), lockup("9xUHOpqeYLU", "LOCKUP_CONTENT_TYPE_VIDEO", "Repeat", [["x"]]), lockup("bad id!", "LOCKUP_CONTENT_TYPE_VIDEO", "Broken", [["x"]]), lockup("PLq5mSwVohMynUfgopz08woNGiVKKBKNCb", "LOCKUP_CONTENT_TYPE_PLAYLIST", "A mix", [["Someone"]])],
  b: { channelMetadataRenderer: { title: "Avraham Fried" }, playlistMetadataRenderer: { title: "Best of" } } });
const playlistSearch = page({ a: [lockup("PLq5mSwVohMynUfgopz08woNGiVKKBKNCb", "LOCKUP_CONTENT_TYPE_PLAYLIST", "Avraham Fried Greatest", [["Avraham Fried"], ["Playlist", "42 videos"]]), lockup("abc", "LOCKUP_CONTENT_TYPE_VIDEO", "A video", [["x"]]), lockup("PLshort", "LOCKUP_CONTENT_TYPE_PLAYLIST", "bad id", [["x"]])] });
const artistPage = page({ contents: { a: [{ channelRenderer: { title: { simpleText: "Avraham Fried" }, navigationEndpoint: { browseEndpoint: { canonicalBaseUrl: "/@Avraham_Fried", browseId: "UC1" } }, subscriberCountText: { simpleText: "@Avraham_Fried" }, videoCountText: { simpleText: "1M subscribers" } } },
  { channelRenderer: { title: { simpleText: "Avraham Fried - Topic" }, navigationEndpoint: { browseEndpoint: { canonicalBaseUrl: "/channel/UC1-uPfCV7ZcLCSou9v_Vclw", browseId: "UC1-uPfCV7ZcLCSou9v_Vclw" } } } },
  { channelRenderer: { title: { simpleText: "Nobody" }, navigationEndpoint: { browseEndpoint: { browseId: "not-an-id" } } } }] } });

test("artists: a name search keeps channels with no @name (Topic channels) by their channel id; plain channel search still leaves them out", () => {
  const artists = parseYoutubeSearch(artistPage, "artist");
  assert.deepEqual(artists.map((a) => [a.title, a.id, a.handle]), [["Avraham Fried", "@avraham_fried", "@avraham_fried"], ["Avraham Fried - Topic", "UC1-uPfCV7ZcLCSou9v_Vclw", null]]);
  assert.deepEqual(parseYoutubeSearch(artistPage, "channel").map((a) => a.handle), ["@avraham_fried"]);
});

test("an artist's or album's songs are read from the lockup cards: videos only, no repeats, no broken ids", () => {
  const v = parseLockupVideos(songsPage);
  assert.equal(v.length, 1);
  assert.deepEqual(v[0], { type: "video", id: "9xUHOpqeYLU", title: "Niggun", channel: "Avraham Fried", thumb: "https://i.ytimg.com/vi/9xUHOpqeYLU/mqdefault.jpg" });
  assert.deepEqual(parseLockupVideos("<html>nothing</html>"), []);
});

test("album / playlist search lists playlist cards with a picture and who made them", () => {
  const p = parseYoutubeSearch(playlistSearch, "playlist");
  assert.equal(p.length, 1, "videos and malformed list ids are left out");
  assert.deepEqual(p[0], { type: "playlist", id: "PLq5mSwVohMynUfgopz08woNGiVKKBKNCb", title: "Avraham Fried Greatest", channel: "Avraham Fried", info: "Playlist · 42 videos", thumb: "https://i.ytimg.com/vi/PLq5mSwVohMynUfgopz08woNGiVKKBKNCb/hqdefault.jpg" });
});

test("listing an artist's or a playlist's songs asks YouTube the right page and refuses odd ids", async () => {
  const asked = [];
  const fake = async (u) => { asked.push(u); return new Response(songsPage, { status: 200 }); };
  const a = await youtubeChannelVideos("@Avraham_Fried", fake);
  assert.ok(asked.some((u) => /youtube\.com\/@Avraham_Fried\/videos/.test(u)) && asked.some((u) => /youtube\.com\/@Avraham_Fried\/releases/.test(u)), "songs and albums");
  assert.equal(a.title, "Avraham Fried");
  assert.equal(a.videos.length, 1);
  assert.equal(a.albums.length, 1, "the albums on the Releases page (here the page also carries one playlist card)");
  await youtubeChannelVideos("UC1-uPfCV7ZcLCSou9v_Vclw", fake);
  assert.ok(asked.some((u) => /youtube\.com\/channel\/UC1-uPfCV7ZcLCSou9v_Vclw\/releases/.test(u)));
  const before = asked.length;
  const p = await youtubePlaylistVideos("PLq5mSwVohMynUfgopz08woNGiVKKBKNCb", fake);
  assert.match(asked[before], /playlist\?list=PLq5mSwVohMynUfgopz08woNGiVKKBKNCb/);
  assert.equal(p.title, "Best of");
  assert.deepEqual(await youtubeChannelVideos("../../etc", fake), { title: "", videos: [], albums: [] });
  assert.deepEqual(await youtubePlaylistVideos("nope", fake), { title: "", videos: [], albums: [] });
  assert.equal(asked.length, before + 1, "odd ids never reach YouTube");
});
