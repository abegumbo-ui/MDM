// A small stand-in for Cloudflare's KV storage, for running the dashboard on a Raspberry Pi.
// Same calls the dashboard code makes: get(key[, "json" | "arrayBuffer"]), put(key, value, {expirationTtl, expiration, metadata}),
// delete(key), list({prefix, cursor, limit}). Small values are kept in memory and written to disk:
//   kv.json  = a snapshot, kv.log = every change since the snapshot (one JSON line each).
// Big values (screenshots, uploaded apps: over 64 KB) stay on the SD card in blobs/ and are read when asked for,
// so a Pi Zero 2 W (512 MB of memory) never holds them. On start the snapshot is read and the log replayed.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { skipKey } from '../src/replicate.js';

const PAGE = 1000;
const BIG = 64 * 1024;

function pack(value) {
  if (typeof value === 'string') return { t: 's', v: value };
  const bytes = value instanceof ArrayBuffer ? new Uint8Array(value) : new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
  return { t: 'b', v: Buffer.from(bytes).toString('base64') };
}

const blobName = (key) => crypto.createHash('sha1').update(key).digest('hex');

export class KvStore {
  constructor(dir, { onChange } = {}) {
    this.dir = dir;
    this.blobDir = path.join(dir, 'blobs');
    this.map = new Map(); // key -> { t, v | f, exp?, meta? }   (f = a file in blobs/)
    this.onChange = onChange || null; // (key, rec|null) after every change, for the copy kept on Cloudflare
    this.logBytes = 0;
    this.timer = null;
  }

  async init() {
    fs.mkdirSync(this.blobDir, { recursive: true });
    const snap = path.join(this.dir, 'kv.json');
    const log = path.join(this.dir, 'kv.log');
    if (fs.existsSync(snap)) {
      const data = JSON.parse(fs.readFileSync(snap, 'utf8'));
      for (const [k, rec] of Object.entries(data)) this.map.set(k, rec);
    }
    if (fs.existsSync(log)) {
      for (const line of fs.readFileSync(log, 'utf8').split('\n')) {
        if (!line.trim()) continue;
        try {
          const e = JSON.parse(line);
          if (e.d) this.map.delete(e.k);
          else this.map.set(e.k, e.r);
        } catch {
          // a half-written last line after a power cut is ignored
        }
      }
    }
    this.purge();
    this.snapshot();
    this.sweepBlobs();
    this.fd = fs.openSync(log, 'a');
    this.timer = setInterval(() => { this.purge(); this.snapshot(); }, 10 * 60 * 1000);
    this.timer.unref();
  }

  now() {
    return Date.now();
  }

  // ---------- big values ----------

  blobPath(rec) {
    return path.join(this.blobDir, rec.f);
  }

  readBlob(rec) {
    return fs.readFileSync(this.blobPath(rec));
  }

  dropBlob(rec) {
    if (rec && rec.f) {
      try { fs.unlinkSync(this.blobPath(rec)); } catch { /* already gone */ }
    }
  }

  /** Files in blobs/ that no entry points at (left by a power cut) are removed. */
  sweepBlobs() {
    const used = new Set();
    for (const r of this.map.values()) if (r.f) used.add(r.f);
    for (const f of fs.readdirSync(this.blobDir)) if (!used.has(f)) { try { fs.unlinkSync(path.join(this.blobDir, f)); } catch { /* ignore */ } }
  }

  /** A record with its value in it (what the other server and the snapshot of old versions expect). */
  inflate(rec) {
    if (!rec || !rec.f) return rec;
    const raw = this.readBlob(rec);
    const { f, ...rest } = rec;
    return { ...rest, v: rec.t === 'b' ? raw.toString('base64') : raw.toString('utf8') };
  }

  /** Puts a record under a key: a big value goes to a file of its own. */
  store(key, rec) {
    const old = this.map.get(key);
    if (rec.v !== undefined && rec.v.length > BIG) {
      const f = blobName(key);
      fs.writeFileSync(path.join(this.blobDir, f), rec.t === 'b' ? Buffer.from(rec.v, 'base64') : Buffer.from(rec.v, 'utf8'));
      const { v, ...rest } = rec;
      rec = { ...rest, f };
    }
    if (old && old.f && old.f !== rec.f) this.dropBlob(old);
    this.map.set(key, rec);
    return rec;
  }

  unpack(rec, type) {
    if (!rec) return null;
    let raw;
    let isBytes;
    if (rec.f) {
      raw = this.readBlob(rec);
      isBytes = true;
      if (rec.t === 's') { raw = raw.toString('utf8'); isBytes = false; }
    } else if (rec.t === 'b') {
      raw = Buffer.from(rec.v, 'base64');
      isBytes = true;
    } else {
      raw = rec.v;
      isBytes = false;
    }
    if (type === 'json') return JSON.parse(isBytes ? raw.toString('utf8') : raw);
    if (type === 'arrayBuffer') {
      const b = isBytes ? raw : Buffer.from(raw, 'utf8');
      return b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
    }
    return isBytes ? raw.toString('utf8') : raw;
  }

