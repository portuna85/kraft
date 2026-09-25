# 백업 검증·복구 절차

`deploy/backup.sh`가 만든 전체 백업을 **격리된 환경**에 되살려 확인하는 절차다. 평가 보고서 2026-09-25 F05에 따라 작성했다. 운영 DB와 운영 업로드 디렉터리를 복구 대상으로 삼지 않는다. 운영에 되돌려야 하는 상황이면 이 절차로 격리 환경에서 먼저 확인하고, 그다음 별도로 판단한다.

## 백업 한 벌의 구성

`/opt/kraft/app/backups/full/<YYYYmmdd-HHMMSS>/` 아래에 다음 파일이 생긴다.

| 파일 | 내용 |
|---|---|
| `db.sql.gz` | `mariadb-dump --single-transaction` 결과. 덤프 시작 시점의 일관된 스냅샷이다 |
| `uploads.tar.gz` | `uploads/images` 디렉터리. DB 덤프가 **끝난 뒤** 따로 묶는다 |
| `KEY-NOTICE.txt` | 백업 시점과 `EMAIL_ENCRYPTION_KEY` 식별자. 키 자체는 들어 있지 않다 |
| `manifest.txt` | 각 파일의 크기, SHA-256, 키 식별자(`email_key_fingerprint`) |

세 가지를 **같은 시점**으로 함께 써야 한다: DB, 이미지, 키. 하나라도 빠지거나 시점이 다르면 복구가 실패하거나 일부가 깨진다. 어떤 식으로 깨지는지는 `BackupRestoreRehearsalTest`에 정리돼 있다.

- 다른 키를 쓰면 회원 이메일을 복호화하지 못한다(`AEADBadTagException`).
- 이미지를 빼면 글은 열리지만 이미지가 깨진다.

### 알려진 한계

- **DB와 이미지는 완전히 같은 순간이 아니다.** tar가 도는 몇 초 사이에 업로드·삭제된 이미지는 두 쪽이 어긋날 수 있다. 복구 후에는 `post_images`의 행과 실제 파일을 대조한다(아래 4단계).
- **스키마와 JAR는 짝이 맞아야 한다.** 덤프는 백업 시점의 Flyway 버전(`flyway_schema_history`)을 담고 있다. 그보다 오래된 JAR는 `ddl-auto: validate`나 새 컬럼 때문에 실패할 수 있다. 백업 시점에 운영하던 JAR 또는 그 이후 JAR로 띄운다. JAR만 롤백해도 DB 마이그레이션은 되돌아가지 않는다(`deploy-apply.sh`의 롤백 로그 참고).
- **V26 이후 백업에는 로그인 세션이 비어 있을 수 있다.** 이메일 평문 제거 때문이다. 복구 후 재로그인이 필요한 것은 정상이다.

## 1. 백업 파일 검증 (읽기 전용)

```bash
/opt/kraft/verify-backup.sh                                  # 가장 최근 백업
/opt/kraft/verify-backup.sh /opt/kraft/app/backups/full/20260925-030000
```

크기·SHA-256 불일치, gzip이나 tar 손상, 키 식별자 누락이 있으면 0이 아닌 코드로 끝난다. 정기 점검(예: 백업 직후 cron)에서도 같은 명령을 쓴다.

## 2. 키 확인

`KEY-NOTICE.txt`나 `manifest.txt`의 `email_key_fingerprint`와 보관소에 있는 후보 키의 식별자를 대조한다.

```bash
# 키 원문이 셸 기록에 남지 않도록 read -s로 받는다.
read -rs KEY && printf '%s' "$KEY" | sha256sum | cut -c1-16; unset KEY
```

식별자가 일치하는 키만 쓴다. 키 원문을 백업 디렉터리나 로그에 복사하지 않는다.

## 3. 격리 환경에 복원

운영과 다른 DB 인스턴스, 다른 업로드 디렉터리, 다른 포트를 쓴다. 로컬 PC도 괜찮다.

```bash
B=/path/to/backups/full/<STAMP>
WORK=$(mktemp -d)

# 1) 빈 MariaDB (운영 compose 볼륨과 무관한 일회용 컨테이너)
docker run -d --name kraft-restore -e MARIADB_ROOT_PASSWORD=restore -e MARIADB_DATABASE=kraft \
  -p 127.0.0.1:33306:3306 mariadb:11.7.2
# 2) DB 복원
gunzip -c "$B/db.sql.gz" | docker exec -i -e MYSQL_PWD=restore kraft-restore mariadb -uroot kraft
# 3) 이미지 복원
tar -xzf "$B/uploads.tar.gz" -C "$WORK"     # → $WORK/images
# 4) 백업 시점의 JAR로 기동. 2단계에서 식별자를 대조한 키를 쓴다
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:mariadb://127.0.0.1:33306/kraft DB_USERNAME=root DB_PASSWORD=restore \
EMAIL_ENCRYPTION_KEY="<대조한 키>" APP_BASE_URL=http://localhost:18080 \
java -jar kraft.jar --server.port=18080 --app.upload.dir="$WORK/images" \
  --app.mail.enabled=false --app.recommend.auto-fetch.enabled=false
```

- 메일 발송은 반드시 끈다(`app.mail.enabled=false`). 복원한 outbox에 남은 메일이 실제 사용자에게 나가면 안 된다. 외부 수집(`app.recommend.auto-fetch.enabled=false`)도 끈다.
- 운영 `.env`에 있는 그 밖의 필수 설정은 격리 환경용 값으로 채운다. 설정 키는 `application-prod.yml`을 기준으로 한다.

## 4. 복구 후 확인

1. `curl -fsS http://127.0.0.1:18080/readyz`가 200인지 본다. DB 연결을 확인하는 단계다.
2. 테스트용 계정으로 로그인한다. 이메일을 조회할 수 있어야 키가 맞는 것이다. 운영 사용자 계정으로 로그인하지 않는다.
3. 최근 게시글 몇 개와 그 첨부 이미지를 연다.
4. DB의 이미지 행과 실제 파일을 대조한다.
   ```sql
   SELECT url FROM post_images WHERE status <> 'PENDING_DELETE';
   ```
   결과를 `$WORK/images` 목록과 비교해, 빠진 파일이 몇 개인지 기록한다.
5. 걸린 시간(검증 시작부터 4단계까지)을 기록한다. 이 기록이 RTO를 판단하는 근거가 된다.

확인이 끝나면 `docker rm -f kraft-restore`로 컨테이너를 지우고 `$WORK`를 삭제한다.

## 운영에서 확정해야 할 항목 (저장소만으로는 알 수 없음)

이 저장소는 백업을 **같은 서버의** `/opt/kraft/app/backups`에 만든다. 아래 항목은 운영에서 실제 사실로 채워야 한다.

- [ ] `backup.sh` cron 등록 여부와 실행 주기, 최근 성공 이력(`/opt/kraft/backup.log`)
- [ ] 다른 장애 영역(다른 서버, 오브젝트 스토리지 등)에 사본이 있는지, 복제 주기와 접근 방법
- [ ] `EMAIL_ENCRYPTION_KEY` 보관 위치와 키 회전 이력. 식별자로 대조할 수 있어야 한다
- [ ] 백업·검증 실패 알림을 누가 어떤 경로로 받는지
- [ ] 목표 RPO(허용 데이터 손실)와 RTO(복구 시간). 위 4-5단계의 실측으로 검증한다
- [ ] 격리 복구 리허설 주기와 마지막 실시 일자
