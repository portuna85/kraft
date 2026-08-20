#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="${1:-}"
HEAD_SHA="${2:-HEAD}"
OUTPUT_FILE="${GITHUB_OUTPUT:-}"

backend=false
web=false
infra=false

# M-14: web-legacy/ 삭제 이후 프론트엔드는 web/ 하나뿐이라 web_next라는 별도
# 변수가 더 이상 필요 없다 — web이 곧 web/(Phase 10부터 실제 kraft.io.kr을
# 서빙하는 배포 대상 프론트엔드)를 가리킨다.
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
      web/*)
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
      # RSP 진단서 커밋(docs/improvement_claude.md)에서 드러난 구멍 — 문서만 바꾼
      # 커밋에 .gitignore가 하나 끼자 아래 `*)` 폴백으로 떨어져 mark_all이 됐고,
      # ci.yml의 paths-ignore(docs/**, **/*.md)를 무력화한 채 전체 검증 + 전체
      # 프로덕션 배포까지 이어졌다. 이 세 파일은 무엇을 추적/포맷할지만 정할 뿐
      # 빌드 산출물·런타임 동작에 관여하지 않는다. 컨테이너 빌드 컨텍스트를 바꾸는
      # .dockerignore는 여기가 아니라 위 backend 케이스에 있다 — 혼동하지 말 것.
      .gitignore|.gitattributes|.editorconfig)
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
