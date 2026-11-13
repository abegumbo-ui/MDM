// The Raspberry Pi side of "Pi is the main server, Cloudflare is the backup" (see replicate.js for the other side).
//  - Changes are copied to Cloudflare in batches (it allows about 1,000 saves a day), and soon after an administrator change.
//  - Before copying it asks Cloudflare whether phones had to use it while the Pi was off; if so it first brings those
//    changes back (/internal/export), tells Cloudflare (/internal/ack) and is the main server again.
//  - A brand-new Pi (nothing stored) starts by bringing everything over.
import { skipKey } from '../src/replicate.js';

const BATCH = 200;

export function startReplication({ kv, vars, log = () => {}, intervalMs = 30 * 60 * 1000, soonMs = 2 * 60 * 1000, fetchFn = fetch }) {
  const secret = vars.REPLICA_SECRET;
  const backup = String(vars.BACKUP_URL || '').replace(/\/+$/, '');
  if (!secret || !backup) return { stop() {}, flush: async () => ({ skipped: true }), importAll: async () => 0, status: () => ({ configured: false }) };

  const dirty = new Map(); // key -> record | null (the newest value to copy; null = deleted)
  const info = { lastCopyAt: 0, lastCopyCount: 0, lastImportAt: 0, lastImportCount: 0, lastError: '', lastTriedAt: 0, backupReachable: null, outagesRecovered: 0 };
  let soon = null;
  let busy = false;

  async function call(pathname, init = {}) {
    const res = await fetchFn(backup + pathname, {
      ...init,
      headers: { 'x-replica-secret': secret, 'content-type': 'application/json' },
      signal: AbortSignal.timeout(25000),
    });
    const text = await res.text();
    let data = {};
    try { data = JSON.parse(text); } catch { /* not JSON */ }
    return { status: res.status, data };
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
    info.lastImportAt = Date.now();
    info.lastImportCount = entries.length;
    await kv.put('repl:imported', String(Date.now())); // local note: the first copy is done
    log(`brought ${entries.length} entries over from Cloudflare`);
    return entries.length;
  }

  const imported = () => kv.map.has('repl:imported');

  /** Copies what changed. Returns what happened (for the log and the tests). */
  async function flush() {
    if (busy) return { skipped: true };
    busy = true;
    info.lastTriedAt = Date.now();
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
      }
      const puts = [];
      const deletes = [];
      for (const [k, rec] of dirty) {
        if (skipKey(k)) continue;
        if (rec) puts.push({ k, r: rec });
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
      dirty.clear();
      info.lastCopyAt = Date.now();
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
    if (!soon) soon = setTimeout(() => { soon = null; flush(); }, soonMs);
    if (soon.unref) soon.unref();
  };
  const timer = setInterval(flush, intervalMs);
  if (timer.unref) timer.unref();

  // A new Pi starts with everything from Cloudflare (tried again at every start and every copy until it works).
  const first = !imported() ? importAll().catch((e) => log('first copy from Cloudflare did not work (tried again later): ' + String(e.message || e))) : Promise.resolve();

  const status = () => ({ configured: true, backupUrl: backup, pending: dirty.size, ...info, firstCopyDone: imported() });
  return { stop() { clearInterval(timer); if (soon) clearTimeout(soon); }, flush, importAll, first, dirty, status };
}
