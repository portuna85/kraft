#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="${1:-}"
HEAD_SHA="${2:-HEAD}"
OUTPUT_FILE="${GITHUB_OUTPUT:-}"

backend=false
web=false
web_next=false
infra=false

# web      = web-legacy/ (곧 삭제될 레거시, Phase 11) — 자체 게이트(lint·test)만 돈다.
# web_next = web/ (Phase 10부터 실제 배포되는 프론트엔드) — 변수 이름은 재작성 기간의
#            흔적이라 뒤바뀐 것처럼 보이지만, docker-publish-web은 이제 web_next로
#            게이트한다(ci.yml 참고). web-legacy 삭제 시(Phase 11) web_next을 web으로
#            정리하고 이 코멘트도 지운다.
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
        # 새 구현은 아직 배포되지 않는다 — 레거시 이미지 빌드·배포를 깨우지 않는다.
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
