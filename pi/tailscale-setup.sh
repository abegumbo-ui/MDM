#!/usr/bin/env bash
# Gives the Pi a permanent public https address with Tailscale Funnel (free: no domain, no router settings, no cost)
# and lets you log in to the Pi from anywhere (Tailscale SSH). Run as root:  sudo bash tailscale-setup.sh
# The first time it prints a link to click once (exit code 2); run it again after clicking. It may also print
# a link to enable HTTPS / Funnel for your Tailscale account (once). When everything is on it prints
#   PUBLIC-ADDRESS=https://mdmpi.<your-name>.ts.net
# and puts that address in /etc/mdm-pi.env (PUBLIC_URL and PRIMARY_URL).
set -euo pipefail

PORT=8787
ENVF=/etc/mdm-pi.env

jsonpick() { # jsonpick '<js expression using j>'
  node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const j=JSON.parse(s);console.log('"$1"')}catch(e){console.log("")}})'
}

setvar() { # setvar KEY VALUE
  if grep -q "^$1=" "$ENVF"; then sed -i "s|^$1=.*|$1=$2|" "$ENVF"; else echo "$1=$2" >> "$ENVF"; fi
}

if ! command -v tailscale >/dev/null 2>&1; then
  echo "== installing Tailscale"
  curl -fsSL https://tailscale.com/install.sh | sh
fi
systemctl enable --now tailscaled
sleep 2

state=$(tailscale status --json 2>/dev/null | jsonpick 'j.BackendState || ""')
if [ "$state" != "Running" ]; then
  echo "== logging in to Tailscale (state: ${state:-unknown})"
  if [ -f /tmp/ts-authkey ]; then
    tailscale up --ssh --hostname=mdmpi --auth-key="$(cat /tmp/ts-authkey)"
    rm -f /tmp/ts-authkey
  else
    pkill -f 'tailscale up' >/dev/null 2>&1 || true
    nohup tailscale up --ssh --hostname=mdmpi > /tmp/ts-up.log 2>&1 &
    for i in $(seq 1 40); do
      link=$(grep -o 'https://login.tailscale.com/a/[A-Za-z0-9]*' /tmp/ts-up.log | head -1 || true)
      [ -n "$link" ] && break
      sleep 1
    done
    echo "LOGIN-LINK=${link:-not-printed-yet}"
    echo "Click that link once (sign in with your Tailscale account and press Connect), then run this script again."
    exit 2
  fi
fi

echo "== publishing the server to the internet (Funnel)"
out=$(tailscale funnel --bg "$PORT" 2>&1 || true)
echo "$out"
if echo "$out" | grep -qiE 'not enabled|enable|https://login.tailscale.com'; then
  echo "FUNNEL-NEEDS-ENABLING: open the link above in your browser, press the Enable button, then run this script again."
  exit 3
fi

name=$(tailscale status --json | jsonpick '(j.Self && j.Self.DNSName || "").replace(/\.$/, "")')
[ -n "$name" ] || { echo "no public name yet"; exit 4; }
addr="https://$name"
setvar PUBLIC_URL "$addr"
setvar PRIMARY_URL "$addr"
systemctl restart mdm-pi.service
echo "PUBLIC-ADDRESS=$addr"
