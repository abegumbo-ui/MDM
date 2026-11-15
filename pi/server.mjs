// The dashboard and phone API on a Raspberry Pi: the same code as the Cloudflare Worker (src/index.js), run by Node,
// with its storage in files (kv.mjs) instead of Cloudflare KV. Cloudflare stays as the backup (see sync.mjs and README.md).
//   node pi/server.mjs          (settings from the environment, see readEnv)
import http from 'node:http';
import { pathToFileURL } from 'node:url';
import worker, { waitingPhones } from '../src/index.js';
import { KvStore } from './kv.mjs';
import { startReplication } from './sync.mjs';
import { createOutageLog } from './outages.mjs';
import { createSystem } from './system.mjs';
import { createAdmin } from './admin.mjs';

const MAX_BODY = 40 * 1024 * 1024;
const DROP_REQUEST_HEADERS = new Set(['connection', 'content-length', 'transfer-encoding', 'keep-alive', 'upgrade', 'proxy-connection', 'te', 'expect']);

/** Everything the Worker reads from its environment, taken from the process environment. */
export function readEnv(src = process.env) {
  const vars = {};
  for (const k of ['ADMIN_PASSWORD', 'GITHUB_REPO', 'PLAY_ANDROID_ID', 'PLAY_EMAIL', 'PLAY_MASTER_TOKEN', 'ROLE', 'PRIMARY_URL', 'BACKUP_URL', 'REPLICA_SECRET', 'POLL_SECONDS', 'FAST_POLL_SECONDS', 'BURST_POLL_SECONDS']) {
    if (src[k]) vars[k] = src[k];
  }
  if (!vars.ROLE) vars.ROLE = 'main';
  // No daily limits on the Pi: phones check in every 3 seconds while the dashboard is open (and right after a change),
  // every 5 minutes when nobody is looking. Cloudflare keeps its own, slower numbers (see src/index.js).
  if (!vars.FAST_POLL_SECONDS) vars.FAST_POLL_SECONDS = '3';
  if (!vars.BURST_POLL_SECONDS) vars.BURST_POLL_SECONDS = '3';
  if (!vars.POLL_SECONDS) vars.POLL_SECONDS = '300';
  return vars;
}

/** Settings that are missing and make the Pi misbehave. Without PRIMARY_URL the Pi does not tell the phones that it is the main server, so they take turns
 *  between the Pi and Cloudflare: a command is handed out by one server and its result goes to the other (found 2026-11-14). */
export function configProblems(vars) {
  const out = [];
  if ((vars.ROLE || 'main') === 'main' && vars.BACKUP_URL && !vars.PRIMARY_URL) {
    out.push("PRIMARY_URL is empty: the Pi does not announce itself to the phones, so they alternate between the Pi and Cloudflare and commands and their results get lost. Run pi/deploy.ps1 again (it fills it in) or set PRIMARY_URL to the Pi's public address.");
  }
  return out;
}

