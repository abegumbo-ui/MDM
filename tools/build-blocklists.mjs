// Builds the Browser's category blocklists from open-source domain lists and writes them in the
// compact form BlockList.java reads (see that file for the format).
//
//   node tools/build-blocklists.mjs <output folder>
//
// Run weekly by .github/workflows/blocklists.yml, which publishes the output to the "blocklists"
// release. Phones download manifest.json first, and only fetch a category file when its sha256
// changed. The sources are the Block List Project lists (public domain / Unlicense):
// https://github.com/blocklistproject/Lists
import crypto from "node:crypto";
import zlib from "node:zlib";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SRC = "https://raw.githubusercontent.com/blocklistproject/Lists/main/";
// The topic lists (news, shopping, games...) come from the Université Toulouse Capitole "UT1" blacklists
// (CC BY-SA 4.0, https://dsi.ut-capitole.fr/blacklists/), plus a short list of the biggest sites of each kind kept here,
// so the best-known ones are always covered even when a source list is late.
const UT1 = "https://dsi.ut-capitole.fr/blacklists/download/";

const NEWS_SEEDS = "wsj.com nytimes.com cnn.com foxnews.com bbc.com bbc.co.uk washingtonpost.com usatoday.com nbcnews.com cbsnews.com abcnews.go.com npr.org reuters.com apnews.com bloomberg.com forbes.com theguardian.com huffpost.com politico.com thehill.com newsweek.com time.com latimes.com nypost.com dailymail.co.uk msn.com news.google.com yahoo.com/news breitbart.com axios.com vox.com theatlantic.com newyorker.com businessinsider.com cnbc.com ft.com economist.com timesofisrael.com haaretz.com jpost.com ynetnews.com israelhayom.com foxbusiness.com nbcnewyork.com nydailynews.com sfchronicle.com chicagotribune.com boston.com independent.co.uk telegraph.co.uk sky.com aljazeera.com dw.com vice.com buzzfeednews.com slate.com salon.com thedailybeast.com rollingstone.com espn.com".split(" ").map((x) => x.split("/")[0]);
const SHOPPING_SEEDS = "temu.com amazon.com amazon.co.uk ebay.com aliexpress.com alibaba.com shein.com walmart.com target.com etsy.com wish.com bestbuy.com costco.com homedepot.com lowes.com wayfair.com overstock.com macys.com kohls.com nordstrom.com zappos.com newegg.com ikea.com shopify.com temu.app fashionnova.com zara.com hm.com nike.com adidas.com sephora.com ulta.com chewy.com cvs.com walgreens.com dollargeneral.com shopee.com mercari.com poshmark.com depop.com rakuten.com groupon.com lightinthebox.com dhgate.com banggood.com jd.com taobao.com tmall.com".split(" ");
const GAMES_SEEDS = "roblox.com minecraft.net steampowered.com steamcommunity.com epicgames.com fortnite.com ea.com blizzard.com battle.net twitch.tv xbox.com playstation.com nintendo.com miniclip.com poki.com crazygames.com y8.com friv.com addictinggames.com kongregate.com itch.io leagueoflegends.com riotgames.com supercell.com king.com zynga.com chess.com lichess.org".split(" ");
const VIDEO_SEEDS = "netflix.com hulu.com disneyplus.com max.com hbomax.com primevideo.com peacocktv.com paramountplus.com crunchyroll.com tubitv.com pluto.tv vimeo.com dailymotion.com twitch.tv spotify.com soundcloud.com pandora.com".split(" ");

// What each category is made of, and which categories each filter level turns on. Phones read the
// `tiers` out of manifest.json, so changing what Moderate/Strict mean never needs an app update.
export const CATEGORIES = {
  adult: { label: "Adult sites", sources: ["porn.txt"] },
  gambling: { label: "Gambling", sources: ["gambling.txt"] },
  drugs: { label: "Drugs and vaping", sources: ["drugs.txt", "vaping.txt"] },
  scams: { label: "Scams, phishing and ransomware", sources: ["phishing.txt", "fraud.txt", "scam.txt", "ransomware.txt"] },
  piracy: { label: "Piracy and torrents", sources: ["piracy.txt", "torrent.txt"] },
  social: { label: "Social media", sources: ["facebook.txt", "twitter.txt", "tiktok.txt"] },
  ads: { label: "Ads", sources: ["ads.txt"] },
  // The kinds of site a parent may also choose to close, in every browser (never in other apps).
  news: { label: "News", ut1: ["press"], seeds: NEWS_SEEDS },
  shopping: { label: "Shopping", ut1: ["shopping"], seeds: SHOPPING_SEEDS },
  games: { label: "Games", ut1: ["games"], seeds: GAMES_SEEDS },
  video: { label: "Video and streaming", ut1: ["audio-video"], seeds: VIDEO_SEEDS },
  dating: { label: "Dating", ut1: ["dating"], seeds: [] },
};
// Closed only inside browsers: a lookup does not say which app asked, and a filter must not break an app the person is allowed to use.
export const BROWSER_ONLY = ["social", "news", "shopping", "games", "video", "dating"];

