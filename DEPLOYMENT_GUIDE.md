# Kraft 무중단 배포 가이드 (Ubuntu 24.04.3)

## 서버 정보
- **IP**: 192.168.0.9
- **OS**: Ubuntu 24.04.3 LTS
- **User**: steve
- **배포 방식**: Blue-Green Deployment (app_a ↔ app_b)

---

## ⚠️ 오류 해결: "no such file or directory"

### 원인 1: Docker가 Snap으로 설치되어 경로 문제 발생

**증상:**
```bash
steve@jsabe:/app/kraft$ docker compose -f docker-compose.prod.yml up -d mariadb redis minio
open /var/lib/snapd/void/docker-compose.prod.yml: no such file or directory
```

파일은 존재하지만 snap 버전의 Docker가 경로를 인식하지 못합니다.

**해결 (서버에서 실행):**

```bash
# 1. Snap Docker 제거 및 정식 Docker 설치
sudo snap remove docker --purge

# 2. 공식 Docker 저장소 추가
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 3. Docker 설치
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 4. 사용자를 docker 그룹에 추가
sudo usermod -aG docker steve

# 5. 그룹 적용 (로그아웃 없이)
newgrp docker

# 6. 확인
docker --version
docker compose version

# 7. 이제 정상 작동
cd /app/kraft
docker compose -f docker-compose.prod.yml up -d mariadb redis minio
```

**또는 자동 스크립트 사용 (로컬 PC에서):**
```bash
# 스크립트 업로드
scp fix-docker-snap.sh steve@192.168.0.9:/tmp/

# 서버에서 실행
ssh steve@192.168.0.9
sudo bash /tmp/fix-docker-snap.sh

# 로그아웃 후 재로그인 (또는 newgrp docker)
exit
ssh steve@192.168.0.9

# 다시 시도
cd /app/kraft
docker compose -f docker-compose.prod.yml up -d mariadb redis minio
```

---

### 원인 2: docker-compose.prod.yml 파일이 없는 경우

이 오류는 docker-compose.prod.yml 파일이 서버의 /app/kraft 디렉토리에 없어서 발생합니다.

### 빠른 해결 방법

**로컬 PC에서 실행:**
```bash
# 1. 파일 자동 업로드 스크립트 실행
bash quick-deploy.sh

# 2. 서버 접속하여 .env 편집
ssh steve@192.168.0.9
nano /app/kraft/.env

# 3. 비밀번호 변경 후 저장 (Ctrl+O, Enter, Ctrl+X)

# 4. 서비스 시작
cd /app/kraft
docker compose -f docker-compose.prod.yml up -d mariadb redis minio
```

**또는 수동 업로드:**
```bash
# docker-compose.prod.yml 업로드
scp docker-compose.prod.yml steve@192.168.0.9:/app/kraft/

# nginx 설정 업로드
scp docker/nginx/nginx.conf steve@192.168.0.9:/app/kraft/docker/nginx/

# .env 파일 생성 (서버에서)
ssh steve@192.168.0.9
cd /app/kraft
nano .env
# (위의 3번 환경변수 내용 붙여넣기)
```

---

## 1. 서버 초기 설정

### 1.1 서버에 접속
```bash
ssh steve@192.168.0.9
```

### 1.2 서버 설정 스크립트 실행
```bash
# 로컬에서 서버로 스크립트 전송
scp server-setup.sh steve@192.168.0.9:/tmp/

# 서버에서 실행
sudo bash /tmp/server-setup.sh
```

**설치 항목:**
- Docker & Docker Compose
- UFW 방화벽 (포트 22, 80, 443, 8080 허용)
- 필수 패키지 (curl, git 등)
- systemd 서비스 (자동 시작)
- 로그 로테이션 설정

---

## 2. 애플리케이션 디렉토리 구조

```
/app/kraft/
├── docker-compose.prod.yml
├── docker/
│   ├── nginx/
│   │   └── nginx.conf
│   └── mariadb/
│       └── init/
├── .env                    # 환경변수 (gitignore)
└── logs/                   # 애플리케이션 로그
```

