import test from "node:test";
import assert from "node:assert/strict";
import zlib from "node:zlib";
import { buildBinary, hash64, parseLine, readTarFile, TIERS, CATEGORIES, BROWSER_ONLY } from "../tools/build-blocklists.mjs";

// A plain-JS reader of the same file format BlockList.java reads, to prove the builder's output is valid.
function contains(bin, domain) {
  assert.equal(bin.toString("latin1", 0, 4), "MDB1");
  const n = bin.readUInt32BE(4);
  assert.equal(bin.length, 8 + n * 8, "size matches the count");
  const h = hash64(domain);
  let lo = 0, hi = n - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1;
    const v = bin.readBigUInt64BE(8 + mid * 8);
    if (v === h) return true;
    if (v < h) lo = mid + 1;
    else hi = mid - 1;
  }
  return false;
}

test("list lines are parsed from every common format", () => {
  assert.equal(parseLine("0.0.0.0 Bad-Site.COM"), "bad-site.com");
  assert.equal(parseLine("127.0.0.1\twww.evil.example.org # trailing comment"), "evil.example.org");
  assert.equal(parseLine("plain.example.net"), "plain.example.net");
  assert.equal(parseLine("||adblock.example.io^"), "adblock.example.io");
  for (const junk of ["", "   ", "# comment", "! comment", "0.0.0.0 localhost", "0.0.0.0 0.0.0.0", "no_dots", "bad domain.com extra!", "-.com"]) {
    assert.equal(parseLine(junk), null, JSON.stringify(junk));
  }
});

test("the built file is sorted, de-duplicated and findable", () => {
  const bin = buildBinary(["0.0.0.0 one.example.com", "two.example.org", "0.0.0.0 one.example.com", "# x", "www.three.example.net"]);
  assert.equal(bin.readUInt32BE(4), 3, "three unique domains");
  for (const d of ["one.example.com", "two.example.org", "three.example.net"]) assert.ok(contains(bin, d), d);
  assert.ok(!contains(bin, "four.example.com"));
  let prev = -1n;
  for (let i = 0; i < 3; i++) {
    const v = bin.readBigUInt64BE(8 + i * 8);
    assert.ok(v > prev, "ascending");
    prev = v;
  }
});

test("the fingerprint matches the one BlockList.java is checked against", () => {
  // Hard-coded in BlockListCheck.java too: if either side changes how it hashes, both checks fail.
  assert.equal(hash64("example.com"), 0xa379a6f6eeafb9a5n);
});

test("every tier only uses categories that exist, and strict includes moderate", () => {
  for (const [tier, cats] of Object.entries(TIERS)) for (const c of cats) assert.ok(CATEGORIES[c], `${tier}: ${c}`);
  for (const c of TIERS.moderate) assert.ok(TIERS.strict.includes(c));
  assert.ok(TIERS.strict.length > TIERS.moderate.length);
});

test("the topic lists (news, shopping, games, video, dating) are read from a UT1 archive, and the biggest sites are always in", () => {
  // A tiny gzipped tar like UT1's: press/domains and press/usage.
  const entry = (name, text) => {
    const body = Buffer.from(text, "utf8");
    const h = Buffer.alloc(512);
    h.write(name, 0, "latin1");
    h.write(body.length.toString(8).padStart(11, "0") + "\0", 124, "latin1");
    return Buffer.concat([h, body, Buffer.alloc((512 - (body.length % 512)) % 512)]);
  };
  const gz = zlib.gzipSync(Buffer.concat([entry("press/usage", "white\n"), entry("press/domains", "wsj.com\nexample-news.org\n"), Buffer.alloc(1024)]));
  assert.equal(readTarFile(gz, "domains"), "wsj.com\nexample-news.org\n");
  assert.equal(readTarFile(gz, "nothing"), null);
  for (const k of ["news", "shopping", "games", "video", "dating"]) {
    assert.ok(CATEGORIES[k], k + " is a category");
    assert.ok(BROWSER_ONLY.includes(k), k + " closes only in browsers");
    for (const d of CATEGORIES[k].seeds || []) assert.ok(parseLine(d), "a usable domain: " + d);
  }
  assert.ok(CATEGORIES.news.seeds.includes("wsj.com") && CATEGORIES.news.seeds.includes("nytimes.com"));
  assert.ok(CATEGORIES.shopping.seeds.includes("temu.com") && CATEGORIES.shopping.seeds.includes("amazon.com"));
  assert.ok(BROWSER_ONLY.includes("social"));
  for (const level of ["moderate", "strict"]) for (const c of TIERS[level]) assert.ok(CATEGORIES[c], level + " names a real category: " + c);
  assert.ok(!TIERS.moderate.includes("news") && !TIERS.strict.includes("shopping"), "the two levels do not silently include the new topics");
});