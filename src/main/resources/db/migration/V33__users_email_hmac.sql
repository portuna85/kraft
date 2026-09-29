-- 이메일 조회용 키 없는 SHA-512(email_hash)는 DB만 유출돼도 후보 주소 목록으로 사전 대입이 가능하다
-- (개선 계획 P0-3). 이메일 암호화 키와 분리한 pepper로 만든 HMAC-SHA256(소문자 16진수 64자)을
-- 새 컬럼에 둔다. 1단계(이 마이그레이션): 컬럼만 추가하고 앱이 이중 기록한다. 기존 행은 NULL이며
-- hmac-backfill 프로파일 도구가 채운다. email_hash 제거와 조회 전환은 백필 확인 뒤 V34에서 한다.
-- NULL은 유니크 제약에서 서로 충돌하지 않으므로 백필 전에도 제약을 걸어 둘 수 있다.
ALTER TABLE users ADD COLUMN email_hmac VARCHAR(64) NULL;
ALTER TABLE users ADD CONSTRAINT UK_USER_EMAIL_HMAC UNIQUE (email_hmac);