### 2.1 디렉토리 생성 및 파일 배치
```bash
# 디렉토리는 server-setup.sh가 자동 생성
cd /app/kraft

# Git 저장소 클론 (또는 파일 직접 업로드)
git clone https://github.com/YOUR_USERNAME/kraft.git .
# 또는
scp -r docker-compose.prod.yml docker/ steve@192.168.0.9:/app/kraft/
```

---

## 3. 환경변수 설정 (.env)

```bash
# /app/kraft/.env 파일 생성
nano /app/kraft/.env
```

**필수 환경변수:**
```env
# GitHub Container Registry
GITHUB_REPOSITORY=your-username/kraft

# Database
MARIADB_ROOT_PASSWORD=your-strong-root-password
MARIADB_DATABASE=kraft_db
MARIADB_USER=kraft_user
MARIADB_PASSWORD=your-db-password

# Redis
REDIS_PORT=6379

# MinIO
MINIO_ROOT_USER=minio_admin
MINIO_ROOT_PASSWORD=your-minio-password

# OAuth (선택)
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-secret
NAVER_CLIENT_ID=your-naver-client-id
NAVER_CLIENT_SECRET=your-naver-secret
```

---

## 4. GitHub Container Registry 인증

### 4.1 Personal Access Token 생성
1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token
3. 권한: `read:packages` 선택
4. 토큰 복사

### 4.2 서버에서 로그인
```bash
echo "YOUR_GITHUB_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

---

## 5. 초기 배포 (수동)

### 5.1 인프라 서비스 시작
```bash
cd /app/kraft
docker compose -f docker-compose.prod.yml up -d mariadb redis minio
```

### 5.2 헬스 체크 대기
```bash
# MariaDB 헬스 확인
docker compose -f docker-compose.prod.yml ps mariadb

# 모든 서비스 상태 확인
docker compose -f docker-compose.prod.yml ps
```

### 5.3 애플리케이션 시작 (Blue-Green)
```bash
# 첫 배포: app_a 시작
docker compose -f docker-compose.prod.yml up -d app_a nginx

# 헬스 확인
curl http://localhost:8080/actuator/health
```

---

## 6. GitHub Actions Secrets 설정

GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret

| Secret 이름 | 값 | 설명 |
|------------|-----|-----|
| `SERVER_HOST` | `192.168.0.9` | 서버 IP |
| `SERVER_USER` | `steve` | SSH 사용자 |
| `SERVER_PORT` | `22` | SSH 포트 |
| `SSH_PRIVATE_KEY` | `-----BEGIN...` | steve 계정 SSH 개인키 |

### SSH 키 생성 (아직 없는 경우)
```bash
# 로컬 개발 머신에서
ssh-keygen -t ed25519 -C "kraft-deploy" -f ~/.ssh/kraft_deploy

# 공개키를 서버에 추가
ssh-copy-id -i ~/.ssh/kraft_deploy.pub steve@192.168.0.9

# 개인키 내용을 GitHub Secret에 추가
cat ~/.ssh/kraft_deploy
```

---

## 7. 무중단 배포 흐름

### GitHub Actions 자동 배포 프로세스
1. **코드 푸시** → `main` 브랜치
2. **CI**: 빌드 & 테스트
3. **이미지 빌드**: Docker 이미지 → ghcr.io
4. **배포**:
   ```
   현재 활성: app_a
   
   ① 새 이미지로 app_b 시작
   ② app_b 헬스체크 (60초 대기)
   ③ app_b healthy → nginx 자동 부하분산 (app_a + app_b)
   ④ app_a 중지 & 제거
   ⑤ 최종 검증: curl /healthz
   
   결과: app_b만 활성 (다음 배포 시 반대)
   ```

### 배포 중 서비스 가용성
- **다운타임**: 0초
- **Nginx**가 두 컨테이너 간 자동 전환
- **헬스체크 실패 시**: 기존 버전 유지 (롤백)

---

## 8. 운영 명령어

### 상태 확인
```bash
# 전체 서비스 상태
docker compose -f docker-compose.prod.yml ps

