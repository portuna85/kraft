-- outbox_mails의 사용자별 인덱스에 kind를 포함한다.
--
-- findFirstByUserIdAndKindOrderByIdDesc(재발송 요청 제한 조회)는 user_id로 좁힌 뒤 kind로도
-- 걸러야 한다. V7의 IX_OUTBOX_MAILS_USER(user_id, id)는 V8에서 추가된 kind를 포함하지
-- 않아 이 조회가 kind 필터를 인덱스 밖에서 처리해야 했다(개선 보고서 "인덱스·암호화·주석의
-- 유지보수 경계"). 기존 인덱스는 다른 조회가 쓸 수 있으므로 남겨 두고 새로 추가한다.
CREATE INDEX IX_OUTBOX_MAILS_USER_KIND ON outbox_mails (user_id, kind, id);
