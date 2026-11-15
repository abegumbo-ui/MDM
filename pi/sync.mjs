// The Raspberry Pi side of "Pi is the main server, Cloudflare is the backup" (see replicate.js for the other side).
//  - Changes are copied to Cloudflare in batches (it allows about 1,000 saves a day), and soon after an administrator change.
//    A phone's routine check-in (when it was last seen, its battery, its event list...) is NOT an administrator change: it is copied
//    with the next regular batch, and a phone record that has changed in nothing but that is copied at most every few hours.
//    (A phone that checks in every few seconds used to make the Pi save its whole record to Cloudflare every two minutes: 386 saves
//    in one day, with 21 more from phones that had to use Cloudflare, and Cloudflare's "50% of the daily limit" warning.)
//  - Before copying it asks Cloudflare whether phones had to use it while the Pi was off; if so it first brings those
//    changes back (/internal/export), tells Cloudflare (/internal/ack) and is the main server again.
//  - A brand-new Pi (nothing stored) starts by bringing everything over.
//  - It counts every request and every Cloudflare operation it causes, per day (UTC, like Cloudflare's own day), so the dashboard
//    can show how much of the free allowance is used.
import crypto from 'node:crypto';
import { skipKey } from '../src/replicate.js';

const BATCH = 200;

/** Cloudflare's free-plan daily allowance (Workers requests; Workers KV operations). All reset at 00:00 UTC. */
export const CLOUDFLARE_FREE_LIMITS = { requests: 100000, reads: 100000, writes: 1000, deletes: 1000, lists: 1000 };

const utcDay = (t) => new Date(t).toISOString().slice(0, 10);

/**
 * What makes a phone record "really changed" for the copy to Cloudflare: who it is, its settings and what the administrator queued for it.
 * Everything else in the record (last seen, battery, apps, events, results, traffic) is refreshed by the phone itself, whichever server it uses.
 * null = not a phone record: every change of it counts.
 */
export function fingerprint(key, rec) {
  if (!rec) return 'deleted';
  if (!/^(device|browserDevice|winDevice):/.test(key) || rec.t !== 's') return null;
  try {
    const d = JSON.parse(rec.v);
    return crypto.createHash('sha1').update(JSON.stringify([d.id, d.name, d.tokenHash, d.ownerId, d.config, d.queue, d.enabled, d.filterOn, d.win, d.allowedPrograms])).digest('hex');
  } catch {
    return null;
  }
}

