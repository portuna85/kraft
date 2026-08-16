#!/usr/bin/env bash
# I-08: playwright.visual.config.ts의 정책 — 베이스라인은 로컬(Windows 등)이 아니라
# CI(Linux, mcr.microsoft.com/playwright 이미지)에서만 만든다. Windows에서 실수로
# `npx playwright test --update-snapshots`를 돌리면 `*-win32.png`가 새로 생겨 커밋될
# 수 있는데, 지금까지는 이를 막는 도구가 없이 코드 주석의 정책 설명에만 의존했다.
# 저장소에 `*-linux.png`가 아닌 스냅샷 파일이 하나라도 있으면 실패시킨다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mapfile -t BAD_SNAPSHOTS < <(find web/e2e/visual -type d -name "*-snapshots" -exec find {} -type f -name "*.png" \; 2>/dev/null | grep -v -- "-linux\.png$" || true)

if (( ${#BAD_SNAPSHOTS[@]} > 0 )); then
  echo "ERROR: 다음 시각 회귀 베이스라인이 -linux.png가 아니다 — 로컬(비-Linux) 실행에서" >&2
  echo "       생성돼 커밋됐을 가능성이 높다. CI(mcr.microsoft.com/playwright 이미지) 실행" >&2
  echo "       결과의 actual.png만 베이스라인으로 채택한다(playwright.visual.config.ts 참고)." >&2
  echo "       로컬에서 같은 컨테이너로 재현하려면: npm run test:e2e:visual:docker" >&2
  for f in "${BAD_SNAPSHOTS[@]}"; do echo "  - $f" >&2; done
  exit 1
fi

echo "OK: 시각 회귀 베이스라인이 전부 -linux.png다"
