import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import { dashboardPage, loginPage } from "../src/ui.js";

// The dashboard's whole interface is one big script inside the page. A typo in it never shows up
// in the server tests (they only fetch the page), but would leave the dashboard blank for everyone.
test("the dashboard page script is valid JavaScript", () => {
  const html = dashboardPage();
  const m = html.match(/<script>([\s\S]*)<\/script>\s*<\/body>/);
  assert.ok(m, "found the page script");
  assert.doesNotThrow(() => new vm.Script(m[1]));
});

test("the sign-in page has the code field and the install-to-home-screen link", () => {
  const html = loginPage();
  assert.match(html, /name="code"/);
  assert.match(html, /manifest\.webmanifest/);
});
