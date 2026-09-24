-- 실측 쿼리와 다른 인덱스가 이미 왼쪽 접두사로 덮는 중복 인덱스를 정리한다(개선 보고서 BE-11).

-- V23의 IX_COMMENTS_POST_PARENT_ID(post_id, parent_id, id)가 왼쪽 접두사로 post_id를
-- 이미 포함한다.
DROP INDEX IX_COMMENTS_POST ON comments;

-- V7의 IX_OUTBOX_MAILS_STATUS_ID(status, id)가 claim 쿼리(status=?, ORDER BY id)를 이미
-- 커버한다. next_attempt_at은 그 쿼리에서 필터로만 쓰이고 정렬 기준이 아니다.
DROP INDEX IX_OUTBOX_MAILS_STATUS_NEXT_ATTEMPT ON outbox_mails;

-- withdrawn_at을 거르는 유일한 쿼리(findGuestsMissingVerificationMail)는 role=GUEST를
-- 선두 조건으로 쓰는 IX_USERS_ROLE_CREATED_AT을 타므로, 이 단독 인덱스를 쓰는 쿼리가 없다.
DROP INDEX IX_USERS_WITHDRAWN_AT ON users;
