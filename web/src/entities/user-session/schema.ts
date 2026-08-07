import * as v from "valibot";

/**
 * 세션 계약 — improvement_fe.md §24.2(1: API 요청·응답 형태는 바뀌면 안 된다)
 *
 * 백엔드 CommunitySessionResponse와 1:1이다. 손으로 쓴 타입가드였다면 activeProviders가
 * 문자열 배열인지까지는 검사하지 않았을 것이다(§6.2 H-3) — 그 값이 비면 로그인 링크가
 * 사라지므로, 여기서 막지 않으면 복구 경로 없는 화면이 그대로 렌더된다.
 */
export const communitySessionSchema = v.object({
  loggedIn: v.boolean(),
  userId: v.nullable(v.number()),
  nickname: v.nullable(v.string()),
  activeProviders: v.array(v.string()),
});

export type CommunitySession = v.InferOutput<typeof communitySessionSchema>;

/** claim-device 응답. 본문을 쓰지 않지만 파싱 자체는 통과해야 성공으로 본다. */
export const claimDeviceSchema = v.nullable(v.unknown());
