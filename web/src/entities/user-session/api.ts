import { browserMutate, browserQuery } from "@/shared/api/transport";

import { claimDeviceSchema, communitySessionSchema, type CommunitySession } from "./schema";

/**
 * 세션 API 바인딩 — improvement_fe.md §7.5(entity가 자기 엔드포인트를 소유한다)
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
