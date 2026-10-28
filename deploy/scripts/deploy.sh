#!/bin/bash
# Build locally and release to the VMs. Usage:
#   deploy/scripts/deploy.sh api|web|all        build, upload a timestamped release, switch, restart, health check
#   deploy/scripts/deploy.sh rollback api|web   switch back to the previous release
# Hosts come from deploy/hosts.env (WEB_HOST, API_HOST, SSH_USER; optional TRUSTED_PROXY), or from the file named by LENDLY_HOSTS_ENV.
# LENDLY_PREBUILT_DIR skips the local build: the jar is taken from <dir>/lendly-api.jar and the web build from <dir>/web.
# Requires key-based SSH and sudo on the VMs.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HOSTS_ENV=${LENDLY_HOSTS_ENV:-$ROOT/deploy/hosts.env}
PREBUILT=${LENDLY_PREBUILT_DIR:-}
# shellcheck source=/dev/null
. "$HOSTS_ENV"
SSH="ssh -o BatchMode=yes"
STAMP=$(date -u +%Y%m%d%H%M%S)
export LC_ALL=C.UTF-8

remote() { $SSH "$SSH_USER@$1" "LC_ALL=C.UTF-8 sudo bash -s" ; }

# Nginx trusts X-Forwarded-For from this peer, so only one proxy address or a narrow range is accepted.
valid_trusted_proxy() {
  local value=$1 addr prefix="" min max octet rest parts part count=0
  addr=${value%%/*}
  if [[ "$value" == */* ]]; then
    prefix=${value#*/}
    [[ "$prefix" =~ ^[0-9]{1,3}$ ]] || return 1
  fi
  if [[ "$addr" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]]; then
    for octet in "${BASH_REMATCH[@]:1}"; do
      [ $((10#$octet)) -le 255 ] || return 1
    done
    [ "$addr" != 0.0.0.0 ] || return 1
    min=24 max=32
  elif [[ "$addr" == *:* && "$addr" =~ ^[0-9a-fA-F:]+$ ]]; then
    [[ "$addr" != *:::* && "$addr" != :: ]] || return 1
    rest=${addr//::/}
    [ $(( (${#addr} - ${#rest}) / 2 )) -le 1 ] || return 1
    [[ "$addr" != :* || "$addr" == ::* ]] || return 1
    [[ "$addr" != *: || "$addr" == *:: ]] || return 1
    IFS=: read -r -a parts <<< "$addr"
    for part in "${parts[@]}"; do
      [ ${#part} -le 4 ] || return 1
      [ -z "$part" ] || count=$((count + 1))
    done
    if [[ "$addr" == *::* ]]; then [ $count -le 7 ] || return 1; else [ $count -eq 8 ] || return 1; fi
    min=64 max=128
  else
    return 1
  fi
  if [ -n "$prefix" ]; then
    [ $((10#$prefix)) -ge $min ] && [ $((10#$prefix)) -le $max ] || return 1
  fi
}

release_api() {
  local jar
  if [ -n "$PREBUILT" ]; then
    jar="$PREBUILT/lendly-api.jar"
  else
    (cd "$ROOT/backend" && JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21} mvn -q -DskipTests package)
    jar="$ROOT/backend/target/lendly-api-0.2.0.jar"
  fi
  [ -f "$jar" ] || { echo "API jar not found: $jar" >&2; exit 1; }
  scp -q "$jar" "$SSH_USER@$API_HOST:/tmp/lendly-api.jar"
  scp -q "$ROOT/deploy/systemd/lendly-api.service" "$SSH_USER@$API_HOST:/tmp/lendly-api.service"
  remote "$API_HOST" <<EOF
set -e
install -d -o root -g lendly -m 750 /opt/lendly/releases/$STAMP
install -o root -g lendly -m 640 /tmp/lendly-api.jar /opt/lendly/releases/$STAMP/lendly-api.jar
install -o root -g root -m 644 /tmp/lendly-api.service /etc/systemd/system/lendly-api.service
rm -f /tmp/lendly-api.jar /tmp/lendly-api.service
test -f /etc/lendly/lendly-api.env || { echo "missing /etc/lendly/lendly-api.env"; exit 1; }
[ -L /opt/lendly/current ] && ln -sfn "\$(readlink /opt/lendly/current)" /opt/lendly/previous
ln -sfn /opt/lendly/releases/$STAMP /opt/lendly/current
systemctl daemon-reload
systemctl enable --quiet lendly-api
systemctl restart lendly-api
for i in \$(seq 1 60); do curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1 && break; sleep 2; done
curl -fsS http://127.0.0.1:8080/api/health && echo
ls -1dt /opt/lendly/releases/* | tail -n +4 | xargs -r rm -rf
EOF
}

release_web() {
  local web_dir tgz tar_meta=""
  # Fill the optional trusted-proxy placeholder; without a value both real_ip lines are removed so nginx -t never sees it.
  TRUSTED_PROXY=${TRUSTED_PROXY:-}
  if [ -n "$TRUSTED_PROXY" ]; then
    valid_trusted_proxy "$TRUSTED_PROXY" || {
      echo "TRUSTED_PROXY must be one IPv4 address or a /24-/32 range, or one IPv6 address or a /64-/128 range" >&2
      exit 1
    }
    PROXY_SED="s#__TRUSTED_PROXY__#$TRUSTED_PROXY#g"
  else
    PROXY_SED="/__TRUSTED_PROXY__/d;/real_ip_header/d"
  fi
  if [ -n "$PREBUILT" ]; then
    web_dir="$PREBUILT/web"
  else
    (cd "$ROOT/frontend" && npx ng build >/dev/null)
    web_dir="$ROOT/frontend/dist/lendly/browser"
  fi
  [ -d "$web_dir" ] && [ -n "$(ls -A "$web_dir")" ] || { echo "web build not found: $web_dir" >&2; exit 1; }
  tar --version 2>/dev/null | grep -q bsdtar && tar_meta=--no-mac-metadata
  tgz=$(mktemp "${TMPDIR:-/tmp}/lendly-web.XXXXXX")
  COPYFILE_DISABLE=1 tar $tar_meta -C "$web_dir" -czf "$tgz" .
  scp -q "$tgz" "$SSH_USER@$WEB_HOST:/tmp/lendly-web.tgz"
  scp -q "$ROOT/deploy/nginx/lendly.conf" "$SSH_USER@$WEB_HOST:/tmp/lendly.conf"
  rm -f "$tgz"
  remote "$WEB_HOST" <<EOF
set -e
conf=/etc/nginx/sites-available/lendly.conf
install -d -m 755 /var/www/lendly/releases/$STAMP
tar -C /var/www/lendly/releases/$STAMP -xzf /tmp/lendly-web.tgz
rm -f /tmp/lendly-web.tgz
sed "s/__API_HOST__/$API_HOST/g; s/__WEB_HOST__/$WEB_HOST/g; $PROXY_SED" /tmp/lendly.conf > "\$conf.new"
rm -f /tmp/lendly.conf
rm -f "\$conf.bak"
[ -f "\$conf" ] && cp -p "\$conf" "\$conf.bak"
mv "\$conf.new" "\$conf"
ln -sfn "\$conf" /etc/nginx/sites-enabled/lendly.conf
rm -f /etc/nginx/sites-enabled/default
if ! nginx -t -q; then
  if [ -f "\$conf.bak" ]; then mv "\$conf.bak" "\$conf"; else rm -f "\$conf" /etc/nginx/sites-enabled/lendly.conf; fi
  rm -rf /var/www/lendly/releases/$STAMP
  echo "nginx -t failed: previous configuration restored, web release not switched" >&2
  exit 1
fi
rm -f "\$conf.bak"
[ -L /var/www/lendly/current ] && ln -sfn "\$(readlink /var/www/lendly/current)" /var/www/lendly/previous
ln -sfn /var/www/lendly/releases/$STAMP /var/www/lendly/current
systemctl reload nginx
ls -1dt /var/www/lendly/releases/* | tail -n +4 | xargs -r rm -rf
echo "web release $STAMP active"
EOF
}

rollback() {
  case "$1" in
    api) remote "$API_HOST" <<'EOF'
set -e
[ -L /opt/lendly/previous ] || { echo "no previous release"; exit 1; }
ln -sfn "$(readlink /opt/lendly/previous)" /opt/lendly/current
systemctl restart lendly-api && echo "api rolled back to $(readlink /opt/lendly/current)"
EOF
    ;;
    web) remote "$WEB_HOST" <<'EOF'
set -e
[ -L /var/www/lendly/previous ] || { echo "no previous release"; exit 1; }
ln -sfn "$(readlink /var/www/lendly/previous)" /var/www/lendly/current
systemctl reload nginx && echo "web rolled back to $(readlink /var/www/lendly/current)"
EOF
    ;;
    *) echo "rollback api|web" >&2; exit 2 ;;
  esac
}

case "${1:-}" in
  api) release_api ;;
  web) release_web ;;
  all) release_api; release_web ;;
  rollback) rollback "${2:-}" ;;
  *) echo "Usage: $0 api|web|all|rollback api|web" >&2; exit 2 ;;
esac
