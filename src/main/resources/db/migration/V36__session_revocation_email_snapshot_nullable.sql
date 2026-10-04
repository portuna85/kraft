-- BE-03: session_revocation_tasks.email_snapshot은 읽는 코드가 없다. 세션 principal이 회원 id가
-- 된 뒤(BE-04) 폐기는 user_id만으로 하고, 이 컬럼은 암호화한 이메일만 계속 쌓아 두고 있었다.
--
-- 컬럼을 바로 지우지 않고 NULL 허용으로만 바꾼다. 롤백용 이전 jar(kraft.jar.prev)는 아직 이
-- 컬럼에 값을 넣는 INSERT를 보내므로 컬럼이 없으면 롤백 직후 비밀번호 변경·탈퇴가 깨진다.
-- 새 jar는 이 컬럼에 쓰지 않는다. 이전 jar가 더는 필요 없어지면(다음 릴리스) 별도
-- 마이그레이션으로 DROP COLUMN 한다.
--
-- 의도된 MODIFY다 — 새 NULL 허용 컬럼으로 완화하는 변경이라 기존 데이터를 잃지 않는다.
ALTER TABLE session_revocation_tasks MODIFY COLUMN email_snapshot VARCHAR(500) NULL;
