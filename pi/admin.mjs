// The Pi's own pages for the dashboard ("Server"): what is going on on the Pi, the Wi-Fi it uses, restarting. Only the
// administrator of the dashboard may use them (same sign-in as everything else). Only exists on the Pi: Cloudflare answers
// 404 for /api/pi/*, and the dashboard shows the Server page only when this answers.
import fs from 'node:fs';
import { adminSessionOf } from '../src/index.js';
import { createSystem } from './system.mjs';

const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });

export function createAdmin({ env, vars, kv, getRepl, publicUrl = '', started = Date.now(), sys = createSystem(), versionFile = '/opt/mdm-pi/VERSION', adminOf = adminSessionOf }) {
  function version() {
    try { return fs.readFileSync(versionFile, 'utf8').trim(); } catch { return ''; }
  }

  async function status() {
    const [health, wifi, tailscale] = await Promise.all([sys.health(), sys.wifiNow(), sys.tailscale()]);
    const repl = getRepl && getRepl();
    return {
      role: vars.ROLE || 'main',
      version: version(),
      node: process.version,
      serverUptimeSeconds: Math.round((Date.now() - started) / 1000),
      serverMemoryMB: Math.round(process.memoryUsage().rss / 1048576),
      entries: kv.size,
      publicUrl: publicUrl || vars.PUBLIC_URL || '',
      backupUrl: vars.BACKUP_URL || '',
      polling: { idleSeconds: Number(vars.POLL_SECONDS) || 300, dashboardOpenSeconds: Number(vars.FAST_POLL_SECONDS) || 30, afterChangeSeconds: Number(vars.BURST_POLL_SECONDS) || 15 },
      health,
      wifi,
      tailscale,
      replication: repl ? repl.status() : { configured: false },
    };
  }

  /** Answers /api/pi/*; null for any other address (the dashboard's own code answers those). */
  async function handle(request) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith('/api/pi/')) return null;
    if (!(await adminOf(request, env))) return json({ error: 'Administrator only' }, 403);
    const method = request.method;
    if (method !== 'GET') {
      // A page on another website must not be able to press these buttons on the administrator's behalf.
      const origin = request.headers.get('origin');
      if (origin) { try { if (new URL(origin).host !== url.host && new URL(origin).host !== (request.headers.get('host') || '')) return json({ error: 'Wrong origin' }, 403); } catch { return json({ error: 'Wrong origin' }, 403); } }
      if (!/application\/json/.test(request.headers.get('content-type') || '')) return json({ error: 'Send JSON' }, 415);
    }
    const body = method === 'GET' || method === 'DELETE' ? {} : await request.json().catch(() => ({}));
    const p = url.pathname;

    if (p === '/api/pi/status' && method === 'GET') return json(await status());
    if (p === '/api/pi/wifi' && method === 'GET') return json({ ...(await sys.wifiLists({ rescan: url.searchParams.get('rescan') === '1' })), now: await sys.wifiNow() });
    if (p === '/api/pi/wifi' && method === 'POST') {
      const r = await sys.wifiRemember({ ssid: body.ssid, password: body.password, hidden: body.hidden === true, priority: body.priority === undefined ? 10 : body.priority });
      if (!r.ok) return json(r, 400);
      if (body.connect === true) {
        // Answered first, the Pi moves to the new network a moment later (the answer would not get back otherwise).
        setTimeout(() => { sys.wifiConnect(body.ssid).catch(() => {}); }, 1500);
        return json({ ok: true, message: 'Saved. The Pi switches to it in a moment; if it cannot join, it goes back to a network it knows.' });
      }
      return json({ ok: true, message: 'Saved. The Pi will use it when it cannot reach a network it prefers.' });
    }
    if (p === '/api/pi/wifi/connect' && method === 'POST') {
      setTimeout(() => { sys.wifiConnect(body.name).catch(() => {}); }, 1500);
      return json({ ok: true });
    }
    if (p === '/api/pi/wifi/priority' && method === 'POST') {
      const r = await sys.wifiPriority(body.name, body.priority);
      return json(r, r.ok ? 200 : 400);
    }
    if (p === '/api/pi/wifi' && method === 'DELETE') {
      const r = await sys.wifiForget(url.searchParams.get('name') || '');
      return json(r, r.ok ? 200 : 400);
    }
    if (p === '/api/pi/log' && method === 'GET') return json({ text: await sys.logLines(Number(url.searchParams.get('lines')) || 100) });
    if (p === '/api/pi/copy-now' && method === 'POST') {
      const repl = getRepl && getRepl();
      if (!repl || !repl.status().configured) return json({ ok: false, error: 'The copy to Cloudflare is not set up (see the four settings on Cloudflare).' }, 400);
      const r = await repl.flush();
      return json({ ok: !r.error && !r.offline, result: r });
    }
    if (p === '/api/pi/restart' && method === 'POST') {
      setTimeout(() => { sys.restartService().catch(() => {}); }, 800);
      return json({ ok: true, message: 'Restarting the server. The dashboard comes back in a few seconds.' });
    }
    if (p === '/api/pi/reboot' && method === 'POST') {
      setTimeout(() => { sys.rebootPi().catch(() => {}); }, 800);
      return json({ ok: true, message: 'Restarting the Pi. It is back in about a minute.' });
    }
    return json({ error: 'Not found' }, 404);
  }

  return { handle, status };
}
