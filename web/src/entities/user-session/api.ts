import { CSRF_HEADER_NAME, readCsrfToken } from "@/shared/api/csrf";
import { browserMutate, browserQuery } from "@/shared/api/transport";

import { claimDeviceSchema, communitySessionSchema, type CommunitySession } from "./schema";

/**
 * 세션 API 바인딩(entity가 자기 엔드포인트를 소유한다)
 *
 * 세션은 사용자별 응답이라 절대 공유 캐시에 담기지 않는다. browserQuery가 항상
 * no-store로 보내므로 여기서 따로 신경 쓸 것은 없다.
 */

export const SESSION_RESOURCE_KEY = "session";

export function fetchSession(signal: AbortSignal): Promise<CommunitySession> {
  return browserQuery("/api/v1/community/session", communitySessionSchema, { signal });
}

/**
 * 로그인 직후 익명 디바이스 기록을 계정으로 옮긴다.
 *
 * 로그인 사용자 요청 중 **유일하게 디바이스 토큰을 함께 보내는** 요청이다 — 토큰이
 * 없으면 백엔드가 무엇을 옮길지 알 수 없다. 호출부는 토큰이 실제로 존재할 때만 이
 * 함수를 부른다(없으면 옮길 익명 기록도 없다).
 *
 * 토큰 회전은 여기서 하지 않는다. 이 함수 안에서 하면 실패 경로에서도 회전하게 되고,
 * 그 순간 이 브라우저의 익명 기록이 영구 고립된다(불변식 I-2, 레거시 F-P0-10).
 */
export function claimDevice(signal?: AbortSignal): Promise<unknown> {
  return browserMutate("/api/v1/community/session/claim-device", claimDeviceSchema, {
    method: "POST",
    deviceScoped: true,
    ...(signal === undefined ? {} : { signal }),
  });
}

/**
 * 로그아웃 — Spring Security 기본 `/logout`이라 JSON을 돌려주지 않는다.
 *
 * `logoutSuccessUrl`로 302 리다이렉트한다(`CommunitySecurityConfig`) — `browserMutate`는
 * 모든 쓰기 요청에 스키마 검증을 강제하므로(§13.1 원칙 1) JSON이 아닌 이 응답에는 쓸 수
 * 없다. web-legacy `community-client.ts`의 `logout()`과 같은 이유로 raw fetch를 쓴다.
 * 실패해도 던지지 않고 boolean으로 알린다 — 호출부가 재시도 문구만 보여주면 된다.
 */
export async function logout(): Promise<boolean> {
  const csrf = readCsrfToken();
  if (csrf === null) return false;

  try {
    const response = await fetch("/logout", {
      method: "POST",
      credentials: "same-origin",
      headers: { [CSRF_HEADER_NAME]: csrf },
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

/**
 * H-02: 회원 탈퇴 — `CommunityAccountController.withdraw`가 `204 No Content`를
 * 반환하고 그 요청 안에서 세션을 무효화한다. `logout()`과 같은 이유로 raw fetch를
 * 쓴다 — 응답에 검증할 JSON 바디가 없어 `browserMutate`(모든 쓰기 응답에 스키마를
 * 요구, §13.1 원칙 1)를 쓸 수 없다. 되돌릴 수 없는 동작이라 호출부가 반드시
 * 확인 절차를 거친 뒤에만 불러야 한다.
 */
export async function withdraw(): Promise<boolean> {
  const csrf = readCsrfToken();
  if (csrf === null) return false;

  try {
    const response = await fetch("/api/v1/community/me/withdrawal", {
      method: "POST",
      credentials: "same-origin",
      headers: { [CSRF_HEADER_NAME]: csrf },
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}
