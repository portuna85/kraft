/**
 * 인메모리 요청 제한 상당(공개 수집 엔드포인트 보호)
 *
 * `/api/vitals`·`/api/csp-report`·`/api/client-error`는 Caddy의 `/api/v1/*` 프록시
 * 대상이 아니라 백엔드 레이트리밋 필터가 도달하지 못한다. 이 Next.js 컨테이너는 단일
 * 장기 실행 프로세스(standalone, 서버리스 아님)라 인메모리 윈도우 카운터로 충분하다.
 */
const WINDOW_MS = 60_000;

/**
 * 서로 다른 IP를 무제한으로 위장해 메모리를 소진하는 것을 막는 상한. 정밀한 LRU가
 * 아니라 Map 삽입 순서(가장 오래된 항목)로 근사한다 — 완화가 목적이지 정확한 계정이
 * 목적이 아니다.
 */
const MAX_TRACKED_KEYS = 10_000;

type Bucket = { count: number; windowStart: number };

const buckets = new Map<string, Bucket>();

export function isRateLimited(key: string, maxPerWindow: number): boolean {
  const now = Date.now();
  const bucket = buckets.get(key);

  if (bucket === undefined || now - bucket.windowStart >= WINDOW_MS) {
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

/** 테스트 전용 — 모듈 상태를 초기화해 테스트 케이스 간 카운트가 섞이지 않게 한다. */
export function resetRateLimitForTests(): void {
  buckets.clear();
}

/** Caddy의 reverse_proxy가 기본으로 채우는 헤더 — 프록시 뒤에서만 신뢰할 수 있다. */
export function clientIpFromHeaders(headers: Headers): string {
  const forwarded = headers.get("x-forwarded-for");
  if (forwarded !== null) {
    const first = forwarded.split(",")[0]?.trim();
    if (first !== undefined && first !== "") return first;
  }
  return "unknown";
}
