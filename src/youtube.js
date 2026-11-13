// YouTube search for the dashboard ("find a channel or a video and allow it"), without a Google
// account or API key: YouTube's own search page carries its results as data (ytInitialData);
// this reads the channels and videos out of it. If YouTube changes that page, the search shows
// nothing and pasting a link still works.

const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36";
// YouTube's own filters for "channels only" / "videos only".
const FILTER = { channel: "EgIQAg%3D%3D", video: "EgIQAQ%3D%3D", playlist: "EgIQAw%3D%3D" };

function initialData(html) {
  const i = html.indexOf("var ytInitialData = ");
  if (i < 0) return null;
  const j = html.indexOf(";</script>", i);
  try {
    return JSON.parse(html.slice(i + 20, j));
  } catch {
    return null;
  }
}

function collect(o, key, out) {
  if (!o || typeof o !== "object") return out;
  if (o[key]) out.push(o[key]);
  for (const v of Object.values(o)) collect(v, key, out);
  return out;
}

const text = (t) => (t && (t.simpleText || (t.runs || []).map((r) => r.text).join(""))) || "";
const handleOf = (endpoint) => {
  const u = endpoint && endpoint.browseEndpoint && endpoint.browseEndpoint.canonicalBaseUrl;
  return u && /^\/@[A-Za-z0-9._-]{3,30}$/.test(u) ? u.slice(1).toLowerCase() : null;
};
const https = (u) => (u && u.startsWith("//") ? "https:" + u : u || "");
const UC_ID = /^UC[A-Za-z0-9_-]{22}$/;
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const PLAYLIST_ID = /^(PL|OLAK5uy_|RD|UU|FL)[A-Za-z0-9_-]{8,60}$/;

/** Channels or videos found on a YouTube search results page. */
export function parseYoutubeSearch(html, kind) {
  const data = initialData(html);
  if (!data) return [];
  if (kind === "playlist") return parsePlaylistCards(data);
  if (kind === "channel" || kind === "artist") {
    return collect(data, "channelRenderer", [])
      .map((c) => {
        const handle = handleOf(c.navigationEndpoint);
        const browseId = (c.navigationEndpoint && c.navigationEndpoint.browseEndpoint && c.navigationEndpoint.browseEndpoint.browseId) || c.channelId || "";
        const thumbs = (c.thumbnail && c.thumbnail.thumbnails) || [];
        // YouTube shows "@handle" in subscriberCountText and the subscriber count in videoCountText.
        const counts = [text(c.subscriberCountText), text(c.videoCountText)].filter((s) => !s.startsWith("@"));
        const base = { type: "channel", title: text(c.title).slice(0, 100), thumb: https((thumbs[thumbs.length - 1] || {}).url), subs: counts[0] || "" };
        if (handle) return kind === "artist" ? { ...base, handle, id: handle } : { ...base, handle };
        // Artists can be channels with no @name (for example "Artist - Topic"): their songs are still listed by channel id.
        return kind === "artist" && UC_ID.test(browseId) ? { ...base, handle: null, id: browseId } : null;
      })
      .filter(Boolean)
      .slice(0, 20);
  }
  return collect(data, "videoRenderer", [])
    .map((v) => {
      if (!/^[A-Za-z0-9_-]{11}$/.test(v.videoId || "")) return null;
      const owner = (v.ownerText && v.ownerText.runs && v.ownerText.runs[0]) || {};
      return {
        type: "video",
        id: v.videoId,
        title: text(v.title).slice(0, 150),
        channel: (owner.text || "").slice(0, 100),
        handle: handleOf(owner.navigationEndpoint),
        thumb: `https://i.ytimg.com/vi/${v.videoId}/mqdefault.jpg`,
        length: text(v.lengthText),
        views: text(v.viewCountText),
      };
    })
    .filter(Boolean)
    .slice(0, 20);
}

