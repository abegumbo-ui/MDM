// What the Pi's "Server" page in the dashboard can see and change: health numbers, the Wi-Fi it is on and the networks it
// remembers, restarting. Everything goes through `run(command, args)` (default: the real thing), so it can be tested without
// a Pi. Nothing here is ever given to a shell: arguments are passed as a list.
import { execFile } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';

export function realRun(cmd, args = [], { input, timeoutMs = 20000 } = {}) {
  return new Promise((resolve) => {
    const child = execFile(cmd, args, { timeout: timeoutMs, maxBuffer: 4 * 1024 * 1024 }, (err, stdout, stderr) => {
      resolve({ ok: !err, code: err && typeof err.code === 'number' ? err.code : err ? 1 : 0, out: String(stdout || ''), err: String(stderr || '') });
    });
    if (input !== undefined && child.stdin) { child.stdin.end(input); }
  });
}

/** nmcli -t output: fields are separated by ":" and a literal ":" inside a value is written "\:". */
export function splitTerse(line) {
  const out = [];
  let cur = '';
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '\\' && i + 1 < line.length) { cur += line[++i]; continue; }
    if (c === ':') { out.push(cur); cur = ''; continue; }
    cur += c;
  }
  out.push(cur);
  return out;
}

export function parseSavedWifi(text) {
  // nmcli -t -f NAME,TYPE,AUTOCONNECT,AUTOCONNECT-PRIORITY,DEVICE con show
  const rows = [];
  for (const line of String(text).split('\n')) {
    if (!line.trim()) continue;
    const [name, type, auto, prio, device] = splitTerse(line);
    if (!/wireless/.test(type || '')) continue;
    rows.push({ name, autoconnect: auto === 'yes', priority: Number(prio) || 0, active: !!device });
  }
  return rows.sort((a, b) => b.priority - a.priority || a.name.localeCompare(b.name));
}

export function parseVisibleWifi(text) {
  // nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY dev wifi list
  const seen = new Map();
  for (const line of String(text).split('\n')) {
    if (!line.trim()) continue;
    const [inUse, ssid, signal, security] = splitTerse(line);
    if (!ssid) continue;
    const row = { ssid, signal: Number(signal) || 0, security: security || '', active: inUse === '*' };
    const old = seen.get(ssid);
    if (!old || row.signal > old.signal || row.active) seen.set(ssid, { ...row, active: row.active || (old && old.active) });
  }
  return [...seen.values()].sort((a, b) => Number(b.active) - Number(a.active) || b.signal - a.signal);
}

export function wifiInputProblem(ssid, password) {
  if (typeof ssid !== 'string' || !ssid.trim() || ssid.length > 32 || /[\r\n\0]/.test(ssid)) return 'The network name must be 1 to 32 characters.';
  if (password !== undefined && password !== '' && (typeof password !== 'string' || password.length < 8 || password.length > 63 || /[\r\n\0]/.test(password))) return 'The Wi-Fi password must be 8 to 63 characters (or empty for an open network).';
  return null;
}

