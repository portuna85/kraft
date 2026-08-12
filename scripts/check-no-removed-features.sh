#!/usr/bin/env bash
# scripts/check-no-removed-features.sh
# 제거된 기능 잔재 검사 (자기참조 결함 수정판)
set -euo pipefail
FAIL=0

# 1) Flutter 부재
[ -d "app" ] && { echo "ERROR: app/ must not exist"; FAIL=1; }
find . -path ./.git -prune -o \( -name 'pubspec.yaml' -o -name '*.dart' \) -print | grep -q . \
  && { echo "ERROR: Flutter files remain"; FAIL=1; }

# 2) 푸시/FCM 부재 — 소스 한정, 자기 자신과 음성 계약 테스트(smoke-test.sh) 제외
SRC_DIRS=(src/main src/test infra scripts/deploy)
[ -d "web/src" ] && SRC_DIRS+=("web/src")
GREP_EXCLUDES=(--exclude="$(basename "$0")" --exclude="smoke-test.sh")
if grep -RIn "${GREP_EXCLUDES[@]}" -E 'feature/push|infra/fcm|firebase-admin|device_tokens|/api/v1/push' \
     "${SRC_DIRS[@]}" 2>/dev/null; then
  echo "ERROR: push/FCM references remain in source"; FAIL=1
fi

# 3) 뉴스 부재 — 단어 경계로 과매칭 방지
if grep -RInw "${GREP_EXCLUDES[@]}" -E 'news_articles|news_blocked_domain|news_blocked_keyword' \
     "${SRC_DIRS[@]}" 2>/dev/null; then
  echo "ERROR: news schema references remain"; FAIL=1
fi

# 4) 공개 회차 목록·상세 화면 부재 — /admin/rounds(운영 화면)와 내부 history 서비스는 대상 아님.
# RoundsApiController는 클래스 레벨 @RequestMapping("/api/v1/rounds") 아래 latest/freshness만
# 남아 있어야 한다. list()/byRound()가 쓰던 매핑(경로 없는 @GetMapping, "/{round}")이
# 재등장하는지만 좁혀서 검사한다(클래스 레벨 매핑 자체는 정상이므로 오탐 대상에서 제외).
ROUNDS_CONTROLLER="src/main/java/com/kraft/winningnumber/RoundsApiController.java"
[ -d "web/src/app/rounds" ] && { echo "ERROR: web/src/app/rounds must not exist (공개 회차 화면은 제거됨)"; FAIL=1; }
if [ -f "$ROUNDS_CONTROLLER" ] && grep -nE '@GetMapping\s*(\(\s*\))?\s*$|@GetMapping\("/\{round\}"\)' "$ROUNDS_CONTROLLER"; then
  echo "ERROR: 공개 rounds list/detail 매핑이 재등장했습니다"; FAIL=1
fi

exit "$FAIL"
