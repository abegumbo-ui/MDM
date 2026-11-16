// The search inside a phone must lead everywhere: every tab of every group has an entry, and no entry points at a tab or page that does not exist
// (two entries once pointed at a "settings" group that is not a phone tab and just showed the Overview).
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const src = fs.readFileSync(new URL("../src/ui.js", import.meta.url), "utf8").replace(/\r\n/g, "\n");

function slice(startMarker, endMarker, from = 0) {
  const i = src.indexOf(startMarker, from);
  assert.ok(i >= 0, "found " + startMarker);
  const j = src.indexOf(endMarker, i);
  assert.ok(j > i, "found the end after " + startMarker);
  return src.slice(i, j);
}

const groupsStart = src.indexOf("const GROUPS=[");
const groupsEnd = src.indexOf("];", src.indexOf("{key:'device'", groupsStart)) + 2;
const GROUPS = new Function("return " + src.slice(groupsStart, groupsEnd).replace("const GROUPS=", "").replace(/;$/, ""))();
const indexSrc = slice("const searchIndex=[", "const pageIndex");
const searchIndex = new Function("return " + indexSrc.replace("const searchIndex=", "").replace(/;\s*(\/\/[^\n]*)?\s*$/, ""))();
const pageSrc = slice("const pageIndex=function(){", "const sWrap");
const pageNames = [...pageSrc.matchAll(/'@page','(\w+)'/g)].map((m) => m[1]);

test("every tab of every group has at least one search entry", () => {
  const have = new Set(searchIndex.map((e) => e[1] + "/" + e[2]));
  const missing = [];
  for (const g of GROUPS) for (const [k, label] of g.subs) if (!have.has(g.key + "/" + k)) missing.push(g.label + " > " + label);
  assert.deepEqual(missing, [], "tabs the search cannot lead to");
});

test("no search entry points at a tab that does not exist", () => {
  const valid = new Set();
  for (const g of GROUPS) for (const [k] of g.subs) valid.add(g.key + "/" + k);
  const pages = new Set(["clients", "server", "templates", "audit", "codes", "settings"]);
  const bad = [];
  for (const e of searchIndex) {
    if (e[1] === "@page") { if (!pages.has(e[2])) bad.push(e[0] + " -> page " + e[2]); continue; }
    if (!valid.has(e[1] + "/" + e[2])) bad.push(e[0] + " -> " + e[1] + "/" + e[2]);
  }
  assert.deepEqual(bad, [], "entries that lead nowhere");
});

test("the pages outside a phone are searchable: Clients, Add a device, Server, Templates, Activity, Settings", () => {
  for (const p of ["clients", "codes", "server", "templates", "audit", "settings"]) assert.ok(pageNames.includes(p) || searchIndex.some((e) => e[1] === "@page" && e[2] === p), "the " + p + " page has a search entry");
  assert.match(pageSrc, /Clients: give someone \(a customer\) their own login and page/);
});
