import * as v from "valibot";

/**
 * 공개 장애·보정 이력 계약
 *
 * 백엔드가 내부 운영 로그(IP·요청 ID·원문 예외)를 걸러 회차·유형·해결 여부만 보낸다.
 * 같은 (유형, 회차)의 반복은 한 항목으로 집계되고 occurrences가 그 횟수다.
 */
export const statusIncidentSchema = v.object({
  /** 회차를 특정할 수 없는 이력도 있다(수집 자체가 실패한 경우). */
  round: v.nullable(v.pipe(v.number(), v.integer())),
  type: v.string(),
  resolved: v.boolean(),
  occurredAt: v.string(),
  occurrences: v.pipe(v.number(), v.integer(), v.minValue(1)),
});

export type StatusIncident = v.InferOutput<typeof statusIncidentSchema>;

export const statusIncidentListSchema = v.array(statusIncidentSchema);

/**
 * 운영 유형을 사람 말로 — 레거시는 EXTERNAL_COLLECT를 그대로 화면에 보여줬다.
 *
 * 백엔드 enum(WinningNumberOperationType)이 늘어날 수 있으므로 모르는 값은 버리지 않고
 * 원문을 보여준다. 빈 칸으로 두면 "무슨 일이 있었는지" 자체가 사라진다.
 */
const INCIDENT_TYPE_LABELS: Record<string, string> = {
  EXTERNAL_COLLECT: "당첨번호 자동 수집",
  MANUAL_UPSERT: "운영자 수동 보정",
};

export function incidentTypeLabel(type: string): string {
  return INCIDENT_TYPE_LABELS[type] ?? type;
}
