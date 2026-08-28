import { serverFetch } from "@/shared/api/transport";
import { serverEnv } from "@/shared/config/env";

import { analysisSchema, type CombinationAnalysis } from "./schema";

/**
 * 조합 분석 — 백엔드가 POST로 받는다(`POST /api/v1/stats/analysis`).
 *
 * 부수효과는 없지만 번호 6개를 본문에 실어야 해서 POST다. Next의 fetch 캐시는 GET에만
 * 걸리므로 no-store로 둔다 — revalidate를 적어 두면 캐시된 줄 알았는데 아닌 상태가 된다.
 *
 * 이 응답은 패턴 분석과 역대 1등 이력을 함께 준다. 그래서 `/api/v1/numbers/check`를
 * 따로 부르지 않아도 "이 조합이 1등으로 나온 적 있는지"까지 한 번에 답할 수 있다.
 */
export function analyzeCombination(numbers: readonly number[]): Promise<CombinationAnalysis> {
  return serverFetch(`${serverEnv.backendInternalUrl}/api/v1/stats/analysis`, analysisSchema, {
    method: "POST",
    body: { numbers: [...numbers] },
    cache: { mode: "no-store" },
  });
}