  purge() {
    const t = this.now();
    for (const [k, r] of this.map) if (r.exp && r.exp <= t) { this.dropBlob(r); this.map.delete(k); }
  }

  snapshot() {
    const snap = path.join(this.dir, 'kv.json');
    const tmp = snap + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(Object.fromEntries(this.map)));
    fs.renameSync(tmp, snap);
    if (this.fd) fs.closeSync(this.fd);
    fs.writeFileSync(path.join(this.dir, 'kv.log'), '');
    this.fd = fs.openSync(path.join(this.dir, 'kv.log'), 'a');
    this.logBytes = 0;
  }

  append(entry) {
    const line = JSON.stringify(entry) + '\n';
    fs.writeSync(this.fd, line);
    this.logBytes += line.length;
    if (this.logBytes > 20 * 1024 * 1024) this.snapshot();
  }

  live(key) {
    const r = this.map.get(key);
    if (!r) return null;
    if (r.exp && r.exp <= this.now()) {
      this.dropBlob(r);
      this.map.delete(key);
      return null;
    }
    return r;
  }

  async get(key, type) {
    if (type && typeof type === 'object') type = type.type;
    return this.unpack(this.live(key), type);
  }

  async put(key, value, opts = {}) {
    const rec = pack(value);
    if (opts.expirationTtl) rec.exp = this.now() + Number(opts.expirationTtl) * 1000;
    if (opts.expiration) rec.exp = Number(opts.expiration) * 1000;
    if (opts.metadata) rec.meta = opts.metadata;
    const kept = this.store(key, rec);
    this.append({ k: key, r: kept });
    // Big local-only things (screenshots...) are never copied, so they are not read back here.
    if (this.onChange && !skipKey(key)) this.onChange(key, this.inflate(kept));
  }

  async delete(key) {
    const old = this.map.get(key);
    if (!old) return;
    this.dropBlob(old);
    this.map.delete(key);
    this.append({ k: key, d: 1 });
    if (this.onChange) this.onChange(key, null);
  }

  async list({ prefix = '', cursor, limit } = {}) {
    this.purge();
    const keys = [...this.map.keys()].filter((k) => k.startsWith(prefix)).sort();
    const start = cursor ? Math.max(0, keys.findIndex((k) => k > cursor)) : 0;
    const size = Math.min(limit || PAGE, PAGE);
    const slice = cursor && keys.every((k) => k <= cursor) ? [] : keys.slice(start, start + size);
    const complete = start + slice.length >= keys.length || slice.length === 0;
    return {
      keys: slice.map((name) => {
        const r = this.map.get(name);
        return { name, ...(r && r.exp ? { expiration: Math.floor(r.exp / 1000) } : {}), ...(r && r.meta ? { metadata: r.meta } : {}) };
      }),
      list_complete: complete,
      ...(complete ? {} : { cursor: slice[slice.length - 1] }),
    };
  }

  /** Every live entry with its value, for the copy on Cloudflare: [{k, r}]. */
  entries() {
    this.purge();
    return [...this.map].map(([k, r]) => ({ k, r: this.inflate(r) }));
  }

  /**
   * Replaces everything with `entries` (the changes made on Cloudflare while the Pi was off, or the very first copy).
   * keep(key) = true keeps this server's own entry for that key (things that are never copied between the two).
   */
  replaceAll(entries, keep) {
    const old = this.map;
    this.map = new Map();
    for (const e of entries) this.store(e.k, { ...e.r });
    if (keep) {
      for (const [k, r] of old) {
        if (keep(k)) {
          const was = this.map.get(k);
          if (was && was.f && was.f !== r.f) this.dropBlob(was);
          this.map.set(k, r);
        }
      }
    }
    for (const [k, r] of old) {
      const now = this.map.get(k);
      if (r.f && (!now || now.f !== r.f)) this.dropBlob(r);
    }
    this.snapshot();
    this.sweepBlobs();
  }

  get size() {
    return this.map.size;
  }

  /** Puts one raw record (from the other server). */
  setRaw(key, rec) {
    if (rec) {
      const kept = this.store(key, { ...rec });
      this.append({ k: key, r: kept });
    } else {
      const old = this.map.get(key);
      this.dropBlob(old);
      this.map.delete(key);
      this.append({ k: key, d: 1 });
    }
  }

  close() {
    if (this.timer) clearInterval(this.timer);
    try { this.snapshot(); fs.closeSync(this.fd); } catch { /* closing anyway */ }
  }
}
