-- 답글이 있는 최상위 댓글을 지우면 남의 답글까지 통째로 사라졌다(개선 보고서 A-BE-06).
-- 답글이 있으면 행을 지우지 않고 내용만 비운 채 이 시각을 남겨 "삭제된 댓글입니다"로
-- 표시한다(CommentService.delete). 답글이 없으면(또는 답글 자신이면) 지금처럼 행 자체를
-- 지운다 — 이 컬럼은 그 경우 계속 NULL이다.
ALTER TABLE comments ADD COLUMN deleted_at DATETIME(6) NULL;