export function startReplication({ kv, vars, log = () => {}, intervalMs = 30 * 60 * 1000, soonMs = 2 * 60 * 1000, volatileRefreshMs = 3 * 3600 * 1000, fetchFn = fetch, now = Date.now, onOutage = null }) {
  const secret = vars.REPLICA_SECRET;
  const backup = String(vars.BACKUP_URL || '').replace(/\/+$/, '');
  if (!secret || !backup) return { stop() {}, flush: async () => ({ skipped: true }), importAll: async () => 0, status: () => ({ configured: false }) };

  const dirty = new Map(); // key -> record | null (the newest value to copy; null = deleted)
  const copied = new Map(); // key -> { fp, at }: what the last copy of it looked like
  const info = { lastCopyAt: 0, lastCopyCount: 0, lastImportAt: 0, lastImportCount: 0, lastError: '', lastTriedAt: 0, backupReachable: null, outagesRecovered: 0 };
  let soon = null;
  let busy = false;

  // ---- how much of Cloudflare's daily allowance this Pi has used ----
  const usage = { day: utcDay(now()), requests: 0, reads: 0, writes: 0, deletes: 0, lists: 0 };
  try {
    const saved = kv.map && kv.map.get('repl:usage');
    const u = saved && saved.t === 's' ? JSON.parse(saved.v) : null;
    if (u && u.day === usage.day) Object.assign(usage, u);
  } catch { /* start the count again */ }
  let usageTimer = null;
  const rollDay = () => {
    const d = utcDay(now());
    if (d !== usage.day) Object.assign(usage, { day: d, requests: 0, reads: 0, writes: 0, deletes: 0, lists: 0 });
  };
  const saveUsageSoon = () => {
    if (usageTimer) return;
    usageTimer = setTimeout(() => { usageTimer = null; kv.put('repl:usage', JSON.stringify(usage)).catch(() => {}); }, 60 * 1000);
    if (usageTimer.unref) usageTimer.unref();
  };
  /** What one request to Cloudflare costs there: 1 request, plus the storage operations its handler makes (see handleInternal in replicate.js). */
  function account(pathname, init, res) {
    rollDay();
    usage.requests++;
    const data = res && res.data ? res.data : {};
    if (pathname.startsWith('/internal/status')) usage.reads += 1;
    else if (pathname.startsWith('/internal/ack')) usage.deletes += 1;
    else if (pathname.startsWith('/internal/replicate')) {
      usage.reads += 1; // it first looks whether an outage is being saved
      if (data.ok) { usage.writes += data.puts || 0; usage.deletes += data.deletes || 0; }
    } else if (pathname.startsWith('/internal/export')) {
      usage.lists += 1;
      usage.reads += Array.isArray(data.entries) ? data.entries.length : 0;
    }
    saveUsageSoon();
  }

  async function call(pathname, init = {}) {
    let res = { status: 0, data: {} };
    try {
      const r = await fetchFn(backup + pathname, {
        ...init,
        headers: { 'x-replica-secret': secret, 'content-type': 'application/json' },
        signal: AbortSignal.timeout(25000),
      });
      const text = await r.text();
      let data = {};
      try { data = JSON.parse(text); } catch { /* not JSON */ }
      res = { status: r.status, data };
      return res;
    } finally {
      account(pathname, init, res);
    }
  }

  /** Everything Cloudflare has, into this Pi. */
  async function importAll() {
    const entries = [];
    let cursor = null;
    do {
      const r = await call('/internal/export' + (cursor ? '?cursor=' + encodeURIComponent(cursor) : ''));
      if (r.status !== 200) throw new Error('export answered ' + r.status);
      entries.push(...(r.data.entries || []));
      cursor = r.data.cursor || null;
    } while (cursor);
    kv.replaceAll(entries, skipKey);
    dirty.clear();
    copied.clear();
    info.lastImportAt = now();
    info.lastImportCount = entries.length;
    await kv.put('repl:imported', String(now())); // local note: the first copy is done
    log(`brought ${entries.length} entries over from Cloudflare`);
    return entries.length;
  }

  const imported = () => kv.map.has('repl:imported');

  /** Is this changed record worth a save on Cloudflare now? Real changes are; a phone's routine check-in only once in a few hours. */
  function worthSending(k, rec) {
    const fp = fingerprint(k, rec);
    if (fp === null) return true;
    const seen = copied.get(k);
    return !seen || seen.fp !== fp || now() - seen.at >= volatileRefreshMs;
  }

  /** Copies what changed. Returns what happened (for the log and the tests). */
  async function flush() {
    if (busy) return { skipped: true };
    busy = true;
    info.lastTriedAt = now();
    try {
      let st;
      try {
        st = await call('/internal/status');
        info.backupReachable = true;
      } catch (e) {
        info.backupReachable = false;
        info.lastError = 'Cloudflare could not be reached: ' + String(e.message || e);
        return { offline: true, error: String(e.message || e) }; // no internet: the changes stay and go next time
      }
      if (st.status !== 200) { info.lastError = 'Cloudflare answered ' + st.status + ' (is the REPLICA_SECRET the same on both?)'; return { error: 'status answered ' + st.status }; }
      let tookBack = false;
      // The first copy of everything that Cloudflare has did not happen yet (no address or secret before): now.
      if (!imported()) await importAll();
      if (st.data.outage) {
        await importAll();
        const ack = await call('/internal/ack', { method: 'POST', body: '{}' });
        if (ack.status !== 200) throw new Error('ack answered ' + ack.status);
        tookBack = true;
        info.outagesRecovered++;
        log('phones had used Cloudflare: its changes are back here, Pi is the main server again');
        if (typeof onOutage === 'function') {
          try { onOutage({ from: Number(st.data.outage) || undefined, to: now() }); } catch { /* the history is a courtesy */ }
        }
      }
      const puts = [];
      const deletes = [];
      for (const [k, rec] of dirty) {
        if (skipKey(k)) continue;
        if (rec) { if (worthSending(k, rec)) puts.push({ k, r: rec }); }
        else deletes.push(k);
      }
      let sent = 0;
      for (let i = 0; i < puts.length || (i === 0 && deletes.length); i += BATCH) {
        const body = { puts: puts.slice(i, i + BATCH), deletes: i === 0 ? deletes.slice(0, BATCH) : [] };
        const r = await call('/internal/replicate', { method: 'POST', body: JSON.stringify(body) });
        if (r.status === 409) { // a phone used Cloudflare since the check above: start again from the top next time
          return { tookBack, outageAgain: true };
        }
        if (r.status !== 200) throw new Error('replicate answered ' + r.status);
        sent += body.puts.length + body.deletes.length;
        if (i === 0 && puts.length <= BATCH) break;
      }
      // deletes beyond the first batch
      for (let i = BATCH; i < deletes.length; i += BATCH) {
        const r = await call('/internal/replicate', { method: 'POST', body: JSON.stringify({ puts: [], deletes: deletes.slice(i, i + BATCH) }) });
        if (r.status !== 200) throw new Error('replicate answered ' + r.status);
        sent += Math.min(BATCH, deletes.length - i);
      }
      // What was sent is done; a phone record that changed in nothing but its routine check-in stays waiting for its next turn.
      const at = now();
      for (const p of puts) { dirty.delete(p.k); copied.set(p.k, { fp: fingerprint(p.k, p.r), at }); }
      for (const k of deletes) { dirty.delete(k); copied.delete(k); }
      for (const k of [...dirty.keys()]) if (skipKey(k)) dirty.delete(k);
      info.lastCopyAt = at;
      info.lastCopyCount = sent;
      info.lastError = '';
      if (sent) log(`copied ${sent} changes to Cloudflare`);
      return { tookBack, sent };
    } catch (e) {
      log('copy to Cloudflare did not work: ' + String(e.message || e));
      info.lastError = String(e.message || e);
      return { error: String(e.message || e) };
    } finally {
      busy = false;
    }
  }

  kv.onChange = (k, rec) => {
    if (skipKey(k)) return;
    dirty.set(k, rec);
    // Only a real change calls for a quick copy; a phone's check-in waits for the regular one.
    const fp = fingerprint(k, rec);
    const seen = copied.get(k);
    const real = fp === null || !seen || seen.fp !== fp;
    if (real && !soon) {
      soon = setTimeout(() => { soon = null; flush(); }, soonMs);
      if (soon.unref) soon.unref();
    }
  };
  const timer = setInterval(flush, intervalMs);
  if (timer.unref) timer.unref();

  // A new Pi starts with everything from Cloudflare (tried again at every start and every copy until it works).
  const first = !imported() ? importAll().catch((e) => log('first copy from Cloudflare did not work (tried again later): ' + String(e.message || e))) : Promise.resolve();

  const status = () => {
    rollDay();
    let waiting = 0;
    for (const [k, rec] of dirty) if (!skipKey(k) && (rec ? worthSending(k, rec) : true)) waiting++;
    return { configured: true, backupUrl: backup, pending: waiting, routineWaiting: dirty.size - waiting, ...info, firstCopyDone: imported(), usage: { ...usage, limits: CLOUDFLARE_FREE_LIMITS } };
  };
  return {
    stop() {
      clearInterval(timer);
      if (soon) clearTimeout(soon);
      if (usageTimer) { clearTimeout(usageTimer); usageTimer = null; try { kv.put('repl:usage', JSON.stringify(usage)).catch(() => {}); } catch { /* closing */ } }
    },
    flush, importAll, first, dirty, status,
  };
}
