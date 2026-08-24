import { describe, expect, it } from "vitest";

import { CACHE_TAGS } from "./cache-tags";

/**
 * QA-FE-01(docs/improvement.md): CACHE_TAGS 자체를 백엔드 소스와 대조하는 테스트가
 * 없었다 — `app/api/revalidate/guard.test.ts`는 `filterAllowedTags`라는 별개 함수의
 * 동작만 검사한다.
 *
 * 아래 리터럴은 백엔드
 * `src/main/java/com/kraft/winningnumber/RevalidateWebhookListener.java`의
 * `tagsFor()`(2026-08-24 기준 71-73줄)가 실제로 낼 수 있는 값 전체다:
 *
 *   static List<String> tagsFor() {
 *       return List.of("rounds:latest", "stats:all");
 *   }
 *
 * Java 소스를 파싱하는 인프라는 이 저장소에 없다(가장 가까운 선례인
 * AlertRunbookContractTest도 텍스트 포함 여부만 본다) — 그래서 guard.ts와 같은 방식으로
 * 리터럴을 하드코딩하고, 백엔드 파일을 손으로 바꿀 때 이 테스트도 함께 봐야 한다는
 * 것을 주석으로 강제한다.
 */
const BACKEND_EMITTED_TAGS = ["rounds:latest", "stats:all"] as const;

describe("CACHE_TAGS — 백엔드 RevalidateWebhookListener.tagsFor()와 일치", () => {
  it("백엔드가 실제로 보내는 두 태그를 포함한다", () => {
    expect(CACHE_TAGS.roundsLatest).toBe(BACKEND_EMITTED_TAGS[0]);
    expect(CACHE_TAGS.statsAll).toBe(BACKEND_EMITTED_TAGS[1]);
  });

  it("communityPosts는 백엔드가 보내는 집합 밖이다(app/api/revalidate/guard.ts와 같은 전제)", () => {
    // 백엔드 웹훅이 절대 보내지 않는 태그다 — community-post/api.ts가 자기 ISR
    // 재검증에만 쓰고, /api/revalidate 화이트리스트에는 의도적으로 없다(guard.test.ts).
    expect(BACKEND_EMITTED_TAGS as readonly string[]).not.toContain(CACHE_TAGS.communityPosts);
  });
});
