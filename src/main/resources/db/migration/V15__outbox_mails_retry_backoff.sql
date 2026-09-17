-- 메일 재시도에 지수 백오프와 선점 소유자 추적을 더한다.
--
-- 예전에는 실패한 메일이 균일한 주기(drain-interval-ms)마다 계속 재시도됐다. 그 사이 SMTP가
-- 여전히 응답하지 않으면 같은 실패가 매 주기 반복되며 배치 자리를 차지했다. next_attempt_at을
-- 두어 실패할수록 다음 시도까지 더 기다리게 한다(개선 보고서 "메일 임대·재시도·실행량 제한").
--
-- owner_token은 어느 워커 인스턴스가 이 메일을 선점했는지 운영 로그로 추적하기 위한 값이다.
-- 현재 배치 크기·발송 속도로는 SENDING 유지 시간이 stuck 판정 문턱(300초)보다 훨씬 짧아
-- 별도의 임대 갱신(heartbeat)까지는 두지 않는다.
ALTER TABLE outbox_mails
    ADD COLUMN next_attempt_at DATETIME(6),
    ADD COLUMN owner_token VARCHAR(36);

-- 선점 조회가 next_attempt_at도 함께 거른다.
CREATE INDEX IX_OUTBOX_MAILS_STATUS_NEXT_ATTEMPT ON outbox_mails (status, next_attempt_at);
