-- 실측 쿼리 패턴에 맞춰 인덱스를 정리한다(개선 보고서 PERF-05).

-- V14의 IX_OUTBOX_MAILS_USER_KIND(user_id, kind, id)가 이미 왼쪽 접두사로 포함하므로
-- V7의 IX_OUTBOX_MAILS_USER(user_id, id)는 남는 쓰기 비용만 만들 뿐 남은 쿼리가 없다.
DROP INDEX IX_OUTBOX_MAILS_USER ON outbox_mails;

-- GuestVerificationSweeper(UserRepository.findGuestsMissingVerificationMail)가
-- role = GUEST AND created_at < :threshold로 훑는데, role만 걸리는 인덱스가 없어 전체
-- 스캔이었다.
CREATE INDEX IX_USERS_ROLE_CREATED_AT ON users (role, created_at);

-- CommentService.pageForView(CommentRepository.findPageByPostIdAsc)가
-- post_id = ? AND parent_id IS NULL을 id 순으로 훑는데, post_id·parent_id가 각각 단독
-- 인덱스(IX_COMMENTS_POST, IX_COMMENTS_PARENT)라 이 조합에는 못 쓰였다.
CREATE INDEX IX_COMMENTS_POST_PARENT_ID ON comments (post_id, parent_id, id);

-- V4의 IX_POST_IMAGES_STATUS_CREATED_AT(status, created_at)는 아래 3컬럼 인덱스가 왼쪽
-- 접두사로 그대로 포함한다. PostImageRepository의 id 커서 조회(findAllByStatusAndIdGreaterThan
-- OrderByIdAsc, findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc)와
-- claimExpiredOrphanForDeletion까지 한 인덱스로 커버하도록 합친다.
DROP INDEX IX_POST_IMAGES_STATUS_CREATED_AT ON post_images;
CREATE INDEX IX_POST_IMAGES_STATUS_CREATED_AT_ID ON post_images (status, created_at, id);
