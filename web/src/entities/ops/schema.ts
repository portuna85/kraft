import * as v from "valibot";

/**
 * 운영 콘솔 계약 — improvement_fe.md §23.14, §25.7
 *
 * `/ops` 계약은 OpenAPI 코드젠 대상에서 제외돼 있다(`springdoc.pathsToMatch:
 * /api/**`) — `generate-api-types.mjs`가 못 잡으므로 여기서 직접 손으로 쓴다.
 *
 * 회차 응답 자체는 `entities/round/schema.ts`의 `winningNumberSchema`와 모양이
 * 같지만(백엔드 `WinningNumberResponse`), entity는 다른 entity를 import할 수
 * 없다는 아키텍처 제약(`no-restricted-imports`)이 있어 여기서 따로 정의한다.
 */

const lottoNumberSchema = v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(45));

export const opsSummarySchema = v.object({
  service: v.string(),
  timezone: v.string(),
  status: v.string(),
  latestRound: v.nullable(v.pipe(v.number(), v.integer())),
  latestDrawDate: v.nullable(v.string()),
  checkedAt: v.string(),
  fresh: v.boolean(),
});

export type OpsSummary = v.InferOutput<typeof opsSummarySchema>;

/** `POST /ops/collect/latest`·`POST /ops/collect/{round}`·`POST /ops/rounds` 응답. */
export const winningNumberResultSchema = v.object({
  round: v.pipe(v.number(), v.integer(), v.minValue(1)),
  drawDate: v.string(),
  numbers: v.pipe(v.array(lottoNumberSchema), v.length(6)),
  bonusNumber: lottoNumberSchema,
  firstPrizeAmount: v.number(),
  secondPrize: v.number(),
  secondWinners: v.number(),
  totalSales: v.number(),
  firstAccumAmount: v.number(),
});

export type WinningNumberResult = v.InferOutput<typeof winningNumberResultSchema>;

/**
 * `POST /ops/rounds` 요청 — 백엔드 `WinningNumberUpsertRequest`와 같은 9필드.
 * round·drawDate·numbers·bonusNumber·firstPrizeAmount는 필수, 나머지 4개는
 * 선택(비우면 백엔드가 0으로 채운다 — 그래서 여기서도 기본값을 만들지 않고
 * `undefined`를 그대로 보낸다).
 *
 * 보너스 번호가 당첨 번호 6개와 겹치면 안 된다는 규칙은 단일 필드로 못 끝나서
 * `v.check`로 객체 레벨에 둔다 — 위반 시 이슈의 `path`가 없어 필드별로 못 붙는다.
 * 호출부(수동 적재 폼)는 이 경우 폼 상단 알림으로 보여준다.
 */
const optionalNonNegative = v.optional(v.pipe(v.number(), v.minValue(0)));

export const winningNumberUpsertRequestSchema = v.pipe(
  v.object({
    round: v.pipe(v.number(), v.integer(), v.minValue(1)),
    drawDate: v.pipe(v.string(), v.minLength(1)),
    numbers: v.pipe(v.array(lottoNumberSchema), v.length(6)),
    bonusNumber: lottoNumberSchema,
    firstPrizeAmount: v.pipe(v.number(), v.minValue(0)),
    secondPrize: optionalNonNegative,
    secondWinners: optionalNonNegative,
    totalSales: optionalNonNegative,
    firstAccumAmount: optionalNonNegative,
  }),
  v.check(
    (input) => !input.numbers.includes(input.bonusNumber),
    "보너스 번호는 당첨 번호 6개와 달라야 합니다.",
  ),
);

export type WinningNumberUpsertRequest = v.InferOutput<typeof winningNumberUpsertRequestSchema>;

export const OPERATION_TYPES = ["EXTERNAL_COLLECT", "MANUAL_UPSERT"] as const;
export type OperationType = (typeof OPERATION_TYPES)[number];

export const OPERATION_TYPE_LABELS: Record<OperationType, string> = {
  EXTERNAL_COLLECT: "자동 수집",
  MANUAL_UPSERT: "수동 적재",
};

export const OPERATION_STATUSES = ["SUCCESS", "FAILURE"] as const;
export type OperationStatus = (typeof OPERATION_STATUSES)[number];

export const OPERATION_STATUS_LABELS: Record<OperationStatus, string> = {
  SUCCESS: "성공",
  FAILURE: "실패",
};

export const operationLogSchema = v.object({
  id: v.number(),
  operationType: v.picklist(OPERATION_TYPES),
  executionStatus: v.picklist(OPERATION_STATUSES),
  round: v.nullable(v.pipe(v.number(), v.integer())),
  sourceDetail: v.nullable(v.string()),
  message: v.nullable(v.string()),
  requestId: v.nullable(v.string()),
  createdAt: v.string(),
});

export type OperationLog = v.InferOutput<typeof operationLogSchema>;

export const operationLogPageSchema = v.object({
  items: v.array(operationLogSchema),
  page: v.pipe(v.number(), v.integer()),
  size: v.pipe(v.number(), v.integer()),
  totalElements: v.pipe(v.number(), v.integer()),
  totalPages: v.pipe(v.number(), v.integer()),
});

export type OperationLogPage = v.InferOutput<typeof operationLogPageSchema>;