export async function createApp({ dataDir, vars, publicUrl = '', log = () => {}, replication = true, fetchFn, scheduleMs = 15 * 60 * 1000, piAdminOptions, replicationOptions = {}, outageOptions = {} }) {
  const kv = new KvStore(dataDir);
  await kv.init();
  const env = { ...vars, STATE: kv, RUNTIME: 'pi' };
  const pending = new Set();
  const ctx = {
    waitUntil(p) {
      const q = Promise.resolve(p).catch(() => {}).finally(() => pending.delete(q));
      pending.add(q);
    },
    passThroughOnException() {},
  };
  // The outage history: when the Pi was off or restarted, and when phones had to use Cloudflare (see outages.mjs).
  const sys = (piAdminOptions && piAdminOptions.sys) || createSystem();
  const outages = createOutageLog({ kv, sys, log, ...outageOptions });
  const repl = replication ? startReplication({ kv, vars, log, fetchFn, onOutage: (o) => outages.phonesUsedCloudflare(o), ...replicationOptions }) : null;
  const started = Date.now();
  const piAdmin = createAdmin({ env, vars, kv, getRepl: () => repl, publicUrl, started, outages, ...(piAdminOptions || {}) });
  outages.start();

  async function handle(req, res) {
    if (req.url === '/healthz') {
      const mem = process.memoryUsage();
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: true, role: vars.ROLE, entries: kv.size, uptimeSeconds: Math.round((Date.now() - started) / 1000), rssMB: Math.round(mem.rss / 1048576), waiting: waitingPhones() }));
      return;
    }
    const startedAt = Date.now();
    const chunks = [];
    let size = 0;
    for await (const c of req) {
      size += c.length;
      if (size > MAX_BODY) { res.writeHead(413); res.end(); return; }
      chunks.push(c);
    }
    const body = Buffer.concat(chunks);
    const origin = publicUrl || 'http://' + (req.headers.host || 'localhost');
    const url = new URL(req.url, origin);
    const headers = new Headers();
    for (const [k, v] of Object.entries(req.headers)) {
      if (DROP_REQUEST_HEADERS.has(k)) continue;
      if (Array.isArray(v)) v.forEach((x) => headers.append(k, x));
      else if (v !== undefined) headers.set(k, v);
    }
    // The Worker tells people apart (sign-in lock-out) by this header, which Cloudflare adds; here it comes from the connection.
    if (!headers.has('cf-connecting-ip')) {
      headers.set('cf-connecting-ip', String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || 'unknown');
    }
    const init = { method: req.method, headers };
    if (req.method !== 'GET' && req.method !== 'HEAD' && body.length) init.body = body;
    const request = new Request(url, init);
    // The Server page (/api/pi/*) is the Pi's own; everything else is the dashboard code.
    const response = (await piAdmin.handle(request)) || (await worker.fetch(request, env, ctx));
    const out = {};
    response.headers.forEach((v, k) => { if (k !== 'set-cookie') out[k] = v; });
    const cookies = typeof response.headers.getSetCookie === 'function' ? response.headers.getSetCookie() : [];
    if (cookies.length) out['set-cookie'] = cookies;
    const bytes = Buffer.from(await response.arrayBuffer());
    // One line for every phone request (a check-in is one every few minutes, so this stays small), so a stall can be read afterwards:
    // path, answer, time, bytes in and out. An idle long wait (/agent/wait that ran its full ~40 s) is not worth a line; a wake-up is.
    if (url.pathname.startsWith('/agent/')) {
      const ms = Date.now() - startedAt;
      const idleWait = url.pathname === '/agent/wait' && response.status < 400 && ms >= 35000;
      if (!idleWait) {
        log('phone ' + req.method + ' ' + url.pathname + ' answered ' + response.status + ' in ' + ms + ' ms (' + body.length + ' B in, ' + bytes.length + ' B out)' + (response.status >= 400 ? ': ' + bytes.toString('utf8').slice(0, 160) : ''));
      }
    }
    res.writeHead(response.status, out);
    res.end(req.method === 'HEAD' ? undefined : bytes);
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch((e) => {
      log('request failed: ' + String(e && e.message || e));
      if (!res.headersSent) res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ error: 'Server error' }));
    });
  });
  // Phones that went quiet, low batteries, daily and weekly summaries: what Cloudflare runs on a schedule.
  const sched = scheduleMs > 0 ? setInterval(() => { try { worker.scheduled({}, env, ctx); } catch (e) { log('scheduled run failed: ' + e.message); } }, scheduleMs) : null;
  if (sched && sched.unref) sched.unref();

  return {
    server,
    kv,
    env,
    repl,
    outages,
    async stop() {
      if (sched) clearInterval(sched);
      outages.stop();
      if (repl) repl.stop();
      const closing = new Promise((r) => server.close(r));
      if (server.closeIdleConnections) server.closeIdleConnections(); // do not wait for browsers that keep a connection open
      // Phones waiting in /agent/wait hold their connection for up to 40 s (a restart used to take 90 s, until systemd killed the
      // server). Let normal requests finish for 2 seconds, then drop the long waits: a phone simply asks again.
      const force = setTimeout(() => { if (server.closeAllConnections) server.closeAllConnections(); }, 2000);
      await closing;
      clearTimeout(force);
      await Promise.all([...pending]);
      kv.close();
    },
  };
}

async function main() {
  const vars = readEnv();
  if (!vars.ADMIN_PASSWORD) {
    console.error('ADMIN_PASSWORD is not set (see pi/README.md).');
    process.exit(1);
  }
  for (const p of configProblems(vars)) console.warn('WARNING: ' + p);
  const port = Number(process.env.PORT || 8787);
  const host = process.env.HOST || '127.0.0.1';
  const app = await createApp({
    dataDir: process.env.DATA_DIR || '/var/lib/mdm-pi',
    vars,
    publicUrl: process.env.PUBLIC_URL || '',
    log: (m) => console.log(new Date().toISOString() + ' ' + m),
  });
  await new Promise((r) => app.server.listen(port, host, r));
  console.log(`mdm-pi listening on ${host}:${port} as ${vars.ROLE}`);
  const quit = async () => { await app.stop(); process.exit(0); };
  process.on('SIGTERM', quit);
  process.on('SIGINT', quit);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