/** The "domains" file of a gzipped tar of one UT1 category. */
export function readTarFile(gz, wanted) {
  const tar = zlib.gunzipSync(gz);
  let pos = 0;
  while (pos + 512 <= tar.length) {
    const name = tar.toString("latin1", pos, pos + 100).replace(/\0.*$/, "");
    if (!name) break;
    const size = parseInt(tar.toString("latin1", pos + 124, pos + 136).replace(/\0.*$/, "").trim() || "0", 8);
    const body = pos + 512;
    if (name === wanted || name.endsWith("/" + wanted)) return tar.toString("utf8", body, body + size);
    pos = body + Math.ceil(size / 512) * 512;
  }
  return null;
}
export const TIERS = {
  moderate: ["adult", "gambling", "drugs", "scams", "piracy"],
  strict: ["adult", "gambling", "drugs", "scams", "piracy", "social"],
  // "Block ads" on top of a level (phones ask for these; older ones never do).
  "moderate+ads": ["adult", "gambling", "drugs", "scams", "piracy", "ads"],
  // "Open: no filter" with "Block ads" on (our Browser): only the ad servers.
  ads: ["ads"],
  "strict+ads": ["adult", "gambling", "drugs", "scams", "piracy", "social", "ads"],
};

const DOMAIN_RE = /^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$/;

/** One list line -> a lower-case domain, or null (comments, blanks, IP addresses, junk). */
export function parseLine(line) {
  let s = line.trim();
  if (!s || s.startsWith("#") || s.startsWith("!")) return null;
  s = s.split("#")[0].trim();
  const parts = s.split(/\s+/);
  s = parts[parts.length - 1].toLowerCase(); // "0.0.0.0 example.com" and "example.com" both end in the domain
  s = s.replace(/^\|\|/, "").replace(/\^.*$/, "").replace(/\.$/, "");
  if (s.startsWith("www.")) s = s.slice(4);
  if (!s.includes(".") || s.length > 253 || !DOMAIN_RE.test(s)) return null;
  if (/^\d+(\.\d+){3}$/.test(s) || s === "localhost") return null;
  return s;
}

export const hash64 = (domain) => crypto.createHash("sha256").update(domain, "utf8").digest().readBigUInt64BE(0);

/** Lines of text -> the finished binary file (Buffer). */
export function buildBinary(lines) {
  const set = new Set();
  for (const l of lines) {
    const d = parseLine(l);
    if (d) set.add(hash64(d));
  }
  const arr = new BigUint64Array(set.size);
  let i = 0;
  for (const h of set) arr[i++] = h;
  arr.sort(); // numeric, unsigned
  const out = Buffer.alloc(8 + arr.length * 8);
  out.write("MDB1", 0, "latin1");
  out.writeUInt32BE(arr.length, 4);
  for (let k = 0; k < arr.length; k++) out.writeBigUInt64BE(arr[k], 8 + k * 8);
  return out;
}

async function main() {
  const outDir = process.argv[2];
  if (!outDir) throw new Error("usage: node tools/build-blocklists.mjs <output folder>");
  fs.mkdirSync(outDir, { recursive: true });
  const manifest = {
    version: new Date().toISOString().replace(/[-:]/g, "").slice(0, 13), // e.g. 20261109T04
    builtAt: new Date().toISOString(),
    credit: "Block List Project (https://github.com/blocklistproject/Lists), Unlicense; topic lists: UT1 blacklists, Universite Toulouse Capitole (https://dsi.ut-capitole.fr/blacklists/), CC BY-SA 4.0",
    tiers: TIERS,
    browserOnly: BROWSER_ONLY,
    categories: {},
  };
  for (const [name, cat] of Object.entries(CATEGORIES)) {
    const lines = [];
    for (const src of cat.sources || []) {
      const r = await fetch(SRC + src);
      if (!r.ok) throw new Error(`could not download ${src}: ${r.status}`);
      for (const l of (await r.text()).split(/\r?\n/)) lines.push(l); // a loop: spreading 500k lines overflows the stack
    }
    for (const src of cat.ut1 || []) {
      const r = await fetch(UT1 + src + ".tar.gz");
      if (!r.ok) throw new Error(`could not download UT1 ${src}: ${r.status}`);
      const text = readTarFile(Buffer.from(await r.arrayBuffer()), "domains");
      if (!text) throw new Error(`UT1 ${src} has no domains file`);
      for (const l of text.split(/\r?\n/)) lines.push(l);
    }
    for (const d of cat.seeds || []) lines.push(d);
    const bin = buildBinary(lines);
    const file = `${name}.bin`;
    fs.writeFileSync(path.join(outDir, file), bin);
    const count = (bin.length - 8) / 8;
    // A category that suddenly came out nearly empty means the source moved or broke: refuse to
    // publish it rather than quietly shipping a filter that blocks nothing.
    if (count < 50) throw new Error(`${name} has only ${count} entries; not publishing`);
    manifest.categories[name] = { label: cat.label, file, count, bytes: bin.length, sha256: crypto.createHash("sha256").update(bin).digest("hex") };
    console.log(`${name}: ${count} domains, ${(bin.length / 1048576).toFixed(1)} MB`);
  }
  fs.writeFileSync(path.join(outDir, "manifest.json"), JSON.stringify(manifest, null, 1));
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}
