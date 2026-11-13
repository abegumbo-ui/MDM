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

echo "== program files"
rm -rf "$APP/src" "$APP/pi"
cp -r "$SRC/src" "$SRC/pi" "$APP/"
rm -f "$APP/pi/deploy.ps1" "$APP/pi/README.md"
printf '{ "name": "mdm-pi", "private": true, "type": "module" }\n' > "$APP/package.json"
chown -R root:root "$APP"
chown -R "$SVC_USER:$SVC_USER" "$DATA"

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
