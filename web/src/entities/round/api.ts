import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { serverEnv } from "@/shared/config/env";
import { serverFetch } from "@/shared/api/transport";

import {
  roundFreshnessSchema,
  winningNumberSchema,
  type RoundFreshness,
  type WinningNumber,
} from "./schema";

/**
 * 회차 API 바인딩
 *
 * **캐시 태그와 재검증 주기를 이 파일이 소유한다.** 라우트가 각자 정하면 같은 데이터에
 * 다른 주기가 붙고, 백엔드 웹훅이 무효화해야 할 태그를 놓친다.
 *
 * 태그 이름은 백엔드 RevalidateWebhookListener와 **문자열이 정확히 일치**해야 한다
 * (§24.2 3번). 어긋나면 커밋 후에도 화면이 옛 회차를 계속 보여준다 — 조용히 실패하는
 * 종류라 T-30 통합 테스트로 대조한다(R-6).
 */
export const ROUNDS_LATEST_TAG = CACHE_TAGS.roundsLatest;
export const REVALIDATE_LATEST_SECONDS = 60;

function backendUrl(path: string): string {
  return `${serverEnv.backendInternalUrl}${path}`;
}

/**
 * 최신 당첨 회차. 홈의 LCP 요소이자 이 서비스의 존재 이유다.
 *
 * **실패를 200으로 감추지 않는다**(§6.5). 폴백 화면을 200으로 내보내면 업타임 체커와
 * 크롤러가 장애를 보지 못하고, Caddy가 그 상태를 60초 캐시한다. 여기서 던지면
 * 라우트 그룹의 error 경계가 받아 5xx가 된다.
 */
export function getLatestRound(): Promise<WinningNumber> {
  return serverFetch(backendUrl("/api/v1/rounds/latest"), winningNumberSchema, {
    cache: {
      mode: "revalidate",
      seconds: REVALIDATE_LATEST_SECONDS,
      tags: [ROUNDS_LATEST_TAG],
    },
  });
}

/** 데이터 신선도. 부수 데이터라 실패해도 화면 전체를 죽이지 않는다(§7.6). */
export function getRoundFreshness(): Promise<RoundFreshness> {
  return serverFetch(backendUrl("/api/v1/rounds/freshness"), roundFreshnessSchema, {
    cache: {
      mode: "revalidate",
      seconds: REVALIDATE_LATEST_SECONDS,
      tags: [ROUNDS_LATEST_TAG],
    },
  });
}
