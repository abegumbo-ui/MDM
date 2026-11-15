import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { dashboardPage } from "../src/ui.js";

// The dashboard used to say "within about 5 minutes" everywhere. It now says what the server really does.
function boot(extra = {}) {
  const kv = new Map();
  const env = {
    ADMIN_PASSWORD: "pw",
    ...extra,
    STATE: {
      get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
      put: async (k, v) => void kv.set(k, v),
      delete: async (k) => void kv.delete(k),
      list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
    },
  };
  return async (path, init = {}) => worker.fetch(new Request("https://x.test" + path, init), env);
}
async function stateOf(extra) {
  const call = boot(extra);
  const r = await call("/login", { method: "POST", body: new URLSearchParams({ password: "pw" }) });
  const cookie = r.headers.get("set-cookie").split(";")[0];
  return (await call("/api/state", { headers: { cookie } })).json();
}

test("the Pi tells the dashboard its phones are woken at once; Cloudflare gives its timer", async () => {
  const pi = await stateOf({ RUNTIME: "pi", POLL_SECONDS: "300", FAST_POLL_SECONDS: "3", BURST_POLL_SECONDS: "3" });
  assert.deepEqual(pi.polling, { instant: true, idle: 300, open: 3, burst: 3 });
  const cf = await stateOf({});
  assert.equal(cf.polling.instant, false);
  assert.equal(cf.polling.idle, 300);
  assert.equal(cf.polling.open, 30);
  assert.equal(cf.polling.burst, 15);
});

test("no text on the page promises a fixed 5 minutes any more; the health check has an Update button", () => {
  const page = dashboardPage();
  assert.equal(/within about 5 minutes/.test(page), false, "the old blanket sentence is gone");
  assert.match(page, /function pickup\(\)/);
  assert.match(page, /btn\('Update now'/, "Update now is offered next to the warning");
  assert.match(page, /type:'updateAgent'/);
});
