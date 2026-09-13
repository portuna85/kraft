-- 닉네임(users.name) 중복 금지를 DB에서 강제한다.
--
-- UserService.signUp()의 existsByName() 사전 검사는 검사와 INSERT 사이에 같은 이름이
-- 들어오는 것을 막지 못한다. 이메일 해시에는 유니크 제약이 있었지만 이름에는 없어서,
-- 서로 다른 이메일이 같은 이름을 갖는 상태가 실제로 저장됐다(개선 보고서 F09).
--
-- 주의: 기존 DB에 중복 이름이 있으면 이 마이그레이션이 실패한다. 적용 전에 확인하고
-- 정리한다.
--   SELECT name, COUNT(*) FROM users GROUP BY name HAVING COUNT(*) > 1;

ALTER TABLE users
    ADD CONSTRAINT UK_USER_NAME UNIQUE (name);
