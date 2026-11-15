import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// An application-restrictions Bundle (what the agent hands to DevicePolicyManager.setApplicationRestrictions) may only hold text, whole numbers,
// yes/no, lists of text and nested bundles. On 2026-11-15 two long numbers in it made Samsung's Android 16 throw
// "ClassCastException: Long cannot be cast to String[]" while saving its policy file, and from then on EVERY policy change failed after being
// applied in memory (PIN control never worked). Numbers that are longer than an int go in as text.
const FORBIDDEN = ["putLong", "putDouble", "putFloat", "putShort", "putByte", "putChar", "putSerializable", "putParcelable", "putParcelableArray"];

function javaFiles(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    if (["build", ".gradle", ".kotlin", "intermediates"].includes(e.name)) continue;
    const p = join(dir, e.name);
    if (e.isDirectory()) javaFiles(p, out);
    else if (e.name.endsWith(".java") && p.replace(/\\/g, "/").includes("/src/main/")) out.push(p);
  }
  return out;
}

test("no Bundle given to setApplicationRestrictions holds a long (or any other type Android's policy file cannot save)", () => {
  const files = javaFiles("android").filter((f) => readFileSync(f, "utf8").includes("setApplicationRestrictions("));
  assert.ok(files.length >= 1, "found the file that sets application restrictions");
  const bad = [];
  for (const f of files) {
    const src = readFileSync(f, "utf8");
    for (const m of src.matchAll(/Bundle\s+(\w+)\s*=\s*new Bundle\(\)/g)) {
      for (const fn of FORBIDDEN) {
        if (new RegExp("\\b" + m[1] + "\\." + fn + "\\(").test(src)) bad.push(f + ": " + m[1] + "." + fn + "(...)");
      }
    }
  }
  assert.deepEqual(bad, [], "Bundles with a type Android's policy file cannot save");
});

test("the check catches the mistake that shipped", () => {
  const src = 'Bundle b = new Bundle();\nb.putString("a", "1");\nb.putLong("browseUntil", 5L);\ndpm.setApplicationRestrictions(admin, pkg, b);';
  const hits = [...src.matchAll(/Bundle\s+(\w+)\s*=\s*new Bundle\(\)/g)].filter((m) => new RegExp("\\b" + m[1] + "\\.putLong\\(").test(src));
  assert.equal(hits.length, 1);
});
