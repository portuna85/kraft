#!/usr/bin/env bash
# H-06 회귀 테스트: scripts/lib/backup-common.sh의 BACKUP_MODE 정책이 실제로
# 지켜지는지 직접 검증한다. rclone은 PATH 셰도잉으로 성공/실패를 흉내 내고, 각
# 시나리오는 exit이 테스트 프로세스 전체를 끝내지 않도록 서브셸에서 실행한다.
# db-backup.sh/caddy-data-backup.sh 전체를 돌리려면 docker compose가 필요해
# CI에서 무겁고 불안정하므로, 공유 함수만 독립적으로 검증한다.
# shellcheck disable=SC2030,SC2031 # PATH/BACKUP_REMOTE_DEST를 서브셸 안에서만 export하는 것이
# 의도다 — 각 시나리오를 격리해서, 한 시나리오의 환경 변경이 다음 시나리오로 새지 않게 한다.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib/backup-common.sh"

FAIL=0
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

FAKE_BIN="$WORKDIR/bin"
FILENAME="$WORKDIR/fake-backup.tar.gz"
TEXTFILE_DIR="$WORKDIR/textfile"
mkdir -p "$FAKE_BIN" "$TEXTFILE_DIR"
echo "dummy" > "$FILENAME"

install_fake_rclone() {
  local outcome="$1" # success | failure
  cat > "$FAKE_BIN/rclone" <<EOF
#!/usr/bin/env bash
if [[ "$outcome" == "success" ]]; then
  exit 0
else
  exit 1
fi
EOF
  chmod +x "$FAKE_BIN/rclone"
}

remove_fake_rclone() {
  rm -f "$FAKE_BIN/rclone"
}

assert_exit_code() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "  OK  [exit=$actual] $desc"
  else
    echo "  FAIL[exit=$actual != $expected] $desc" >&2
    FAIL=1
  fi
}

assert_file_exists() {
  local desc="$1" path="$2"
  if [[ -f "$path" ]]; then
    echo "  OK  [exists] $desc"
  else
    echo "  FAIL[missing: $path] $desc" >&2
    FAIL=1
  fi
}

assert_file_absent() {
  local desc="$1" path="$2"
  if [[ ! -f "$path" ]]; then
    echo "  OK  [absent] $desc"
  else
    echo "  FAIL[unexpectedly exists: $path] $desc" >&2
    FAIL=1
  fi
}

echo "==> backup_validate_mode: 잘못된 값은 exit 2"
(
  # shellcheck disable=SC2034 # backup-common.sh(소싱된 외부 파일)의 backup_validate_mode()가 읽는다
  BACKUP_MODE="bogus"
  backup_validate_mode >/dev/null 2>&1
)
assert_exit_code "BACKUP_MODE=bogus → exit 2" 2 "$?"

echo "==> local-only + BACKUP_REMOTE_DEST 미설정 → 원격 없이 exit 0, 상태 파일 없음"
rm -f "$TEXTFILE_DIR"/*
(
  unset BACKUP_REMOTE_DEST
  backup_upload_remote "$FILENAME" "local-only" "kraft_backup" "$TEXTFILE_DIR"
)
assert_exit_code "local-only + 미설정 → exit 0" 0 "$?"
assert_file_absent "local-only + 미설정: remote 성공 메트릭 없음" "$TEXTFILE_DIR/kraft_backup_remote.prom"

echo "==> offsite-required + BACKUP_REMOTE_DEST 미설정 → exit 1"
rm -f "$TEXTFILE_DIR"/*
(
  unset BACKUP_REMOTE_DEST
  backup_upload_remote "$FILENAME" "offsite-required" "kraft_backup" "$TEXTFILE_DIR"
)
assert_exit_code "offsite-required + 미설정 → exit 1" 1 "$?"

echo "==> offsite-required + rclone 실패 → exit 1, 성공 메트릭 기록 안 됨"
install_fake_rclone failure
rm -f "$TEXTFILE_DIR"/*
(
  export PATH="$FAKE_BIN:$PATH"
  export BACKUP_REMOTE_DEST="fake:bucket/path"
  backup_upload_remote "$FILENAME" "offsite-required" "kraft_backup" "$TEXTFILE_DIR"
)
assert_exit_code "offsite-required + rclone 실패 → exit 1" 1 "$?"
assert_file_absent "offsite-required + rclone 실패: remote 성공 메트릭 없음" "$TEXTFILE_DIR/kraft_backup_remote.prom"
remove_fake_rclone

echo "==> offsite-required + rclone 성공 → exit 0, 성공/상태 메트릭 둘 다 기록됨"
install_fake_rclone success
rm -f "$TEXTFILE_DIR"/*
(
  export PATH="$FAKE_BIN:$PATH"
  export BACKUP_REMOTE_DEST="fake:bucket/path"
  backup_upload_remote "$FILENAME" "offsite-required" "kraft_backup" "$TEXTFILE_DIR"
)
assert_exit_code "offsite-required + rclone 성공 → exit 0" 0 "$?"
assert_file_exists "offsite-required + rclone 성공: remote 타임스탬프 메트릭 기록됨" "$TEXTFILE_DIR/kraft_backup_remote.prom"
assert_file_exists "offsite-required + rclone 성공: remote 상태 메트릭 기록됨" "$TEXTFILE_DIR/kraft_backup_remote_status.prom"
if grep -q "kraft_backup_remote_last_status 1" "$TEXTFILE_DIR/kraft_backup_remote_status.prom"; then
  echo "  OK  [content] remote 상태 메트릭 값이 1"
else
  echo "  FAIL[content] remote 상태 메트릭 값이 1이 아님" >&2
  FAIL=1
fi
remove_fake_rclone

echo "==> local-only + rclone 성공 → exit 0, 타임스탬프만 기록되고 상태 메트릭은 안 남음"
install_fake_rclone success
rm -f "$TEXTFILE_DIR"/*
(
  export PATH="$FAKE_BIN:$PATH"
  export BACKUP_REMOTE_DEST="fake:bucket/path"
  backup_upload_remote "$FILENAME" "local-only" "kraft_backup" "$TEXTFILE_DIR"
)
assert_exit_code "local-only + rclone 성공 → exit 0" 0 "$?"
assert_file_exists "local-only + rclone 성공: remote 타임스탬프 메트릭 기록됨" "$TEXTFILE_DIR/kraft_backup_remote.prom"
assert_file_absent "local-only: remote 상태 메트릭은 시계열 자체가 없어야 함" "$TEXTFILE_DIR/kraft_backup_remote_status.prom"
remove_fake_rclone

if [[ $FAIL -ne 0 ]]; then
  echo "==> backup-common.sh 회귀 테스트 실패" >&2
  exit 1
fi
echo "==> backup-common.sh 회귀 테스트 통과"
