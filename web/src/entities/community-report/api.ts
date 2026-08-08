import * as v from "valibot";

import { browserMutate } from "@/shared/api/transport";

import type { ReportReason, ReportTargetType } from "./schema";

/** 신고 접수. 응답 본문은 쓰지 않지만 형태 검증은 거친다. */
export function reportContent(
  targetType: ReportTargetType,
  targetId: number,
  reason: ReportReason,
): Promise<unknown> {
  return browserMutate("/api/v1/community/reports", v.unknown(), {
    method: "POST",
    body: { targetType, targetId, reason },
  });
}
