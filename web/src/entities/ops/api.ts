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
 * 운영 콘솔 API — improvement_fe.md §23.14, §25.7
 *
 * `next.config.ts`의 `/ops-api/:path*` → 백엔드 `/ops/:path*` 리라이트를 그대로
 * 탄다. 백엔드가 `X-Ops-Token`으로 별도 인증하므로 매 호출에 토큰을 넘긴다 —
 * 서버에 저장하지 않고 호출부(컴포넌트 상태)가 들고 있는 값을 그대로 전달한다.
 */

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
  });
}

export function collectRound(token: string, round: number): Promise<WinningNumberResult> {
  return opsMutate(`/ops-api/collect/${round}`, winningNumberResultSchema, {
    token,
    method: "POST",
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
