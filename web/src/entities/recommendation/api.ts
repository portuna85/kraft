import { browserMutate } from "@/shared/api/transport";

import { recommendNumbersSchema, type RecommendNumbers, type Strategy } from "./schema";

/**
 * 추천 API 바인딩 — improvement_fe.md §5.2
 *
 * 전부 브라우저 요청이다. 추천 생성은 **명시적 버튼 클릭으로만** 일어나야 하므로
 * 서버 컴포넌트에서 부를 수 있는 함수를 아예 두지 않는다 — 두면 언젠가 페이지가
 * 마운트되는 것만으로 POST가 나간다(레거시 F-P0-6/7).
 */

export type RecommendInput = {
  strategy: Strategy;
  count: number;
  lockedNumbers: readonly number[];
  excludedNumbers: readonly number[];
};

export function recommendNumbers(
  input: RecommendInput,
  signal?: AbortSignal,
): Promise<RecommendNumbers> {
  return browserMutate("/api/v1/numbers/recommend", recommendNumbersSchema, {
    method: "POST",
    // 익명 사용자의 추천 이력을 이 기기에 귀속시킨다. 로그인 사용자는 세션으로 식별되므로
    // 백엔드가 계정 스코프를 우선한다.
    deviceScoped: true,
    body: {
      strategy: input.strategy,
      count: input.count,
      lockedNumbers: [...input.lockedNumbers],
      excludedNumbers: [...input.excludedNumbers],
    },
    ...(signal === undefined ? {} : { signal }),
  });
}