# 로그 확인
docker compose -f docker-compose.prod.yml logs -f app_a
docker compose -f docker-compose.prod.yml logs -f nginx

# 헬스 체크
curl http://localhost:8080/actuator/health
```

### 수동 롤백
```bash
# 현재 활성 컨테이너 확인
docker ps --filter name=kraft-app

# 이전 버전으로 롤백 (예: app_a가 문제)
docker compose -f docker-compose.prod.yml stop app_a
docker compose -f docker-compose.prod.yml up -d app_b
```

### 전체 재시작
```bash
docker compose -f docker-compose.prod.yml restart
```

### 리소스 정리
```bash
# 사용하지 않는 이미지/볼륨 정리
docker system prune -a --volumes -f
```

---

## 9. 모니터링

### 컨테이너 리소스 확인
```bash
docker stats kraft-app-a kraft-app-b kraft-nginx
```

### 디스크 사용량
```bash
df -h /app/kraft
du -sh /app/kraft/logs
```

### 로그 확인
```bash
# Nginx 액세스 로그
docker logs kraft-nginx --tail 100

# 애플리케이션 로그 (Spring Boot)
docker exec kraft-app-a tail -f /app/logs/kraft.log
```

---

## 10. 트러블슈팅

### 배포 실패 시
```bash
# 1. 헬스체크 상태 확인
docker inspect kraft-app-a | grep -A 10 Health

# 2. 로그 확인
docker logs kraft-app-a --tail 200

# 3. 데이터베이스 연결 확인
docker exec kraft-app-a curl -f http://localhost:8080/actuator/health/db
```

### 포트 충돌
```bash
# 8080 사용 중인 프로세스 확인
sudo lsof -i :8080
sudo netstat -tlnp | grep 8080
```

### 메모리 부족
```bash
# 현재 메모리 사용량
free -h

# Java 힙 크기 조정 (docker-compose.prod.yml)
JAVA_OPTS=-Xmx1g -Xms512m  # 기존 2g → 1g
```

---

## 11. 보안 권장사항

### SSL/TLS 설정 (Nginx + Let's Encrypt)
```bash
# Certbot 설치
sudo apt install certbot python3-certbot-nginx

# SSL 인증서 발급
sudo certbot --nginx -d yourdomain.com
```

### 방화벽 강화
```bash
# 특정 IP만 SSH 허용
sudo ufw delete allow 22
sudo ufw allow from YOUR_OFFICE_IP to any port 22
```

---

## 12. 성능 최적화

### Docker 리소스 제한 (docker-compose.prod.yml)
```yaml
app_a:
  deploy:
    resources:
      limits:
        cpus: '2.0'
        memory: 3G
      reservations:
        cpus: '1.0'
        memory: 2G
```

### Nginx 캐싱
```nginx
# nginx.conf에 추가
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=app_cache:10m max_size=1g inactive=60m;
proxy_cache app_cache;
proxy_cache_valid 200 5m;
```

---

## 요약

✅ **설정 완료 체크리스트**
- [ ] 서버 초기 설정 (server-setup.sh)
- [ ] /app/kraft 디렉토리 및 파일 배치
- [ ] .env 환경변수 설정
- [ ] GitHub Container Registry 로그인
- [ ] GitHub Actions Secrets 등록
- [ ] 초기 수동 배포 테스트
- [ ] GitHub Push로 자동 배포 검증

🚀 **배포 테스트**
```bash
# 1. 코드 수정 & 커밋
git commit -am "test: zero-downtime deployment"
git push origin main

# 2. GitHub Actions 확인
# https://github.com/YOUR_USERNAME/kraft/actions

# 3. 서버에서 확인
ssh steve@192.168.0.9
docker ps
curl http://192.168.0.9:8080/actuator/health
```
