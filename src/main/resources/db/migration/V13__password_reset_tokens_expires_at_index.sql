-- password_reset_tokens에 만료 시각 인덱스를 추가한다.
--
-- email_verification_tokens는 만료 정리 조회를 위한 IX_EVT_EXPIRES_AT(V6)가 있는데
-- password_reset_tokens에는 같은 인덱스가 빠져 있었다. deleteByExpiresAtBefore를
-- 건별 삭제에서 한 문장 DELETE로 바꾸면서(개선 보고서 "파생 delete 메서드의 엔티티별
-- 삭제") 이 조건으로 정기적으로 스캔·삭제하게 되므로 인덱스가 필요하다.
CREATE INDEX IX_PASSWORD_RESET_TOKENS_EXPIRES_AT ON password_reset_tokens (expires_at);
