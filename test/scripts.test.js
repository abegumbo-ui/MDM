import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import vm from "node:vm";

// The scripts the phones inject into web pages (the YouTube cover, the shelf, the ad cover, ...) are plain JavaScript files inside the
// Android sources. A typo in one is invisible to every Java check (they only look for the script's TEXT), yet the page then runs WITHOUT
// that script. On 2026-11-15 a stray "//" comment in youtube_cover.js commented out the rest of its line: the script no longer parsed, so
// YouTube was not filtered at all on every phone on build 764 or newer. Every one of these files must at least parse.
function jsFiles(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    if (["build", "node_modules", ".gradle", ".kotlin", "intermediates"].includes(e.name)) continue;
    const p = join(dir, e.name);
    if (e.isDirectory()) jsFiles(p, out);
    else if (e.name.endsWith(".js") && p.replace(/\\/g, "/").includes("/src/main/")) out.push(p);
  }
  return out;
}

test("every script the phones inject into pages parses (a broken one means the page runs without it)", () => {
  const files = [...jsFiles("android"), ...jsFiles("android-jelly")];
  assert.ok(files.length >= 8, "found the injected scripts (" + files.length + ")");
  assert.ok(files.some((f) => f.endsWith("youtube_cover.js")), "the YouTube cover script is among them");
  const broken = [];
  for (const f of files) {
    try {
      new vm.Script(readFileSync(f, "utf8"), { filename: f }); // parses only, never runs
    } catch (e) {
      broken.push(f + ": " + e.message);
    }
  }
  assert.deepEqual(broken, [], "scripts that do not parse");
});

test("the check really catches the mistake that shipped (a comment in the middle of a return line)", () => {
  const bad = "(function(){ function f(){ return { a: 1, b: 2, // note v: 3, c: 4 };\n } })();";
  assert.throws(() => new vm.Script(bad), SyntaxError);
});
