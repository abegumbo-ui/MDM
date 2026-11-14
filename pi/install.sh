#!/usr/bin/env bash
# Installs or updates the MDM server on a Raspberry Pi (Raspberry Pi OS Lite). Run as root, after pi/deploy.ps1 (or by hand)
# has copied the project to /tmp/mdm-pi-src and the settings file to /tmp/mdm-pi.env:
#   sudo bash /tmp/mdm-pi-src/pi/install.sh
# Safe to run again: it updates the code and keeps the data and the settings.
set -euo pipefail

APP=/opt/mdm-pi
DATA=/var/lib/mdm-pi
ENVF=/etc/mdm-pi.env
SVC_USER=mdmsrv
SRC=/tmp/mdm-pi-src

[ "$(id -u)" = 0 ] || { echo "run as root (sudo)"; exit 1; }
[ -d "$SRC/src" ] && [ -d "$SRC/pi" ] || { echo "the project is not in $SRC"; exit 1; }

node_ok() { command -v node >/dev/null 2>&1 && [ "$(node -p 'process.versions.node.split(".")[0]')" -ge 20 ]; }

echo "== Node.js"
if ! node_ok; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y nodejs || true
fi
if ! node_ok; then
  # the system's package is too old: the official build for this Pi
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi
node --version

echo "== service user and folders"
id -u "$SVC_USER" >/dev/null 2>&1 || useradd --system --home "$DATA" --shell /usr/sbin/nologin "$SVC_USER"
mkdir -p "$APP" "$DATA"

echo "== what the Server page in the dashboard may do (Wi-Fi networks, restart, the log, the temperature)"
# Only these exact programs, only for the service user, nothing else.
for g in systemd-journal video netdev; do getent group "$g" >/dev/null 2>&1 && usermod -aG "$g" "$SVC_USER" || true; done
NMCLI="$(command -v nmcli || echo /usr/bin/nmcli)"
SYSTEMCTL="$(command -v systemctl || echo /usr/bin/systemctl)"
cat > /etc/sudoers.d/mdm-pi <<EOF
$SVC_USER ALL=(root) NOPASSWD: $NMCLI, $SYSTEMCTL restart mdm-pi, $SYSTEMCTL reboot
EOF
chmod 440 /etc/sudoers.d/mdm-pi
visudo -cf /etc/sudoers.d/mdm-pi >/dev/null || { rm -f /etc/sudoers.d/mdm-pi; echo "sudoers check failed (Server page buttons will not work)"; }
# Tailscale: let the service user read its status.
if command -v tailscale >/dev/null 2>&1; then tailscale set --operator="$SVC_USER" >/dev/null 2>&1 || true; fi

echo "== program files"
rm -rf "$APP/src" "$APP/pi"
cp -r "$SRC/src" "$SRC/pi" "$APP/"
rm -f "$APP/pi/deploy.ps1" "$APP/pi/README.md"
if [ -f "$SRC/VERSION" ]; then cp "$SRC/VERSION" "$APP/VERSION"; fi
printf '{ "name": "mdm-pi", "private": true, "type": "module" }\n' > "$APP/package.json"
chown -R root:root "$APP"
chown -R "$SVC_USER:$SVC_USER" "$DATA"

echo "== self-repair (the watcher, the hardware watchdog and the health note)"
# 1. watch.sh runs every minute: restarts the server, brings the Wi-Fi back up and restarts Tailscale when they stop working.
install -m 755 "$APP/pi/watch.sh" /usr/local/bin/mdm-pi-watch
cat > /etc/systemd/system/mdm-pi-watch.service <<EOF
[Unit]
Description=Keep the MDM Pi reachable (restart what stopped)
[Service]
Type=oneshot
ExecStart=/usr/local/bin/mdm-pi-watch
EOF
cat > /etc/systemd/system/mdm-pi-watch.timer <<EOF
[Unit]
Description=MDM Pi watcher, every minute
[Timer]
OnBootSec=3min
OnUnitActiveSec=1min
AccuracySec=5s
[Install]
WantedBy=timers.target
EOF
# 2. The Pi's hardware watchdog: if the whole system hangs (not just the network) the board restarts itself after 10 seconds.
mkdir -p /etc/systemd/system.conf.d
cat > /etc/systemd/system.conf.d/mdm-watchdog.conf <<EOF
[Manager]
RuntimeWatchdogSec=10
RebootWatchdogSec=2min
EOF
# 3. A line a minute in the permanent log: power, heat, memory, load, Wi-Fi signal (the part of a Pi's story you need after a freeze).
cat > /usr/local/bin/mdm-health-note <<'EOF'
#!/bin/sh
# One line a minute in the permanent log: power flags (0x0 = fine; a 1 in the low 4 bits = power too low NOW, a 1 in the 0x10000 bits = it happened since boot), heat, memory, load, Wi-Fi signal.
logger -t mdm-health "throttled=$(vcgencmd get_throttled | cut -d= -f2) volts=$(vcgencmd measure_volts core | cut -d= -f2) temp=$(vcgencmd measure_temp | cut -d= -f2) memfree=$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo)MB swapused=$(awk '/SwapTotal/{t=$2}/SwapFree/{f=$2}END{print int((t-f)/1024)}')MB load=$(cut -d' ' -f1 /proc/loadavg) rssi=$(iw dev wlan0 link 2>/dev/null | awk '/signal/{print $2}')"
EOF
chmod 755 /usr/local/bin/mdm-health-note
cat > /etc/systemd/system/mdm-health-note.service <<EOF
[Unit]
Description=Write a health note to the log
[Service]
Type=oneshot
ExecStart=/usr/local/bin/mdm-health-note
EOF
cat > /etc/systemd/system/mdm-health-note.timer <<EOF
[Unit]
Description=Health note every minute
[Timer]
OnBootSec=1min
OnUnitActiveSec=1min
[Install]
WantedBy=timers.target
EOF
# Wi-Fi: never give up on a saved network after a few refusals from the router.
nmcli -t -f NAME,TYPE con show 2>/dev/null | awk -F: '$2=="802-11-wireless"{print $1}' | while IFS= read -r w; do
  nmcli con mod "$w" connection.autoconnect-retries 0 >/dev/null 2>&1 || true
done
systemctl daemon-reload
systemctl enable --now mdm-pi-watch.timer
systemctl enable --now mdm-health-note.timer 2>/dev/null || true
systemctl daemon-reexec   # makes systemd read the watchdog setting now (no restart of anything else)

echo "== settings"
if [ -f /tmp/mdm-pi.env ]; then
  # new settings given: they replace the old file (the deploy script keeps its own copy)
  install -m 600 -o root -g root /tmp/mdm-pi.env "$ENVF"
fi
[ -f "$ENVF" ] || { echo "no settings file ($ENVF); copy pi/mdm-pi.env.example to it and fill it in"; exit 1; }

echo "== service"
install -m 644 "$APP/pi/mdm-pi.service" /etc/systemd/system/mdm-pi.service
systemctl daemon-reload
systemctl enable mdm-pi.service
systemctl restart mdm-pi.service

echo "== waiting for it to answer"
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8787/healthz >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1:8787/healthz; echo
    echo "OK: the server is running."
    exit 0
  fi
  sleep 1
done
echo "the server did not answer; last log lines:"
journalctl -u mdm-pi -n 30 --no-pager
exit 1
