import { browserMutate, browserQuery, noContentSchema } from "@/shared/api/transport";

import {
  saveNumberResultSchema,
  savedNumberListSchema,
  savedNumberMatchListSchema,
  type SaveNumberResult,
  type SavedNumber,
  type SavedNumberMatch,
} from "./schema";

/**
 * 보관함 API
 *
 * **엔드포인트가 두 벌이다.** 익명은 `/api/v1/saved`(X-Device-Token 스코프), 로그인은
 * `/api/v1/community/me/saved-numbers`(세션 스코프)다. 하나로 합칠 수 없는 이유는
 * 백엔드 주석(B-P0-3)에 있다 — claim이 끝나면 저장 번호의 client_token_hash가 지워져서
 * 기기 토큰 경로로는 더 이상 찾지 못한다.
 *
 * 그래서 **로그인 상태에서는 절대 기기 경로를 쓰지 않는다.** 섞이면 로그인했는데
 * 보관함이 비어 보이거나, 저장한 것이 계정에 안 남는다.
 */

export function listDeviceSavedNumbers(): Promise<SavedNumber[]> {
  return browserQuery("/api/v1/saved", savedNumberListSchema, { deviceScoped: true });
}

export function listAccountSavedNumbers(): Promise<SavedNumber[]> {
  return browserQuery("/api/v1/community/me/saved-numbers", savedNumberListSchema);
}

export function matchDeviceSavedNumbers(round: string): Promise<SavedNumberMatch[]> {
  return browserQuery(
    `/api/v1/saved/matches?round=${encodeURIComponent(round)}`,
    savedNumberMatchListSchema,
    { deviceScoped: true },
  );
}

export function matchAccountSavedNumbers(round: string): Promise<SavedNumberMatch[]> {
  return browserQuery(
    `/api/v1/community/me/saved-numbers/matches?round=${encodeURIComponent(round)}`,
    savedNumberMatchListSchema,
  );
}

export function deleteDeviceSavedNumber(id: number): Promise<null> {
  return browserMutate(`/api/v1/saved/${id}`, noContentSchema, {
    method: "DELETE",
    deviceScoped: true,
  });
}

export function deleteAccountSavedNumber(id: number): Promise<null> {
  return browserMutate(`/api/v1/community/me/saved-numbers/${id}`, noContentSchema, {
    method: "DELETE",
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
): Promise<SaveNumberResult> {
  return browserMutate("/api/v1/community/me/saved-numbers", saveNumberResultSchema, {
    method: "POST",
    body: { numbers: [...numbers] },
    ...(signal === undefined ? {} : { signal }),
  });
}

export function saveNumbersToDevice(
  numbers: readonly number[],
  signal?: AbortSignal,
): Promise<SaveNumberResult> {
  return browserMutate("/api/v1/saved", saveNumberResultSchema, {
    method: "POST",
    deviceScoped: true,
    body: { numbers: [...numbers] },
    ...(signal === undefined ? {} : { signal }),
  });
}
