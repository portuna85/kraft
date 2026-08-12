#!/usr/bin/env bash
# db-backup.sh / caddy-data-backup.sh가 공유하는 메트릭 기록·원격 업로드 로직.
# 소싱해서 쓰는 라이브러리다 — 직접 실행하지 않는다. 호출하는 스크립트가 이미
# `set -euo pipefail`을 걸어둔다고 가정한다.
#
# H-06: 예전에는 BACKUP_REMOTE_DEST 미설정/rclone 부재/업로드 실패가 전부 경고
# 로그만 남기고 스크립트는 항상 exit 0이었다 — cron 실패 감시로는 오프사이트
# 백업 실패를 절대 잡을 수 없었고, Prometheus 스테일 알림도 메트릭이 한 번도
# 안 찍히면 조용했다(absent() 가드 없음). BACKUP_MODE=offsite-required에서는
# 원격 실패를 실제 job 실패(exit 1)로 승격한다.

# BACKUP_MODE=local-only(기본) | offsite-required. 잘못된 값이면 즉시 종료.
# 결과를 stdout으로 반환하므로 `mode="$(backup_validate_mode)"` 형태로 호출한다.
backup_validate_mode() {
  local mode="${BACKUP_MODE:-local-only}"
  case "$mode" in
    local-only|offsite-required) ;;
    *)
      echo "==> [FAIL] BACKUP_MODE 값이 올바르지 않습니다: '$mode' (local-only 또는 offsite-required만 허용)" >&2
      exit 2
      ;;
  esac
  echo "$mode"
}

# $1=textfile_dir $2=메트릭 파일명(확장자 제외) $3=본문(HELP/TYPE/값 라인)
# mktemp+mv로 원자적으로 쓴다. textfile_dir이 없으면 조용히 건너뛴다(node-exporter 미설정).
backup_write_metrics() {
  local textfile_dir="$1" metric_name="$2" content="$3"
  if [[ ! -d "$textfile_dir" ]]; then
    echo "==> [WARN] $textfile_dir 없음 — ${metric_name} 메트릭을 노출하지 않습니다(node-exporter textfile collector 미설정)." >&2
    return 0
  fi
  local tmp
  tmp="$(mktemp "$textfile_dir/.${metric_name}.XXXXXX")"
  printf '%s\n' "$content" > "$tmp"
  mv "$tmp" "$textfile_dir/${metric_name}.prom"
}

# 원격 업로드를 BACKUP_MODE 정책에 따라 수행한다.
# $1=filename $2=backup_mode $3=metric_label(예: kraft_backup, kraft_caddy_data_backup)
# $4=textfile_dir
# offsite-required인데 미설정/rclone 부재/업로드 실패면 exit 1로 종료한다.
backup_upload_remote() {
  local filename="$1" mode="$2" metric_label="$3" textfile_dir="$4"

  if [[ -z "${BACKUP_REMOTE_DEST:-}" ]]; then
    if [[ "$mode" == "offsite-required" ]]; then
      echo "==> [FAIL] BACKUP_MODE=offsite-required인데 BACKUP_REMOTE_DEST가 설정되지 않았습니다." >&2
      exit 1
    fi
    echo "==> [WARN] BACKUP_REMOTE_DEST 미설정 — 백업이 로컬(동일 호스트/볼륨)에만 저장됩니다. 단일 VM/디스크 장애 시 백업이 함께 유실될 수 있습니다." >&2
    return 0
  fi

  if ! command -v rclone >/dev/null 2>&1; then
    if [[ "$mode" == "offsite-required" ]]; then
      echo "==> [FAIL] BACKUP_MODE=offsite-required인데 rclone이 설치되어 있지 않습니다." >&2
      exit 1
    fi
    echo "==> [WARN] BACKUP_REMOTE_DEST가 설정됐지만 rclone이 설치되어 있지 않아 원격 사본을 건너뜁니다." >&2
    return 0
  fi

  if rclone copy "$filename" "$BACKUP_REMOTE_DEST" --quiet; then
    echo "==> 원격 사본 업로드 완료: $BACKUP_REMOTE_DEST"
    backup_write_metrics "$textfile_dir" "${metric_label}_remote" "$(backup_remote_success_metric_body "$metric_label")"
    # local-only에서는 원격이 옵션이라 remote_status 시계열 자체를 만들지 않는다(§ 아래
    # offsite-required 분기의 초기 0 기록과 대칭 — 시계열이 존재하면 그 자체로 "이 환경은
    # 오프사이트 백업을 추적한다"는 뜻이 되어, 미설정 개발 환경까지 알림 대상이 되면 안 된다).
    if [[ "$mode" == "offsite-required" ]]; then
      backup_write_metrics "$textfile_dir" "${metric_label}_remote_status" "$(backup_remote_status_metric_body "$metric_label" 1)"
    fi
    return 0
  fi

  echo "==> [FAIL] 원격 사본 업로드 실패: $BACKUP_REMOTE_DEST" >&2
  if [[ "$mode" == "offsite-required" ]]; then
    exit 1
  fi
  return 0
}

backup_remote_success_metric_body() {
  local metric_label="$1"
  cat <<EOF
# HELP ${metric_label}_last_remote_success_timestamp_seconds Unix time of the last successful offsite backup copy
# TYPE ${metric_label}_last_remote_success_timestamp_seconds gauge
${metric_label}_last_remote_success_timestamp_seconds $(date +%s)
EOF
}

# $1=metric_label $2=status(0|1)
backup_status_metric_body() {
  local metric_label="$1" status="$2"
  cat <<EOF
# HELP ${metric_label}_last_status 1 if the last backup attempt succeeded, 0 otherwise
# TYPE ${metric_label}_last_status gauge
${metric_label}_last_status ${status}
EOF
}

# $1=metric_label $2=backup_mode $3=status(0|1)
# local-only 모드에서는 원격 상태 시계열 자체를 남기지 않는다 — 개발 환경이 오프사이트
# 실패로 오인돼 알림이 울리면 안 된다.
backup_remote_status_metric_body() {
  local metric_label="$1" status="$2"
  cat <<EOF
# HELP ${metric_label}_remote_last_status 1 if the last offsite backup upload succeeded, 0 otherwise
# TYPE ${metric_label}_remote_last_status gauge
${metric_label}_remote_last_status ${status}
EOF
}
