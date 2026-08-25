import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/ops-api-types";

import {
  operationLogPageSchema,
  operationLogSchema,
  opsSummarySchema,
  winningNumberResultSchema,
  winningNumberUpsertRequestSchema,
  type OperationLog,
  type OperationLogPage,
  type OpsSummary,
  type WinningNumberResult,
  type WinningNumberUpsertRequest,
} from "./schema";

const SUMMARY = {
  service: "kraft-lotto",
  timezone: "Asia/Seoul",
  status: "UP",
  latestRound: 1150,
  latestDrawDate: "2026-08-01",
  checkedAt: "2026-08-25T00:00:00+09:00",
  fresh: true,
};

const RESULT = {
  round: 1150,
  drawDate: "2026-08-01",
  numbers: [3, 11, 24, 30, 38, 44],
  bonusNumber: 7,
  firstPrizeAmount: 2_100_000_000,
  secondPrize: 60_000_000,
  secondWinners: 35,
  totalSales: 110_000_000_000,
  firstAccumAmount: 2_100_000_000,
};

const LOG = {
  id: 42,
  operationType: "MANUAL_UPSERT",
  executionStatus: "SUCCESS",
  round: 1150,
  sourceDetail: "ops-console",
  message: null,
  requestId: "req-1",
  createdAt: "2026-08-25T00:00:00+09:00",
};

describe("opsSummarySchema", () => {
  it("정상 요약을 통과시킨다", () => {
    expect(v.parse(opsSummarySchema, SUMMARY)).toEqual(SUMMARY);
  });

  it("아직 수집된 회차가 없으면 latestRound·latestDrawDate가 null이다", () => {
    const empty = { ...SUMMARY, latestRound: null, latestDrawDate: null, fresh: false };
    expect(v.parse(opsSummarySchema, empty)).toEqual(empty);
  });

  it("필드가 빠지면 거부한다", () => {
    const { fresh: _fresh, ...withoutFresh } = SUMMARY;
    expect(() => v.parse(opsSummarySchema, withoutFresh)).toThrow();
  });
});

describe("winningNumberResultSchema", () => {
  it("정상 회차 결과를 통과시킨다", () => {
    expect(v.parse(winningNumberResultSchema, RESULT)).toEqual(RESULT);
  });

  it("번호가 6개가 아니면 거부한다", () => {
    expect(() => v.parse(winningNumberResultSchema, { ...RESULT, numbers: [1, 2, 3] })).toThrow();
  });

  it("범위를 벗어난 번호를 거부한다", () => {
    expect(() =>
      v.parse(winningNumberResultSchema, { ...RESULT, numbers: [3, 11, 24, 30, 38, 46] }),
    ).toThrow();
  });
});

describe("winningNumberUpsertRequestSchema", () => {
  it("선택 4필드를 비워도 통과한다", () => {
    const minimal = {
      round: 1151,
      drawDate: "2026-08-08",
      numbers: [1, 2, 3, 4, 5, 6],
      bonusNumber: 7,
      firstPrizeAmount: 0,
    };
    expect(v.parse(winningNumberUpsertRequestSchema, minimal)).toEqual(minimal);
  });

  it("보너스 번호가 당첨 번호와 겹치면 거부한다", () => {
    expect(() =>
      v.parse(winningNumberUpsertRequestSchema, {
        round: 1151,
        drawDate: "2026-08-08",
        numbers: [1, 2, 3, 4, 5, 6],
        bonusNumber: 6,
        firstPrizeAmount: 0,
      }),
    ).toThrow("보너스 번호는 당첨 번호 6개와 달라야 합니다.");
  });

  it("음수 금액을 거부한다", () => {
    expect(() =>
      v.parse(winningNumberUpsertRequestSchema, {
        round: 1151,
        drawDate: "2026-08-08",
        numbers: [1, 2, 3, 4, 5, 6],
        bonusNumber: 7,
        firstPrizeAmount: -1,
      }),
    ).toThrow();
  });
});

