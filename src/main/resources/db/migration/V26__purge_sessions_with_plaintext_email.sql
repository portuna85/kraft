-- 기존 로그인 세션을 모두 비운다(평가 보고서 2026-09-25 F01).
--
-- 이 버전부터 세션에 직렬화되는 principal(KraftUserDetails)이 이메일 필드를 들고 있지 않다.
-- 이전 버전이 저장한 세션은 두 가지 이유로 남겨 둘 수 없다.
--   1) ATTRIBUTE_BYTES 안에 이메일 원문이 들어 있다 — users.email 컬럼 암호화를 우회한다.
--   2) 클래스 구성(serialVersionUID)이 달라져 새 JAR가 역직렬화하지 못한다.
-- 그래서 배포 시 모든 사용자가 한 번 다시 로그인해야 한다. JAR만 이전 버전으로 롤백해도 비운
-- 세션은 돌아오지 않는다(재로그인이면 충분하므로 롤백 호환에는 문제가 없다).
--
-- SPRING_SESSION_ATTRIBUTES는 FK ON DELETE CASCADE지만, 순서를 명시해 엔진 설정과 무관하게 한다.
DELETE FROM SPRING_SESSION_ATTRIBUTES;
DELETE FROM SPRING_SESSION;
