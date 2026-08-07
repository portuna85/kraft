/**
 * H-2: `/api/vitals`, `/api/csp-report`는 Caddy가 `/api/v1/*`만 백엔드로 프록시하기
 * 때문에 백엔드의 `PublicRateLimitFilter`가 절대 도달하지 못한다(도달 불가 확인됨).
 * 이 Next.js 컨테이너는 단일 장기 실행 프로세스(standalone 서버, 서버리스 아님)라
 * 인메모리 윈도우 카운터로 충분하다 — 백엔드의 `InMemoryRateLimitCounter`와 같은 전제.
 */
const WINDOW_MS = 60_000;
// 무제한으로 서로 다른 IP를 위장해 메모리를 소진하는 것을 막는 상한. 정밀한 LRU가
// 아니라 Map 삽입 순서(가장 오래된 항목)로 근사한다 — 공격 완화가 목적이지 정확한
// 사용량 계정이 목적이 아니다.
const MAX_TRACKED_KEYS = 10_000;

type Bucket = { count: number; windowStart: number };

const buckets = new Map<string, Bucket>();

export function isRateLimited(key: string, maxPerWindow: number): boolean {
  const now = Date.now();
  const bucket = buckets.get(key);
  if (!bucket || now - bucket.windowStart >= WINDOW_MS) {
    if (!buckets.has(key) && buckets.size >= MAX_TRACKED_KEYS) {
      const oldestKey = buckets.keys().next().value;
      if (oldestKey !== undefined) buckets.delete(oldestKey);
    }
    buckets.set(key, { count: 1, windowStart: now });
    return false;
  }
  bucket.count += 1;
  return bucket.count > maxPerWindow;
}

/** 테스트 전용 — 모듈 상태(윈도우 카운터)를 초기화해 테스트 케이스 간 카운트가 섞이지 않게 한다. */
export function resetRateLimitForTests(): void {
  buckets.clear();
}

/** Caddy의 reverse_proxy가 기본으로 채우는 헤더 — 프록시 뒤에서만 신뢰할 수 있다. */
export function clientIpFromHeaders(headers: Headers): string {
  const forwarded = headers.get("x-forwarded-for");
  if (forwarded) {
    const first = forwarded.split(",")[0]?.trim();
    if (first) return first;
  }
  return "unknown";
}
