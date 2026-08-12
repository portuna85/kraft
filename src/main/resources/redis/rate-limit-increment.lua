-- KEYS[1] = 카운터 키, ARGV[1] = 윈도 초
-- INCR와 만료 설정을 하나의 원자 실행으로 묶는다. PTTL이 음수면(-1 만료 없음, -2 키 없음)
-- 만료가 붙지 않은 상태이므로 복구한다 — 첫 호출 직후 프로세스가 죽어 EXPIRE를 놓친 키가
-- 영구히 남아 해당 클라이언트를 무기한 차단하는 것을 막는다.
local count = redis.call('INCR', KEYS[1])
if count == 1 or redis.call('PTTL', KEYS[1]) < 0 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