export function createSystem({ run = realRun, readFile = (p) => fs.readFileSync(p, 'utf8'), now = () => Date.now() } = {}) {
  const sudo = (cmd, args, opts) => run('sudo', ['-n', cmd, ...args], opts);

  async function text(cmd, args) {
    const r = await run(cmd, args);
    return r.ok ? r.out.trim() : '';
  }

  async function health() {
    const [temp, thr, df, upt] = await Promise.all([
      text('vcgencmd', ['measure_temp']),
      text('vcgencmd', ['get_throttled']),
      text('df', ['-B1', '--output=size,used,avail', '/']),
      Promise.resolve(os.uptime()),
    ]);
    const flags = parseInt((/0x([0-9a-f]+)/i.exec(thr) || [])[1] || '0', 16);
    const dfl = df.split('\n')[1] ? df.split('\n')[1].trim().split(/\s+/).map(Number) : [];
    let model = '';
    try { model = readFile('/proc/device-tree/model').replace(/\0/g, '').trim(); } catch { /* not a Pi */ }
    const mem = os.totalmem();
    return {
      hostname: os.hostname(),
      model,
      uptimeSeconds: Math.round(upt),
      cpuTempC: Number((/([\d.]+)'C/.exec(temp) || [])[1]) || null,
      load1: Number(os.loadavg()[0].toFixed(2)),
      memoryTotalMB: Math.round(mem / 1048576),
      memoryFreeMB: Math.round(os.freemem() / 1048576),
      diskTotalGB: dfl[0] ? Number((dfl[0] / 1e9).toFixed(1)) : null,
      diskUsedGB: dfl[1] ? Number((dfl[1] / 1e9).toFixed(1)) : null,
      diskFreeGB: dfl[2] ? Number((dfl[2] / 1e9).toFixed(1)) : null,
      // vcgencmd get_throttled: bit 0 under-voltage now, 1 frequency capped now, 2 throttled now, 3 soft temperature limit;
      // bits 16-19 = the same, since the last start.
      power: { ok: (flags & 0xf) === 0, underVoltageNow: !!(flags & 1), underVoltageSinceStart: !!(flags & 0x10000), raw: thr || null },
      clock: new Date(now()).toISOString(),
    };
  }

  async function wifiNow() {
    const dev = await text('nmcli', ['-t', '-f', 'DEVICE,TYPE,STATE,CONNECTION', 'dev']);
    const row = dev.split('\n').map(splitTerse).find((f) => f[1] === 'wifi');
    const state = row ? row[2] : 'unknown';
    const connection = row ? row[3] : '';
    const ip = (await text('nmcli', ['-g', 'IP4.ADDRESS', 'dev', 'show', 'wlan0'])).split('\n')[0] || '';
    const vis = parseVisibleWifi(await text('nmcli', ['-t', '-f', 'IN-USE,SSID,SIGNAL,SECURITY', 'dev', 'wifi', 'list']));
    const cur = vis.find((v) => v.active);
    return { state, connection, ip, ssid: cur ? cur.ssid : '', signal: cur ? cur.signal : null };
  }

  async function wifiLists({ rescan = false } = {}) {
    if (rescan) await sudo('nmcli', ['dev', 'wifi', 'rescan']);
    const saved = parseSavedWifi(await text('nmcli', ['-t', '-f', 'NAME,TYPE,AUTOCONNECT,AUTOCONNECT-PRIORITY,DEVICE', 'con', 'show']));
    const visible = parseVisibleWifi(await text('nmcli', ['-t', '-f', 'IN-USE,SSID,SIGNAL,SECURITY', 'dev', 'wifi', 'list']));
    return { saved, visible };
  }

  /** Remembers a network (new password for one already remembered replaces the old). Does not leave the current network. */
  async function wifiRemember({ ssid, password, hidden = false, priority = 10 }) {
    const bad = wifiInputProblem(ssid, password);
    if (bad) return { ok: false, error: bad };
    const name = ssid;
    await sudo('nmcli', ['con', 'delete', name]); // the same name again = a new password
    const args = ['con', 'add', 'type', 'wifi', 'ifname', 'wlan0', 'con-name', name, 'ssid', ssid,
      '802-11-wireless.powersave', '2', 'connection.autoconnect', 'yes', 'connection.autoconnect-retries', '0',
      'connection.autoconnect-priority', String(Math.max(-999, Math.min(999, Number(priority) || 0)))];
    if (hidden) args.push('802-11-wireless.hidden', 'yes');
    if (password) args.push('wifi-sec.key-mgmt', 'wpa-psk', 'wifi-sec.psk', password, 'wifi-sec.pmf', '1');
    const r = await sudo('nmcli', args);
    return r.ok ? { ok: true } : { ok: false, error: (r.err || r.out || 'nmcli failed').trim().split('\n')[0] };
  }

  async function wifiConnect(name) {
    if (typeof name !== 'string' || !name || /[\r\n\0]/.test(name)) return { ok: false, error: 'No such network.' };
    const r = await sudo('nmcli', ['--wait', '25', 'con', 'up', name], { timeoutMs: 35000 });
    return r.ok ? { ok: true } : { ok: false, error: (r.err || r.out || 'could not connect').trim().split('\n')[0] };
  }

  async function wifiForget(name) {
    if (typeof name !== 'string' || !name || /[\r\n\0]/.test(name)) return { ok: false, error: 'No such network.' };
    const lists = await wifiLists();
    const row = lists.saved.find((s) => s.name === name);
    if (!row) return { ok: false, error: 'That network is not remembered.' };
    if (row.active && lists.saved.filter((s) => s.name !== name && s.autoconnect).length === 0) {
      return { ok: false, error: 'That is the network the Pi is on, and it remembers no other one: removing it would cut the Pi off. Add another network first.' };
    }
    const r = await sudo('nmcli', ['con', 'delete', name]);
    return r.ok ? { ok: true } : { ok: false, error: (r.err || r.out || 'nmcli failed').trim().split('\n')[0] };
  }

  async function wifiPriority(name, priority) {
    const p = Math.max(-999, Math.min(999, Math.round(Number(priority))));
    if (!Number.isFinite(p) || typeof name !== 'string' || !name) return { ok: false, error: 'Bad request.' };
    const r = await sudo('nmcli', ['con', 'modify', name, 'connection.autoconnect-priority', String(p)]);
    return r.ok ? { ok: true } : { ok: false, error: (r.err || r.out || 'nmcli failed').trim().split('\n')[0] };
  }

  async function tailscale() {
    const r = await run('tailscale', ['status', '--json']);
    if (!r.ok) return { installed: false };
    try {
      const j = JSON.parse(r.out);
      return { installed: true, state: j.BackendState || '', name: ((j.Self && j.Self.DNSName) || '').replace(/\.$/, ''), ip: (j.TailscaleIPs || [])[0] || '' };
    } catch {
      return { installed: true, state: 'unknown' };
    }
  }

  async function logLines(n = 100) {
    const r = await run('journalctl', ['-u', 'mdm-pi', '-n', String(Math.max(1, Math.min(500, Number(n) || 100))), '--no-pager', '-o', 'short-iso']);
    return r.out || r.err || '';
  }

  const restartService = () => sudo('systemctl', ['restart', 'mdm-pi']);
  const rebootPi = () => sudo('systemctl', ['reboot']);

  return { health, wifiNow, wifiLists, wifiRemember, wifiConnect, wifiForget, wifiPriority, tailscale, logLines, restartService, rebootPi };
}
