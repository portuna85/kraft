#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="${1:-}"
HEAD_SHA="${2:-HEAD}"
OUTPUT_FILE="${GITHUB_OUTPUT:-}"

backend=false
web=false
infra=false

mark_all() {
  backend=true
  web=true
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
      web/*|web-legacy/*)
        # 프론트엔드 재작성 기간: 배포되는 앱은 web-legacy/, 새 구현은 web/에 있다.
        # 둘 다 같은 web 스코프로 묶어야 unknown path 폴백(full validation)에 걸리지 않는다.
        web=true
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
if [[ "$backend" == true || "$web" == true || "$infra" == true ]]; then
  any=true
fi

echo "Changed files:"
printf '%s\n' "$changed_files"
echo "Scope: backend=$backend web=$web infra=$infra any=$any"

outputs=(
  "backend=$backend"
  "web=$web"
  "infra=$infra"
  "any=$any"
)

if [[ -n "$OUTPUT_FILE" ]]; then
  printf '%s\n' "${outputs[@]}" >> "$OUTPUT_FILE"
else
  printf '%s\n' "${outputs[@]}"
fi
