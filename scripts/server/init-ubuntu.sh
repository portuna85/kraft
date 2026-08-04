#!/usr/bin/env bash
# Ubuntu 24.04 LTS — KRAFT Lotto 서버 초기화 스크립트
# 사용법: sudo bash init-ubuntu.sh [deploy-username] [github-repo-url]
#
# 기본값:
#   deploy-username : deploy
#   github-repo-url : https://github.com/portuna85/Kraft.git
#
# 실행 후 해야 할 일 (스크립트 종료 시 안내):
#   1. GitHub → Settings → Secrets 등록 (DEPLOY_HOST / USER / SSH_KEY 등)
#   2. 서버에서 .env.prod 작성: cp /srv/kraft/.env.prod.example /srv/kraft/.env.prod && vi /srv/kraft/.env.prod
#   3. 도메인 A 레코드 → 이 서버 IP 연결
#   4. GitHub Actions CD 워크플로우 활성화

set -euo pipefail

DEPLOY_USER="${1:-deploy}"
REPO_URL="${2:-https://github.com/portuna85/Kraft.git}"
REPO_DIR="/srv/kraft"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[INIT]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
die()  { echo -e "${RED}[FAIL]${NC} $*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "root 권한으로 실행하세요: sudo bash $0"
[[ "$(lsb_release -rs 2>/dev/null || true)" == "24.04" ]] || warn "Ubuntu 24.04 권장 (현재: $(lsb_release -rs 2>/dev/null || echo unknown))"

# ── 1. 시스템 업데이트 ──────────────────────────────────────────────────────────
log "1/9 시스템 업데이트"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq \
    curl git ca-certificates gnupg lsb-release \
    ufw fail2ban htop unzip

# ── 2. Docker Engine 설치 ───────────────────────────────────────────────────────
log "2/9 Docker Engine 설치"
if ! command -v docker &>/dev/null; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list

    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    log "  Docker $(docker --version) 설치 완료"
else
    log "  Docker 이미 설치됨: $(docker --version)"
fi

# ── 3. deploy 유저 생성 ─────────────────────────────────────────────────────────
log "3/9 deploy 유저 설정 (${DEPLOY_USER})"
if ! id "$DEPLOY_USER" &>/dev/null; then
    useradd --create-home --shell /bin/bash "$DEPLOY_USER"
    log "  유저 생성: $DEPLOY_USER"
else
    log "  유저 이미 존재: $DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"
log "  docker 그룹 추가 완료"

# SSH 디렉토리 준비 (GitHub Actions가 SSH 키를 주입)
SSH_DIR="/home/${DEPLOY_USER}/.ssh"
mkdir -p "$SSH_DIR"
touch "${SSH_DIR}/authorized_keys"
chmod 700 "$SSH_DIR"
chmod 600 "${SSH_DIR}/authorized_keys"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "$SSH_DIR"

# ── 4. 리포지토리 클론 ─────────────────────────────────────────────────────────
log "4/9 리포지토리 클론 → ${REPO_DIR}"
mkdir -p "$REPO_DIR"
if [[ ! -d "${REPO_DIR}/.git" ]]; then
    git clone "$REPO_URL" "$REPO_DIR"
    log "  클론 완료: $REPO_URL"
else
    log "  이미 클론됨, 최신화..."
    git -C "$REPO_DIR" fetch --depth=1 origin main
    git -C "$REPO_DIR" reset --hard origin/main
fi
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "$REPO_DIR"

# ── 5. UFW 방화벽 ─────────────────────────────────────────────────────────────
log "5/9 UFW 방화벽 설정"
# L-7: 다른 단계는 전부 가드가 있어 재실행이 안전한데(멱등) 이 reset만 무조건 실행돼
# 이후 수동으로 추가한 룰을 전부 지웠다 — 최초 설정(inactive)일 때만 reset+기본 정책을
# 적용하고, 이미 활성화돼 있으면 기준 포트만 다시 보장한다(중복 allow는 무해).
if ! ufw status | grep -q "Status: active"; then
    ufw --force reset
    ufw default deny incoming
    ufw default allow outgoing
fi
ufw allow 22/tcp    comment "SSH"
ufw allow 80/tcp    comment "HTTP"
ufw allow 443/tcp   comment "HTTPS"
ufw --force enable
log "  포트 22/80/443 허용"

# ── 6. fail2ban ───────────────────────────────────────────────────────────────
log "6/9 fail2ban 활성화"
systemctl enable --now fail2ban
cat > /etc/fail2ban/jail.local <<'EOF'
[DEFAULT]
bantime  = 1h
findtime = 10m
maxretry = 5

[sshd]
enabled = true
port    = ssh
EOF
systemctl restart fail2ban
log "  fail2ban 시작 (SSH brute-force 차단)"

# ── 7. 스왑 설정 (RAM 2GB 이하 서버용) ─────────────────────────────────────────
log "7/9 스왑 확인"
if [[ $(swapon --show | wc -l) -eq 0 ]]; then
    TOTAL_MEM_GB=$(awk '/MemTotal/{printf "%d", $2/1024/1024}' /proc/meminfo)
    if [[ $TOTAL_MEM_GB -le 2 ]]; then
        fallocate -l 2G /swapfile
        chmod 600 /swapfile
        mkswap /swapfile
        swapon /swapfile
        echo '/swapfile none swap sw 0 0' >> /etc/fstab
        sysctl vm.swappiness=10
        echo 'vm.swappiness=10' >> /etc/sysctl.conf
        log "  2GB 스왑 추가 (RAM ${TOTAL_MEM_GB}GB 감지)"
    else
        log "  RAM ${TOTAL_MEM_GB}GB — 스왑 생략"
    fi
else
    log "  스왑 이미 존재: $(swapon --show --noheadings)"
fi

# ── 8. 로그 디렉토리 ──────────────────────────────────────────────────────────
log "8/9 로그 디렉토리 준비"
mkdir -p "${REPO_DIR}/logs"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${REPO_DIR}/logs"

# ── 9. DB 백업/복구 드릴 cron 등록 ───────────────────────────────────────────────
# 원격(오프서버) 백업 사본을 쓰려면 이 서버에 rclone을 설치하고 /etc/kraft/backup.env에
# BACKUP_REMOTE_DEST(예: BACKUP_REMOTE_DEST=s3:my-bucket/kraft-backups/)를 설정할 것.
# 미설정 시 기존처럼 로컬 ${BACKUP_DIR}에만 보관한다.
log "9/9 DB 백업 cron 등록"
CRON_MARKER="# KRAFT DB backup (managed by scripts/server/init-ubuntu.sh)"
if ! crontab -u "$DEPLOY_USER" -l 2>/dev/null | grep -qF "$CRON_MARKER"; then
    (crontab -u "$DEPLOY_USER" -l 2>/dev/null || true; cat <<EOF
$CRON_MARKER
0 3 * * * cd ${REPO_DIR} && bash scripts/db-backup.sh >> ${REPO_DIR}/logs/backup.log 2>&1
15 3 * * * cd ${REPO_DIR} && bash scripts/caddy-data-backup.sh >> ${REPO_DIR}/logs/caddy-data-backup.log 2>&1
30 3 * * 0 cd ${REPO_DIR} && bash scripts/db-restore-drill.sh >> ${REPO_DIR}/logs/restore-drill.log 2>&1
EOF
    ) | crontab -u "$DEPLOY_USER" -
    log "  cron 등록 완료: 매일 03:00 DB 백업, 03:15 caddy-data 백업, 매주 일요일 03:30 복구 드릴"
else
    log "  cron 이미 등록됨, 건너뜀"
fi

# ── 완료 안내 ─────────────────────────────────────────────────────────────────
SERVER_IP=$(hostname -I | awk '{print $1}')
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  KRAFT 서버 초기화 완료${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  서버 IP   : $SERVER_IP"
echo "  Deploy 유저: $DEPLOY_USER"
echo "  리포지토리 : $REPO_DIR"
echo ""
echo -e "${YELLOW}다음 단계 (수동)${NC}"
echo ""
echo "  [1] GitHub → 리포지토리 → Settings → Secrets → Actions 에 아래 등록:"
echo "      DEPLOY_HOST       = $SERVER_IP"
echo "      DEPLOY_USER       = $DEPLOY_USER"
echo "      DEPLOY_PORT       = 22"
echo "      DEPLOY_SSH_KEY    = (deploy 유저 SSH 개인키 — 아래 명령으로 생성)"
echo ""
echo "      # 로컬 PC에서 실행:"
echo "      ssh-keygen -t ed25519 -C 'github-actions-deploy' -f ~/.ssh/kraft_deploy"
echo "      # 공개키를 서버에 추가:"
echo "      cat ~/.ssh/kraft_deploy.pub >> /home/${DEPLOY_USER}/.ssh/authorized_keys"
echo "      # 개인키 내용을 DEPLOY_SSH_KEY Secret에 붙여넣기:"
echo "      cat ~/.ssh/kraft_deploy"
echo ""
echo "  [2] 나머지 GitHub Secrets 등록 (scripts/server/github-secrets.md 참고)"
echo ""
echo "  [3] 프로덕션 환경 파일 작성:"
echo "      cp ${REPO_DIR}/.env.prod.example ${REPO_DIR}/.env.prod"
echo "      nano ${REPO_DIR}/.env.prod"
echo ""
echo "  [4] 도메인 A 레코드 → $SERVER_IP 연결 후 CD 워크플로우 활성화"
echo ""
