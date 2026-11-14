#!/usr/bin/env bash
# Keeps the Pi reachable without anyone touching it. Runs every minute from mdm-pi-watch.timer (installed by install.sh), as root.
#
# Why it exists (2026-11-14): the router kicked the Pi off its Wi-Fi at 00:50 and refused it a few times. NetworkManager decided
# the password was wrong, found nobody to ask and stopped trying for good. The Pi stayed on (the light was lit) for 17 hours with no
# network. This watcher notices "no network" and "the server does not answer" and fixes them in steps, and writes one line to the
# log whenever it does something:   journalctl -t mdm-watch
#
#   the server (127.0.0.1:8787) does not answer 3 minutes in a row   -> restart the server
#   no internet                                                       -> minute 2, 5, 8 ...: bring the Wi-Fi up again (this also
#                                                                        clears NetworkManager's "wrong password, stop" block)
#                                                                        minute 6: restart NetworkManager;  minute 9: restart Tailscale
#                                                                        minute 25 and the Pi has been on for 30+ minutes: reboot
#   internet fine but Tailscale not running, or the public address
#   (Funnel) does not answer 4 minutes in a row                       -> restart Tailscale (at most once every 20 minutes)
#
# It only restarts or reboots; it never deletes anything and never changes a setting.
set -u
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

STATE=/run/mdm-pi-watch
mkdir -p "$STATE"
log() { logger -t mdm-watch "$*"; }
num() { cat "$STATE/$1" 2>/dev/null || echo 0; }
put() { echo "$2" > "$STATE/$1"; }
now() { date +%s; }
uptime_s() { cut -d. -f1 /proc/uptime; }

# One watcher at a time (a step below can take 30 s).
exec 9>"$STATE/lock"
flock -n 9 || exit 0

BACKUP=$(grep -E '^BACKUP_URL=' /etc/mdm-pi.env 2>/dev/null | head -1 | cut -d= -f2-)
case "$BACKUP" in https://*) ;; *) BACKUP=https://www.cloudflare.com/cdn-cgi/trace ;; esac

wifi_names() { nmcli -t -f NAME,TYPE con show 2>/dev/null | awk -F: '$2=="802-11-wireless"{print $1}'; }
wifi_up() {
  nmcli radio wifi on >/dev/null 2>&1
  rfkill unblock wifi >/dev/null 2>&1
  local n
  while IFS= read -r n; do
    [ -n "$n" ] || continue
    log "bringing the Wi-Fi '$n' up again"
    timeout 25 nmcli -w 20 con up id "$n" >/dev/null 2>&1 && { log "Wi-Fi '$n' is up"; return 0; }
  done < <(wifi_names)
  return 1
}

# 1. The server itself.
r=$(curl -s -m 8 -o /dev/null -w '%{http_code} %{time_total}' http://127.0.0.1:8787/healthz 2>/dev/null || echo "000 0")
code=${r%% *}; secs=${r##* }
if [ "$code" = 200 ]; then
  put srvfail 0
  awk -v s="$secs" 'BEGIN{exit !(s>3)}' && log "the server answered slowly: ${secs}s"
else
  n=$(( $(num srvfail) + 1 )); put srvfail "$n"
  if [ "$n" -ge 3 ]; then
    log "the server has not answered for $n minutes (code $code): restarting it"
    systemctl restart mdm-pi
    put srvfail 0
  fi
fi

# 2. The internet (our own backup server answers any HTTP code when the network works).
r=$(curl -s -m 10 -o /dev/null -w '%{http_code} %{time_total}' "$BACKUP" 2>/dev/null || echo "000 0")
code=${r%% *}; secs=${r##* }
if [ "$code" != 000 ]; then
  prev=$(num netfail)
  [ "$prev" -ge 2 ] && log "the network is back after about $prev minutes"
  put netfail 0
  awk -v s="$secs" 'BEGIN{exit !(s>6)}' && log "the internet answered slowly: ${secs}s"
else
  n=$(( $(num netfail) + 1 )); put netfail "$n"
  [ "$n" -eq 1 ] && log "no internet (first minute)"
  if [ "$n" -ge 2 ] && [ $(( (n - 2) % 3 )) -eq 0 ]; then wifi_up; fi
  if [ "$n" -eq 6 ]; then log "no internet for $n minutes: restarting NetworkManager"; systemctl restart NetworkManager; fi
  if [ "$n" -eq 9 ]; then log "no internet for $n minutes: restarting Tailscale"; systemctl restart tailscaled; fi
  if [ "$n" -ge 25 ] && [ "$(uptime_s)" -gt 1800 ]; then
    log "no internet for $n minutes and the Wi-Fi steps did not help: rebooting"
    systemctl reboot
  fi
  exit 0   # nothing below can work without the internet
fi

# 3. Tailscale and the public address (only when the internet works).
ts_restart() {
  local last; last=$(num tsrestart)
  if [ $(( $(now) - last )) -ge 1200 ]; then
    log "$1: restarting Tailscale"
    put tsrestart "$(now)"
    systemctl restart tailscaled
    sleep 8
    tailscale funnel --bg 8787 >/dev/null 2>&1
  fi
}
if command -v tailscale >/dev/null 2>&1; then
  if ! tailscale status >/dev/null 2>&1; then
    n=$(( $(num tsfail) + 1 )); put tsfail "$n"
    [ "$n" -ge 3 ] && { ts_restart "Tailscale is not running ($n minutes)"; put tsfail 0; }
  else
    put tsfail 0
    name=$(tailscale status --json 2>/dev/null | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{console.log(JSON.parse(s).Self.DNSName.replace(/\.$/,""))}catch(e){console.log("")}})')
    if [ -n "$name" ]; then
      pc=$(curl -s -m 12 -o /dev/null -w '%{http_code}' "https://$name/healthz" 2>/dev/null || echo 000)
      if [ "$pc" = 200 ]; then put pubfail 0
      else
        n=$(( $(num pubfail) + 1 )); put pubfail "$n"
        [ "$n" -ge 4 ] && { ts_restart "the public address answered $pc for $n minutes"; put pubfail 0; }
      fi
    fi
  fi
fi
exit 0
