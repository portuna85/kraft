#!/usr/bin/env bash
# Step 4: 통계 summary 테이블을 리빌드한다.
# winning_numbers 데이터 임포트 후 frequency/pattern/companion 캐시는 비어 있으므로
# StatisticsSummaryRebuilder.rebuildAllSummaries() 를 API를 통해 트리거한다
# (WinningStatisticsCacheService는 캐시 미스 시 이 메서드로 위임만 한다).
#
# 사전 조건: backend 컨테이너가 기동 중이고 actuator/health = UP 이어야 한다.
set -euo pipefail

: "${KRAFT_BACKEND_INTERNAL_URL:=http://localhost:8080}"
: "${KRAFT_OPS_TOKEN:?KRAFT_OPS_TOKEN not set}"

HEALTH_URL="$KRAFT_BACKEND_INTERNAL_URL/actuator/health"
STATS_URL="$KRAFT_BACKEND_INTERNAL_URL/api/v1/stats/frequency"

echo "==> [04] Checking backend health at $HEALTH_URL ..."
for i in {1..24}; do
  status=$(curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null \
    | grep -o '"status":"[A-Z]*"' | head -1 | cut -d'"' -f4 || true)
  if [[ "$status" == "UP" ]]; then
    echo "  Backend UP (attempt $i)"
    break
  fi
  echo "  Waiting... ($i/24)"
  sleep 5
done

if [[ "${status:-}" != "UP" ]]; then
  echo "ERROR: backend not healthy — cannot trigger stats rebuild" >&2
  exit 1
fi

# GET /api/v1/stats/frequency triggers the summary→fallback-rebuild pattern
# (WinningStatisticsCacheService.getFrequencyStats() delegates to
# StatisticsSummaryRebuilder.rebuildAllSummaries() when the cache is empty)
echo "==> [04] Triggering statistics rebuild via GET /api/v1/stats/frequency ..."
HTTP_CODE=$(curl -o /dev/null -fsS -w "%{http_code}" --max-time 60 "$STATS_URL")
if [[ "$HTTP_CODE" == "200" ]]; then
  echo "  OK: statistics rebuilt (HTTP $HTTP_CODE)"
else
  echo "  WARN: unexpected HTTP $HTTP_CODE from stats endpoint" >&2
fi

# Also trigger pattern and companion to warm all caches
for path in patterns companion; do
  code=$(curl -o /dev/null -fsS -w "%{http_code}" --max-time 60 \
    "$KRAFT_BACKEND_INTERNAL_URL/api/v1/stats/$path" || echo "000")
  echo "  /api/v1/stats/$path → HTTP $code"
done

echo "==> [04] Statistics rebuild complete."
