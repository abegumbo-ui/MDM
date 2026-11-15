// The Pi's outage history, for the dashboard's Server page: when it was off or restarted, and when phones could not reach it and had
// to use Cloudflare, with the likeliest reason. Kept on the Pi only (the "repl:" keys are never copied to Cloudflare).
//  - A heartbeat is written every minute with what the Pi looked like (power, Wi-Fi, Tailscale). When the server starts again, the gap
//    since the last heartbeat is the time it was away: a different boot id means the whole Pi restarted (power cut, crash, reboot), the
//    same boot id with a long gap means only the server program was stopped.
//  - When phones had used Cloudflare, the Pi (which stayed on) records that window too, with the reason that the problem was between
//    the phones and the Pi (Wi-Fi, router, internet or Tailscale).
import fs from 'node:fs';
import os from 'node:os';

const readBootId = () => { try { return fs.readFileSync('/proc/sys/kernel/random/boot_id', 'utf8').trim(); } catch { return ''; } };

export const OUTAGES_KEY = 'repl:outages';
const HEARTBEAT_KEY = 'repl:heartbeat';
const PLANNED_KEY = 'repl:planned';

/** One plain sentence about what the Pi looked like when it last said it was fine. */
export function describeSnapshot(snap) {
  if (!snap || typeof snap !== 'object' || !Object.keys(snap).length) return 'Nothing was recorded about its state.';
  const bits = [];
  if (snap.powerOk === false) bits.push('it had reported low power (under-voltage): check its charger and cable');
  if (snap.wifi && snap.wifi !== 'connected') bits.push('its Wi-Fi was not connected');
  if (snap.tailscale && snap.tailscale !== 'Running') bits.push('Tailscale was not running');
  if (bits.length) return 'At the last check ' + bits.join(', and ') + '.';
  return 'Everything looked normal at the last check (power fine, Wi-Fi and Tailscale connected), so it was most likely a power cut or someone restarting it.';
}

export function createOutageLog({ kv, sys = null, now = Date.now, bootId = readBootId, beatMs = 60 * 1000, keep = 100, log = () => {} }) {
  const gapMs = Math.round(beatMs * 2.5);
  let timer = null;
  let beats = 0;
  let snap = {};

  const read = (key, fallback) => {
    try {
      const rec = kv.map && kv.map.get(key);
      if (rec && rec.t === 's') return JSON.parse(rec.v);
    } catch { /* unreadable: start again */ }
    return fallback;
  };
  const write = (key, value) => { try { kv.put(key, JSON.stringify(value)).catch(() => {}); } catch { /* closing */ } };

  /** Newest first. */
  const list = () => read(OUTAGES_KEY, []).slice().sort((a, b) => b.from - a.from);

  function add(entry) {
    const all = read(OUTAGES_KEY, []);
    all.push({ id: String(entry.from) + '-' + all.length, ...entry });
    write(OUTAGES_KEY, all.slice(-keep));
    log('outage noted: ' + entry.title);
  }

  async function snapshot() {
    const out = {};
    if (!sys) return out;
    try { const h = await sys.health(); if (h && h.power && typeof h.power.ok === 'boolean') out.powerOk = h.power.ok; if (h && typeof h.cpuTempC === 'number') out.tempC = h.cpuTempC; } catch { /* unknown */ }
    try { const w = await sys.wifiNow(); if (w && w.state) { out.wifi = w.state; if (w.ssid) out.ssid = w.ssid; if (typeof w.signal === 'number') out.signal = w.signal; } } catch { /* unknown */ }
    try { const t = await sys.tailscale(); if (t && t.state) out.tailscale = t.state; } catch { /* unknown */ }
    return out;
  }

  let lastBeatAt = 0;
  function beat() {
    const at = now();
    const boot = bootId();
    lastBeatAt = at;
    write(HEARTBEAT_KEY, { at, boot, snap });
    // The state of the Pi (power, Wi-Fi, Tailscale) is looked at now and then, and written with that heartbeat as soon as it is known
    // (never over a newer heartbeat, and with the time of the beat it belongs to).
    if (beats++ % 10 === 0) snapshot().then((s) => { snap = s; if (lastBeatAt === at) write(HEARTBEAT_KEY, { at, boot, snap }); }).catch(() => {});
  }

  /** Called before the dashboard restarts the server or the Pi itself, so that it is not mistaken for a failure. */
  function markPlanned(kind) {
    write(PLANNED_KEY, { kind, at: now() });
  }

  /** At every start of the server: was it away for a while? */
  function start() {
    const last = read(HEARTBEAT_KEY, null);
    const t = now();
    const boot = bootId();
    const planned = read(PLANNED_KEY, null);
    const wasPlanned = planned && t - planned.at < 15 * 60 * 1000;
    if (last && last.at) {
      if (last.boot && boot && last.boot !== boot) {
        add(wasPlanned && planned.kind === 'reboot'
          ? { from: last.at, to: t, kind: 'restart', planned: true, title: 'The Pi was restarted from the dashboard', detail: 'You restarted the Pi from the Server page.' }
          : { from: last.at, to: t, kind: 'restart', title: 'The whole Pi restarted', detail: describeSnapshot(last.snap) });
      } else if (t - last.at > gapMs) {
        add(wasPlanned
          ? { from: last.at, to: t, kind: 'server', planned: true, title: 'The server was restarted from the dashboard', detail: 'You restarted the server from the Server page.' }
          : { from: last.at, to: t, kind: 'server', title: 'The server program was stopped for a while', detail: 'The Pi stayed on but the dashboard program was not running: an update, someone stopping it, or a crash. ' + describeSnapshot(last.snap) });
      }
    }
    if (planned) write(PLANNED_KEY, null);
    beat();
    timer = setInterval(beat, beatMs);
    if (timer.unref) timer.unref();
  }

  /** The Pi saw that phones had used Cloudflare between from and to (it was reachable for Cloudflare, but not for them). */
  function phonesUsedCloudflare({ from, to }) {
    const end = to || now();
    const begin = from || end;
    const all = read(OUTAGES_KEY, []);
    const hit = all.find((e) => e.from - 2 * 60 * 1000 <= end && (e.to || end) + 2 * 60 * 1000 >= begin);
    if (hit) {
      hit.phonesUsedCloudflare = { from: begin, to: end };
      write(OUTAGES_KEY, all);
      return;
    }
    add({
      from: begin, to: end, kind: 'network', phonesUsedCloudflare: { from: begin, to: end },
      title: 'A phone could not reach the Pi and used Cloudflare',
      detail: 'The Pi was running the whole time, so the problem was between that phone and the Pi: the phone\'s Wi-Fi or mobile connection, the router, the internet, or Tailscale. Cloudflare covered until the Pi answered again.',
    });
  }

  function stop() { if (timer) clearInterval(timer); timer = null; }

  return { start, stop, beat, list, markPlanned, phonesUsedCloudflare, uptimeMs: () => os.uptime() * 1000 };
}
