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
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SRC = "https://raw.githubusercontent.com/blocklistproject/Lists/main/";

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
};
export const TIERS = {
  moderate: ["adult", "gambling", "drugs", "scams", "piracy"],
  strict: ["adult", "gambling", "drugs", "scams", "piracy", "social"],
  // "Block ads" on top of a level (phones ask for these; older ones never do).
  "moderate+ads": ["adult", "gambling", "drugs", "scams", "piracy", "ads"],
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
    credit: "Block List Project (https://github.com/blocklistproject/Lists), Unlicense",
    tiers: TIERS,
    categories: {},
  };
  for (const [name, cat] of Object.entries(CATEGORIES)) {
    const lines = [];
    for (const src of cat.sources) {
      const r = await fetch(SRC + src);
      if (!r.ok) throw new Error(`could not download ${src}: ${r.status}`);
      for (const l of (await r.text()).split(/\r?\n/)) lines.push(l); // a loop: spreading 500k lines overflows the stack
    }
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
