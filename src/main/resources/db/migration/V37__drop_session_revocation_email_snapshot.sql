-- BE-03 수축 단계. V36이 email_snapshot을 NULL 허용으로 완화했고(확장), 코드는 그 뒤로 이 컬럼에
-- 쓰지도 읽지도 않는다(배포). 서버에 남은 롤백용 jar(releases/·kraft.jar.prev)도 모두 그 이후
-- 빌드라 이 컬럼이 없어도 기동한다. 세션 폐기는 user_id만으로 하므로 암호화한 이메일 스냅샷은
-- 개인정보만 쌓아 두던 데이터였다.
--
-- 의도된 DROP COLUMN이다(db/migration/README.md의 "수축"). 이 배포 이전의 jar로는 롤백할 수 없다.
ALTER TABLE session_revocation_tasks DROP COLUMN email_snapshot;
