import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import {
  incidentTypeLabel,
  statusIncidentListSchema,
  statusIncidentSchema,
  type StatusIncident,
} from "./schema";

describe("장애 이력 스키마", () => {
  const VALID = {
    round: 1150,
    type: "EXTERNAL_COLLECT",
    resolved: true,
    occurredAt: "2026-08-01T12:00:00Z",
    occurrences: 1,
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(statusIncidentSchema, VALID).success).toBe(true);
  });

  it("회차가 없는 이력도 통과시킨다", () => {
    expect(v.safeParse(statusIncidentSchema, { ...VALID, round: null }).success).toBe(true);
  });

  it("occurrences가 1 미만이면 막는다", () => {
    expect(v.safeParse(statusIncidentSchema, { ...VALID, occurrences: 0 }).success).toBe(false);
  });

  it("목록 스키마가 여러 항목을 통과시킨다", () => {
    expect(v.safeParse(statusIncidentListSchema, [VALID, VALID]).success).toBe(true);
  });
});

describe("운영 유형 라벨", () => {
  it("알려진 유형을 한글로 바꾼다", () => {
    expect(incidentTypeLabel("EXTERNAL_COLLECT")).toBe("당첨번호 자동 수집");
    expect(incidentTypeLabel("MANUAL_UPSERT")).toBe("운영자 수동 보정");
  });

  it("모르는 유형은 원문 그대로 보여준다", () => {
    expect(incidentTypeLabel("SOME_NEW_TYPE")).toBe("SOME_NEW_TYPE");
  });
});

/** 생성 타입과의 정합성 — M-07, QA-FE-02(양방향) */
type GeneratedIncident = components["schemas"]["PublicIncidentResponse"];
const _typesMatch: StatusIncident extends GeneratedIncident ? true : never = true;
void _typesMatch;
const _reverseTypesMatch: GeneratedIncident extends StatusIncident ? true : never = true;
void _reverseTypesMatch;
