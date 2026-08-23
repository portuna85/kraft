#!/usr/bin/env bash
# BE-SEC-02(docs/improvement.md) 6번 항목: docker-compose.prod.yml:6-15의 I-11 재설계 지침대로,
# app 네트워크 서브넷을 compose ipam으로 강제 고정하지 않고(그 시도는 2026-08-16 mariadb 장애로
# 되돌렸다) Docker가 이미 자동 할당한 서브넷을 조회만 한다. 배포 호스트에서 이 스크립트를 실행해
# 나온 값을 KRAFT_SECURITY_TRUSTED_PROXY_CIDR로 .env.prod에 직접 넣는다 — 네트워크 재생성이
# 필요 없으므로 backend 재기동만으로 반영된다.
#
# 사용법: bash scripts/deploy/resolve-trusted-proxy-cidr.sh [network-name]
#   network-name 생략 시 kraft-app(docker-compose.prod.yml:409의 app 네트워크 이름).
set -euo pipefail

NETWORK="${1:-kraft-app}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker CLI를 찾을 수 없습니다. 배포 호스트에서 실행하세요." >&2
  exit 1
fi

if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  echo "ERROR: 네트워크 '$NETWORK'를 찾을 수 없습니다. 스택이 기동 중인지, 이름이 맞는지 확인하세요." >&2
  exit 1
fi

subnet="$(docker network inspect "$NETWORK" -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}' 2>/dev/null || true)"

if [[ -z "$subnet" ]]; then
  echo "ERROR: 네트워크 '$NETWORK'에 IPAM 서브넷 정보가 없습니다." >&2
  exit 1
fi

echo "네트워크 '$NETWORK'의 자동 할당 서브넷: $subnet"
echo "KRAFT_SECURITY_TRUSTED_PROXY_CIDR=$subnet"
