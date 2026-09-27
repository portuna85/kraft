-- 신고 접수 시점의 대상 스냅샷(개선 보고서 A-SEC-07 = 전체 리뷰 2026-09-26 SEC-05)과, 본인
-- 삭제로 사라진 대상을 구분하는 상태(개선 보고서 A-BE-01)를 추가한다.
--
-- 신고된 작성자가 신고되자마자 스스로 글·댓글을 지우면, 지금까지는 관리자가 누구를
-- 정지해야 할지도, 무엇이 문제였는지도 알 수 없었다(대상이 사라지면 target_id로 더 이상
-- 아무것도 조회할 수 없다). 신고 접수 시점의 작성자·제목·본문 앞부분을 함께 저장해 두면
-- 대상이 사라져도 판단·정지에 필요한 최소한의 근거가 남는다.
--
-- status에 TARGET_DELETED를 추가한다 — "관리자가 판단해 지웠다"(RESOLVED)와 "작성자가
-- 스스로 지워 사라졌다"(TARGET_DELETED)를 기록상 나눈다. 값 목록은 Hibernate가 만드는 것과
-- 같은 알파벳 순이다(V10 주석 참고) — 새 값이 마침 알파벳 순으로도 끝에 온다.
ALTER TABLE reports MODIFY COLUMN status ENUM('PENDING','REJECTED','RESOLVED','TARGET_DELETED') NOT NULL;

-- FK로 걸어 둔다 — User 행은 탈퇴해도 지워지지 않고 자리표시로 남으므로(User.withdraw)
-- ON DELETE 규칙이 필요 없다.
ALTER TABLE reports ADD COLUMN target_author_id BIGINT NULL;
ALTER TABLE reports ADD COLUMN target_title_snapshot VARCHAR(255) NULL;
ALTER TABLE reports ADD COLUMN target_content_snapshot VARCHAR(500) NULL;
ALTER TABLE reports ADD CONSTRAINT FK_REPORTS_TARGET_AUTHOR FOREIGN KEY (target_author_id) REFERENCES users (id);

-- 처리 완료된 신고의 스냅샷을 일정 기간 뒤 지우는 정리 작업(개인정보 보관기간)이 훑는 범위다.
CREATE INDEX IX_REPORTS_STATUS_HANDLED_AT ON reports (status, handled_at);
