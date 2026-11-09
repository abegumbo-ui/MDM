import test from "node:test";
import assert from "node:assert/strict";
import { buildBinary, hash64, parseLine, TIERS, CATEGORIES } from "../tools/build-blocklists.mjs";

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
