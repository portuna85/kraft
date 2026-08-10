import { timingSafeEqual } from "node:crypto";

import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { ROUTES } from "@/shared/config/routes";

/**
 * 재검증 웹훅 검증 로직 — route.ts에서 분리한 이유는 테스트다. `revalidateTag`/
 * `revalidatePath`(next/cache)는 Next의 요청 컨텍스트 밖(vitest)에서 호출하면 던지므로,
 * 컨텍스트에 의존하지 않는 순수 로직만 여기 둔다.
 *
 * 경로·태그 화이트리스트는 백엔드의 `REVALIDATE_PATHS`·`tagsFor()`와 정확히 일치해야
 * 한다(R-6). `community:posts`는 백엔드의 어떤 웹훅도 보내지 않으므로 넣지 않는다 —
 * 넣어도 절대 오지 않는 값이라 검증이 아니라 장식이 된다.
 */
export const ALLOWED_PATHS = new Set<string>([
  ROUTES.home,
  ROUTES.frequency,
  ROUTES.stats,
  ROUTES.companion,
]);

export const ALLOWED_TAGS = new Set<string>([CACHE_TAGS.roundsLatest, CACHE_TAGS.statsAll]);

/**
 * 시크릿 비교는 타이밍 세이프해야 한다 — 문자열 길이·바이트 일치 여부가 응답 시간
 * 차이로 새어 나가면 시크릿을 바이트 단위로 추측당할 수 있다.
 */
export function matchesSecret(provided: string | null, expected: string | undefined): boolean {
  if (provided === null || expected === undefined || expected === "") return false;

  const providedBuf = Buffer.from(provided);
  const expectedBuf = Buffer.from(expected);
  if (providedBuf.length !== expectedBuf.length) return false;
  return timingSafeEqual(providedBuf, expectedBuf);
}

export function filterAllowedPaths(requested: unknown): string[] {
  const list = Array.isArray(requested) ? requested : [];
  return list.filter((path): path is string => typeof path === "string" && ALLOWED_PATHS.has(path));
}

export function filterAllowedTags(requested: unknown): string[] {
  const list = Array.isArray(requested) ? requested : [];
  return list.filter((tag): tag is string => typeof tag === "string" && ALLOWED_TAGS.has(tag));
}
