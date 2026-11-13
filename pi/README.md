# The MDM server on a Raspberry Pi (Cloudflare as the backup)

The dashboard and the phone API are the same code as the Cloudflare Worker (`src/index.js`). On the Pi, Node runs it
(`pi/server.mjs`) with its data in files (`pi/kv.mjs`). The phones use the Pi first and Cloudflare only while the Pi does not
answer. Nothing here costs money: the Pi is yours, Tailscale's personal plan is free, and Cloudflare's free plan only
serves as the backup.

```
 phones ──► https://mdmpi.<you>.ts.net  (Tailscale Funnel) ──► Pi (Node, port 8787, data in /var/lib/mdm-pi)
   │                                                              │  copies changes in batches (every 30 min, and soon after
   │  only while the Pi does not answer                           │  a change) to Cloudflare, and takes them back after an outage
   └──────────────────────────────────────────────────────────► Cloudflare Worker (ROLE=backup)
```

## What you need (once)

1. A Raspberry Pi with Raspberry Pi OS Lite on the SD card, on the internet, with the user `mdm` and your SSH key
   (pi/README: `cloud-init` user-data does this; the card already has it).
2. A free Tailscale account (login.tailscale.com). In its admin console enable **HTTPS certificates** and **Funnel** (the
   setup script prints a link for each, once).
3. Four variables on the Cloudflare Worker `mdm` (Settings → Variables and Secrets), values from
   `Documents\MDM-Pi-settings.txt`:
   `ROLE=backup`, `REPLICA_SECRET=<same as in the file>`, `PRIMARY_URL=<the Pi's https address>`, `BACKUP_URL=<the Worker's own address>`.
   (Add `REPLICA_SECRET` as a *Secret*.)

## Install (from the PC)

```powershell
.\pi\deploy.ps1 -Target "<the Pi's address>" -BackupUrl "https://mdm.<you>.workers.dev"
```
It copies the program, installs Node.js, makes the service `mdm-pi` (starts on every boot, restarts if it stops), brings the
data over from Cloudflare (the first time), and runs `tailscale-setup.sh` (prints a login link the first time).
Running it again updates the Pi and keeps the data and the settings.

## How they stay in step

* Pi → Cloudflare: changes are copied in batches (Cloudflare's free plan allows about 1,000 saves a day, so only the newest
  value of each entry is sent). Screenshots, uploaded apps and the like stay on the Pi.
* A phone that cannot reach the Pi uses Cloudflare and says so (`viaBackup`): Cloudflare notes "outage" and saves changes
  there. When the Pi is back it first asks Cloudflare, takes those changes (`/internal/export`), tells it (`/internal/ack`),
  and is the main server again. A brand-new Pi starts by taking everything from Cloudflare.
* `/internal/*` needs the shared `REPLICA_SECRET`; without it (or without the variables) it is switched off.

## Checks

* `curl http://127.0.0.1:8787/healthz` on the Pi: `{"ok":true,"role":"main","entries":...,"rssMB":...}`
* `journalctl -u mdm-pi -f` for the log.
* Tests: `npm test` (see `test/pi.test.js`: storage, the dashboard running on Node, copying and recovery).

## Limits (honest)

* A Pi Zero 2 W has 512 MB of memory: big values live on the SD card, and the service is capped at about 200 MB. Many
  phones with many screenshots want a Pi 4/5.
* Tailscale Funnel has soft bandwidth limits: fine for phone check-ins, not for streaming.
* Cloudflare's free plan stops (it never bills) at about 100,000 requests a day: only matters while the Pi is off.
