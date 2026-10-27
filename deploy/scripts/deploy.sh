#!/bin/bash
# Build locally and release to the VMs. Usage:
#   deploy/scripts/deploy.sh api|web|all        build, upload a timestamped release, switch, restart, health check
#   deploy/scripts/deploy.sh rollback api|web   switch back to the previous release
# Hosts come from deploy/hosts.env (WEB_HOST, API_HOST, SSH_USER; optional TRUSTED_PROXY). Requires key-based SSH and sudo on the VMs.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
# shellcheck source=/dev/null
. "$ROOT/deploy/hosts.env"
SSH="ssh -o BatchMode=yes"
STAMP=$(date -u +%Y%m%d%H%M%S)
export LC_ALL=C.UTF-8

remote() { $SSH "$SSH_USER@$1" "LC_ALL=C.UTF-8 sudo bash -s" ; }

release_api() {
  (cd "$ROOT/backend" && JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21} mvn -q -DskipTests package)
  scp -q "$ROOT/backend/target/lendly-api-0.2.0.jar" "$SSH_USER@$API_HOST:/tmp/lendly-api.jar"
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
  # Fill the optional trusted-proxy placeholder; without a value both real_ip lines are removed so nginx -t never sees it.
  TRUSTED_PROXY=${TRUSTED_PROXY:-}
  if [ -n "$TRUSTED_PROXY" ]; then
    [[ "$TRUSTED_PROXY" =~ ^[0-9a-fA-F:./]+$ ]] || { echo "TRUSTED_PROXY must be an IP address or CIDR" >&2; exit 1; }
    PROXY_SED="s#__TRUSTED_PROXY__#$TRUSTED_PROXY#g"
  else
    PROXY_SED="/__TRUSTED_PROXY__/d;/real_ip_header/d"
  fi
  (cd "$ROOT/frontend" && npx ng build >/dev/null)
  COPYFILE_DISABLE=1 tar --no-mac-metadata -C "$ROOT/frontend/dist/lendly/browser" -czf /tmp/lendly-web.tgz .
  scp -q /tmp/lendly-web.tgz "$SSH_USER@$WEB_HOST:/tmp/lendly-web.tgz"
  scp -q "$ROOT/deploy/nginx/lendly.conf" "$SSH_USER@$WEB_HOST:/tmp/lendly.conf"
  rm -f /tmp/lendly-web.tgz
  remote "$WEB_HOST" <<EOF
set -e
install -d -m 755 /var/www/lendly/releases/$STAMP
tar -C /var/www/lendly/releases/$STAMP -xzf /tmp/lendly-web.tgz
rm -f /tmp/lendly-web.tgz
sed "s/__API_HOST__/$API_HOST/g; s/__WEB_HOST__/$WEB_HOST/g; $PROXY_SED" /tmp/lendly.conf > /etc/nginx/sites-available/lendly.conf
rm -f /tmp/lendly.conf
ln -sfn /etc/nginx/sites-available/lendly.conf /etc/nginx/sites-enabled/lendly.conf
rm -f /etc/nginx/sites-enabled/default
[ -L /var/www/lendly/current ] && ln -sfn "\$(readlink /var/www/lendly/current)" /var/www/lendly/previous
ln -sfn /var/www/lendly/releases/$STAMP /var/www/lendly/current
nginx -t -q && systemctl reload nginx
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
