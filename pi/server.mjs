// The dashboard and phone API on a Raspberry Pi: the same code as the Cloudflare Worker (src/index.js), run by Node,
// with its storage in files (kv.mjs) instead of Cloudflare KV. Cloudflare stays as the backup (see sync.mjs and README.md).
//   node pi/server.mjs          (settings from the environment, see readEnv)
import http from 'node:http';
import { pathToFileURL } from 'node:url';
import worker from '../src/index.js';
import { KvStore } from './kv.mjs';
import { startReplication } from './sync.mjs';

const MAX_BODY = 40 * 1024 * 1024;
const DROP_REQUEST_HEADERS = new Set(['connection', 'content-length', 'transfer-encoding', 'keep-alive', 'upgrade', 'proxy-connection', 'te', 'expect']);

/** Everything the Worker reads from its environment, taken from the process environment. */
export function readEnv(src = process.env) {
  const vars = {};
  for (const k of ['ADMIN_PASSWORD', 'GITHUB_REPO', 'PLAY_ANDROID_ID', 'PLAY_EMAIL', 'PLAY_MASTER_TOKEN', 'ROLE', 'PRIMARY_URL', 'BACKUP_URL', 'REPLICA_SECRET', 'YOUTUBE_API_KEY']) {
    if (src[k]) vars[k] = src[k];
  }
  if (!vars.ROLE) vars.ROLE = 'main';
  return vars;
}

export async function createApp({ dataDir, vars, publicUrl = '', log = () => {}, replication = true, fetchFn, scheduleMs = 15 * 60 * 1000 }) {
  const kv = new KvStore(dataDir);
  await kv.init();
  const env = { ...vars, STATE: kv };
  const pending = new Set();
  const ctx = {
    waitUntil(p) {
      const q = Promise.resolve(p).catch(() => {}).finally(() => pending.delete(q));
      pending.add(q);
    },
    passThroughOnException() {},
  };
  const repl = replication ? startReplication({ kv, vars, log, fetchFn }) : null;
  const started = Date.now();

  async function handle(req, res) {
    if (req.url === '/healthz') {
      const mem = process.memoryUsage();
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: true, role: vars.ROLE, entries: kv.size, uptimeSeconds: Math.round((Date.now() - started) / 1000), rssMB: Math.round(mem.rss / 1048576) }));
      return;
    }
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
    const response = await worker.fetch(new Request(url, init), env, ctx);
    const out = {};
    response.headers.forEach((v, k) => { if (k !== 'set-cookie') out[k] = v; });
    const cookies = typeof response.headers.getSetCookie === 'function' ? response.headers.getSetCookie() : [];
    if (cookies.length) out['set-cookie'] = cookies;
    const bytes = Buffer.from(await response.arrayBuffer());
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
    async stop() {
      if (sched) clearInterval(sched);
      if (repl) repl.stop();
      await new Promise((r) => server.close(r));
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
