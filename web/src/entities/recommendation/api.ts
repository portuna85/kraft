import { browserMutate } from "@/shared/api/transport";

import {
  recommendNumbersSchema,
  savedNumberSchema,
  type RecommendNumbers,
  type SavedNumber,
  type Strategy,
} from "./schema";

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

/**
 * 조합 저장. 로그인 여부에 따라 **엔드포인트가 다르다**(§3.3).
 *
 * 로그인 상태라면 claim이 끝나기를 기다리지 않고 곧바로 계정 소유로 저장한다
 * (레거시 C-2). 기다리면 claim이 실패한 사용자의 저장이 익명 스코프로 떨어져,
 * 로그인해 있는데도 보관함에서 안 보이는 상태가 된다.
 */
export function saveNumbersToAccount(
  numbers: readonly number[],
  signal?: AbortSignal,
): Promise<SavedNumber> {
  return browserMutate("/api/v1/community/me/saved-numbers", savedNumberSchema, {
    method: "POST",
    body: { numbers: [...numbers] },
    ...(signal === undefined ? {} : { signal }),
  });
}

export function saveNumbersToDevice(
  numbers: readonly number[],
  signal?: AbortSignal,
): Promise<SavedNumber> {
  return browserMutate("/api/v1/saved", savedNumberSchema, {
    method: "POST",
    deviceScoped: true,
    body: { numbers: [...numbers] },
    ...(signal === undefined ? {} : { signal }),
  });
}
