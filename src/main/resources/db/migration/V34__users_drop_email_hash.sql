-- 이메일 HMAC 전환의 마무리(개선 계획 P0-3). V33이 추가한 email_hmac을 hmac-backfill 도구로 전부 채운 뒤
-- (NULL 0건 확인) 조회를 email_hmac으로 옮겼고, 키 없는 SHA-512 컬럼(email_hash)은 더 쓰지 않는다.
-- 한 문장으로 처리해 NULL이 남아 있으면(=백필이 덜 끝났으면) 아무것도 바꾸지 않고 실패한다.
ALTER TABLE users
    DROP INDEX UK_USER_EMAIL_HASH,
    DROP COLUMN email_hash,
    MODIFY COLUMN email_hmac VARCHAR(64) NOT NULL;
