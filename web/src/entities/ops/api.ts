import { opsMutate, opsQuery } from "@/shared/api/ops-transport";

import {
  operationLogPageSchema,
  opsSummarySchema,
  winningNumberResultSchema,
  type OperationLogPage,
  type OpsSummary,
  type WinningNumberResult,
  type WinningNumberUpsertRequest,
} from "./schema";

/**
 * 운영 콘솔 API
 *
 * `next.config.ts`의 `/ops-api/:path*` → 백엔드 `/ops/:path*` 리라이트를 그대로
 * 탄다. 백엔드가 `X-Ops-Token`으로 별도 인증하므로 매 호출에 토큰을 넘긴다 —
 * 서버에 저장하지 않고 호출부(컴포넌트 상태)가 들고 있는 값을 그대로 전달한다.
 */

// TD-001: 수동 수집은 백엔드에서 최대 2회 재시도(3초 connect + 5초 read, 500ms 대기)를
// 거쳐 최악 약 16.5초가 걸릴 수 있다. Caddy의 /ops-api/collect/* 전용 타임아웃(20초,
// caddy/Caddyfile)과 짝을 맞춘 값 — 다른 ops 호출(summary/logs/rounds)의 기본 5초는
// 그대로 둔다.
const COLLECT_TIMEOUT_MS = 20_000;

export function getOpsSummary(token: string): Promise<OpsSummary> {
  return opsQuery("/ops-api/summary", opsSummarySchema, token);
}

export function getOpsLogs(
  token: string,
  params: { page: number; size: number },
): Promise<OperationLogPage> {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
  });
  return opsQuery(`/ops-api/logs?${query.toString()}`, operationLogPageSchema, token);
}

export function collectLatest(token: string): Promise<WinningNumberResult> {
  return opsMutate("/ops-api/collect/latest", winningNumberResultSchema, {
    token,
    method: "POST",
    timeoutMs: COLLECT_TIMEOUT_MS,
  });
}

export function collectRound(token: string, round: number): Promise<WinningNumberResult> {
  return opsMutate(`/ops-api/collect/${round}`, winningNumberResultSchema, {
    token,
    method: "POST",
    timeoutMs: COLLECT_TIMEOUT_MS,
  });
}

export function upsertRound(
  token: string,
  payload: WinningNumberUpsertRequest,
): Promise<WinningNumberResult> {
  return opsMutate("/ops-api/rounds", winningNumberResultSchema, {
    token,
    method: "POST",
    body: payload,
  });
}