/** Searches YouTube from the dashboard's server. kind: "channel" or "video". */
export async function youtubeSearch(q, kind, fetcher = fetch) {
  const query = String(q || "").trim().slice(0, 100);
  if (!query) return [];
  const k = kind === "video" ? "video" : kind === "playlist" ? "playlist" : kind === "artist" ? "artist" : "channel";
  const url = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}&sp=${FILTER[k === "artist" ? "channel" : k]}&hl=en&gl=US`;
  const r = await fetcher(url, { headers: { "user-agent": UA, "accept-language": "en-US,en;q=0.9", cookie: "SOCS=CAI; CONSENT=YES+1" } });
  if (!r.ok) return [];
  return parseYoutubeSearch(await r.text(), k);
}

// ---- artists, albums and playlists: YouTube's newer "lockup" cards ----

const lockupMeta = (l) => (l && l.metadata && l.metadata.lockupMetadataViewModel) || {};
const lockupTitle = (l) => String((lockupMeta(l).title && lockupMeta(l).title.content) || "").slice(0, 150);
const lockupRows = (l) => ((((lockupMeta(l).metadata || {}).contentMetadataViewModel || {}).metadataRows) || []).map((r) => (r.metadataParts || []).map((p) => (p.text && p.text.content) || "").filter(Boolean));
/** The first picture address anywhere inside a card (video thumbnail, playlist collage). */
function firstPicture(o, depth = 0) {
  if (!o || typeof o !== "object" || depth > 8) return "";
  if (typeof o.url === "string" && /^https:\/\/(i|i9|yt3)\.(ytimg|ggpht)\.com\//.test(o.url)) return o.url;
  for (const v of Object.values(o)) {
    const f = firstPicture(v, depth + 1);
    if (f) return f;
  }
  return "";
}

/** The videos on a channel's Videos page or inside a playlist: [{type:"video", id, title, channel, thumb}] (at most 100, no repeats). */
export function parseLockupVideos(html) {
  const data = initialData(html);
  if (!data) return [];
  const seen = new Set();
  const out = [];
  for (const l of collect(data, "lockupViewModel", [])) {
    if (l.contentType !== "LOCKUP_CONTENT_TYPE_VIDEO" || !VIDEO_ID.test(l.contentId || "") || seen.has(l.contentId)) continue;
    seen.add(l.contentId);
    out.push({ type: "video", id: l.contentId, title: lockupTitle(l), channel: (lockupRows(l)[0] || [""])[0].slice(0, 100), thumb: `https://i.ytimg.com/vi/${l.contentId}/mqdefault.jpg` });
    if (out.length >= 100) break;
  }
  return out;
}

/** Playlist and album cards on a playlist-filtered search page. */
function parsePlaylistCards(data) {
  const out = [];
  const seen = new Set();
  for (const l of collect(data, "lockupViewModel", [])) {
    if (!/PLAYLIST|ALBUM/.test(l.contentType || "") || !PLAYLIST_ID.test(l.contentId || "") || seen.has(l.contentId)) continue;
    seen.add(l.contentId);
    const rows = lockupRows(l);
    out.push({ type: "playlist", id: l.contentId, title: lockupTitle(l), channel: (rows[0] || [""])[0].slice(0, 100), info: (rows[1] || []).join(" · ").slice(0, 80), thumb: firstPicture(l.contentImage) });
  }
  return out.slice(0, 20);
}

const titleOf = (html, key) => {
  const data = initialData(html);
  const m = data && collect(data, key, [])[0];
  return m ? String(m.title || "").slice(0, 100) : "";
};

/** Albums on a channel's Releases page (the auto-made "Artist - Topic" channels have no videos, only albums). */
export function parseAlbumCards(html) {
  const data = initialData(html);
  return data ? parsePlaylistCards(data) : [];
}

/** One artist: their songs (the channel's Videos page) and their albums (its Releases page). id is "@name" or a "UC..." channel id. */
export async function youtubeChannelVideos(id, fetcher = fetch) {
  const who = String(id || "").trim();
  const path = /^@[A-Za-z0-9._-]{3,30}$/.test(who) ? who : UC_ID.test(who) ? "channel/" + who : null;
  if (!path) return { title: "", videos: [], albums: [] };
  const get = async (tab) => {
    const r = await fetcher(`https://www.youtube.com/${path}/${tab}?hl=en&gl=US`, { headers: { "user-agent": UA, "accept-language": "en-US,en;q=0.9", cookie: "SOCS=CAI; CONSENT=YES+1" } });
    return r.ok ? await r.text() : "";
  };
  const [videosHtml, releasesHtml] = await Promise.all([get("videos"), get("releases")]);
  return { title: titleOf(videosHtml || releasesHtml, "channelMetadataRenderer"), videos: videosHtml ? parseLockupVideos(videosHtml) : [], albums: releasesHtml ? parseAlbumCards(releasesHtml) : [] };
}

/** Songs of one album or playlist. */
export async function youtubePlaylistVideos(id, fetcher = fetch) {
  const list = String(id || "").trim();
  if (!PLAYLIST_ID.test(list)) return { title: "", videos: [], albums: [] };
  const r = await fetcher(`https://www.youtube.com/playlist?list=${encodeURIComponent(list)}&hl=en&gl=US`, { headers: { "user-agent": UA, "accept-language": "en-US,en;q=0.9", cookie: "SOCS=CAI; CONSENT=YES+1" } });
  if (!r.ok) return { title: "", videos: [], albums: [] };
  const html = await r.text();
  return { title: titleOf(html, "playlistMetadataRenderer"), videos: parseLockupVideos(html), albums: [] };
}
