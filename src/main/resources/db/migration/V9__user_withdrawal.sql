-- 회원 탈퇴.
--
-- 행을 지우지 않고 개인정보만 지우는 이유는 참조 때문이다. posts.user_id·comments.user_id는
-- NOT NULL이라 회원을 지우려면 그 사람의 글과 댓글을 모두 지워야 하는데, 그러면 남의 댓글이
-- 달린 글이나 대화의 맥락까지 함께 사라진다. 글은 남기고 작성자만 익명으로 바꾼다.
--
-- 대신 이름·이메일·비밀번호는 탈퇴 시점에 쓸 수 없는 값으로 덮어쓴다. 특히 이메일을 바꾸므로
-- (email_hash도 함께 바뀐다) 같은 주소로 다시 가입할 수 있다 — 탈퇴가 그 주소를 영영 잠그면
-- 안 된다.
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL;

-- 탈퇴한 계정을 세는 운영 질의와, 로그인 조회에서 걸러내는 경로가 함께 쓴다.
CREATE INDEX IX_USERS_WITHDRAWN_AT ON users (withdrawn_at);
