#!/usr/bin/env bash
# Reverts caddy/Caddyfile to a previous commit and force-recreates the caddy
# container so the reverted config actually takes effect.
# Usage: rollback-caddy.sh <previous-sha>
#
# Image rollback (rollback.sh) only swaps the backend/web image tags — it never
# touched caddy/Caddyfile, which git reset --hard had already moved to the new
# (possibly broken) commit. So a bad Caddyfile deploy could never self-heal via
# the existing rollback path. This script closes that gap.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="$REPO_ROOT/docker-compose.prod.yml"

PREV_SHA="${1:?Usage: rollback-caddy.sh <previous-sha>}"

cd "$REPO_ROOT"

echo "==> Reverting caddy/Caddyfile to $PREV_SHA"
git checkout "$PREV_SHA" -- caddy/Caddyfile

echo "==> Recreating Caddy with reverted config..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate --no-deps caddy
sleep 2

# Deliberately NOT running check-caddy-routes.sh here: that script encodes the
# *current* commit's expected routes, but PREV_SHA's Caddyfile is an older
# commit and may legitimately lack routes added since (e.g. a deploy whose
# whole point was adding a new route). Checking the reverted config against
# today's expectations would always "fail" in that case even though the
# revert itself worked fine. Just confirm Caddy is alive; the caller decides
# overall deploy success via the full smoke-test.sh.
echo "==> Verifying Caddy is responding after revert..."
code="000"
for _ in 1 2 3 4 5; do
  code=$(curl -sk -o /dev/null -w "%{http_code}" --max-time 5 \
    --resolve "${KRAFT_DOMAIN}:443:127.0.0.1" "https://${KRAFT_DOMAIN}/" 2>/dev/null || echo "000")
  [[ "$code" != "000" ]] && break
  sleep 1
done
if [[ "$code" == "000" ]]; then
  echo "ERROR: Caddy not responding after Caddyfile revert" >&2
  exit 1
fi
echo "  Caddy responding (status $code)"
