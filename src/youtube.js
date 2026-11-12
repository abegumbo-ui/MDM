// YouTube search for the dashboard ("find a channel or a video and allow it"), without a Google
// account or API key: YouTube's own search page carries its results as data (ytInitialData);
// this reads the channels and videos out of it. If YouTube changes that page, the search shows
// nothing and pasting a link still works.

const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36";
// YouTube's own filters for "channels only" / "videos only".
const FILTER = { channel: "EgIQAg%3D%3D", video: "EgIQAQ%3D%3D" };

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

/** Channels or videos found on a YouTube search results page. */
export function parseYoutubeSearch(html, kind) {
  const data = initialData(html);
  if (!data) return [];
  if (kind === "channel") {
    return collect(data, "channelRenderer", [])
      .map((c) => {
        const handle = handleOf(c.navigationEndpoint);
        const thumbs = (c.thumbnail && c.thumbnail.thumbnails) || [];
        // YouTube shows "@handle" in subscriberCountText and the subscriber count in videoCountText.
        const counts = [text(c.subscriberCountText), text(c.videoCountText)].filter((s) => !s.startsWith("@"));
        return handle && { type: "channel", handle, title: text(c.title).slice(0, 100), thumb: https((thumbs[thumbs.length - 1] || {}).url), subs: counts[0] || "" };
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
  const k = kind === "video" ? "video" : "channel";
  const url = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}&sp=${FILTER[k]}&hl=en&gl=US`;
  const r = await fetcher(url, { headers: { "user-agent": UA, "accept-language": "en-US,en;q=0.9", cookie: "SOCS=CAI; CONSENT=YES+1" } });
  if (!r.ok) return [];
  return parseYoutubeSearch(await r.text(), k);
}
