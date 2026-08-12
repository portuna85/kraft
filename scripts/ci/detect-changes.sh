#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="${1:-}"
HEAD_SHA="${2:-HEAD}"
OUTPUT_FILE="${GITHUB_OUTPUT:-}"

backend=false
web=false
web_next=false
infra=false

# M-06: web/web_next 변수 이름이 실제 역할과 뒤바뀐 것처럼 보인다 — 재작성 기간의
# 흔적이다. 지금 뒤바꾸면 ci.yml 전역(needs.changes.outputs.web_next를 참조하는 8개
# 잡, job outputs 선언, 요약 잡의 WEB_CHANGED/WEB_NEXT_CHANGED env)을 한 번에 고쳐야
# 하는데, 로컬에서 GitHub Actions를 실행해 검증할 방법이 없어 이름만 바꾸다 조용히
# 깨뜨릴 위험이 크다. web-legacy 삭제(M-14) 시점에는 이 변수 자체가 하나만 남으므로
# 그때 자연스럽게 정리한다 — 지금은 실제 의미만 정확히 남긴다:
#
# web      = web-legacy/ (Phase 11에 삭제 예정인 레거시) — 자체 게이트(lint·test)만 돈다.
#            더 이상 배포 이미지로 빌드되지 않는다(docker-publish-web은 web_next 기준).
# web_next = web/ (Phase 10부터 실제 kraft.io.kr을 서빙하는 배포 대상 프론트엔드).
mark_all() {
  backend=true
  web=true
  web_next=true
  infra=true
}

if [[ -z "$BASE_SHA" || "$BASE_SHA" =~ ^0+$ ]]; then
  mark_all
  changed_files="(full validation: no usable base SHA)"
else
  if ! git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null; then
    git fetch --no-tags --depth=1 origin "$BASE_SHA"
  fi

  changed_files="$(git -c core.quotepath=false diff --name-only "$BASE_SHA" "$HEAD_SHA")"
  if [[ -z "$changed_files" ]]; then
    changed_files="(no changed files)"
  fi

  while IFS= read -r path; do
    [[ -z "$path" || "$path" == "(no changed files)" ]] && continue
    case "$path" in
      .github/workflows/*|.github/actions/*|scripts/ci/*)
        mark_all
        ;;
      src/*|config/*|gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties|gradle.lockfile|gradlew|gradlew.bat|Dockerfile|.dockerignore)
        backend=true
        ;;
      web-legacy/*)
        web=true
        ;;
      web/*)
        # M-06: 이 코멘트는 예전에 "새 구현은 아직 배포되지 않는다"고 적혀 있었다 —
        # Phase 10 컷오버 이후로는 틀린 말이다. web/이 실제 배포 대상이고
        # docker-publish-web이 이 플래그로 이미지를 빌드·배포한다.
        web_next=true
        ;;
      caddy/*|infra/*|scripts/deploy/*|scripts/server/*|docker-compose*.yml|.env*.example)
        infra=true
        ;;
      scripts/check-*|scripts/verify-all.sh|.trivyignore|.github/dependabot.yml)
        mark_all
        ;;
      README.md|docs/*|*.md)
        ;;
      *)
        echo "::notice::Unknown path '$path'; running full validation."
        mark_all
        ;;
    esac
  done <<< "$changed_files"
fi

any=false
if [[ "$backend" == true || "$web" == true || "$web_next" == true || "$infra" == true ]]; then
  any=true
fi

echo "Changed files:"
printf '%s\n' "$changed_files"
echo "Scope: backend=$backend web=$web web_next=$web_next infra=$infra any=$any"

outputs=(
  "backend=$backend"
  "web=$web"
  "web_next=$web_next"
  "infra=$infra"
  "any=$any"
)

if [[ -n "$OUTPUT_FILE" ]]; then
  printf '%s\n' "${outputs[@]}" >> "$OUTPUT_FILE"
else
  printf '%s\n' "${outputs[@]}"
fi