describe("operationLogSchema", () => {
  it("정상 로그를 통과시킨다", () => {
    expect(v.parse(operationLogSchema, LOG)).toEqual(LOG);
  });

  it("알 수 없는 operationType을 거부한다", () => {
    expect(() => v.parse(operationLogSchema, { ...LOG, operationType: "UNKNOWN" })).toThrow();
  });

  it("알 수 없는 executionStatus를 거부한다", () => {
    expect(() => v.parse(operationLogSchema, { ...LOG, executionStatus: "PENDING" })).toThrow();
  });

  it("페이지 응답을 통과시킨다", () => {
    const page = { items: [LOG], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    expect(v.parse(operationLogPageSchema, page)).toEqual(page);
  });
});

/**
 * 생성 타입과의 정합성 — FE-API-02
 *
 * `/ops` 계약은 백엔드 `ops` 그룹(`/v3/api-docs/ops`)에서 생성된다. 아래 단언이
 * 백엔드 필드 이름·타입 변경을 컴파일 타임에 잡는다 — 운영 콘솔은 사고 대응 중에
 * 열릴 가능성이 높은 화면이라 런타임 SCHEMA_MISMATCH로 처음 아는 것을 피해야 한다.
 */
type GeneratedOpsSummary = components["schemas"]["OpsSummaryResponse"];
const _summaryTypesMatch: OpsSummary extends GeneratedOpsSummary ? true : never = true;
void _summaryTypesMatch;
const _summaryReverseTypesMatch: GeneratedOpsSummary extends OpsSummary ? true : never = true;
void _summaryReverseTypesMatch;

/** round entity의 winningNumberSchema 복제본이 갈라지지 않게 같은 생성 타입에 묶는다. */
type GeneratedWinningNumber = components["schemas"]["WinningNumberResponse"];
const _resultTypesMatch: WinningNumberResult extends GeneratedWinningNumber ? true : never = true;
void _resultTypesMatch;
const _resultReverseTypesMatch: GeneratedWinningNumber extends WinningNumberResult ? true : never =
  true;
void _resultReverseTypesMatch;

type GeneratedUpsertRequest = components["schemas"]["WinningNumberUpsertRequest"];
const _upsertTypesMatch: WinningNumberUpsertRequest extends GeneratedUpsertRequest ? true : never =
  true;
void _upsertTypesMatch;
const _upsertReverseTypesMatch: GeneratedUpsertRequest extends WinningNumberUpsertRequest
  ? true
  : never = true;
void _upsertReverseTypesMatch;

type GeneratedOperationLog = components["schemas"]["WinningNumberOperationLogResponse"];
const _logTypesMatch: OperationLog extends GeneratedOperationLog ? true : never = true;
void _logTypesMatch;
/**
 * operationType·executionStatus는 백엔드가 enum.name()을 그대로 문자열로 내보내
 * 생성 타입이 string이다 — 프론트는 picklist로 **의도적으로 좁혔다**(API-COMM-01
 * 커밋에서 그대로 유지하기로 확정). 그래서 역방향은 그 두 필드만 string으로 되돌려
 * 예외 처리한다 — 이 필드 자체의 누락은 여전히 못 잡지만, id·round 같은 다른 필드가
 * 새로 추가되는 것은 이 완화 없이도 계속 잡힌다.
 */
type OperationLogWidened = Omit<OperationLog, "operationType" | "executionStatus"> & {
  operationType: string;
  executionStatus: string;
};
const _logReverseTypesMatch: GeneratedOperationLog extends OperationLogWidened ? true : never =
  true;
void _logReverseTypesMatch;

type GeneratedOperationLogPage =
  components["schemas"]["PageResponseWinningNumberOperationLogResponse"];
const _logPageTypesMatch: OperationLogPage extends GeneratedOperationLogPage ? true : never = true;
void _logPageTypesMatch;
/** items 안의 operationType·executionStatus도 위와 같은 이유로 완화한다. */
type OperationLogPageWidened = Omit<OperationLogPage, "items"> & { items: OperationLogWidened[] };
const _logPageReverseTypesMatch: GeneratedOperationLogPage extends OperationLogPageWidened
  ? true
  : never = true;
void _logPageReverseTypesMatch;
