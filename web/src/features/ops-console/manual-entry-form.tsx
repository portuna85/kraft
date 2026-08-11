"use client";

import * as v from "valibot";

import { upsertRound } from "@/entities/ops/api";
import type { WinningNumberResult, WinningNumberUpsertRequest } from "@/entities/ops/schema";
import { ApiError } from "@/shared/api/error";
import { useForm } from "@/shared/hooks/use-form";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/field";
import { InlineAlert } from "@/shared/ui/states";

/**
 * 수동 적재 폼 — improvement_fe.md §23.14 ④.
 *
 * 백엔드가 받는 `numbers`는 배열이지만 사용자는 텍스트 하나로 6개를 입력한다 —
 * `useForm`의 필드는 원시값 하나씩만 다루므로, 폼 값 자체는 문자열 9개로 두고
 * (`numbersText`가 배열을 대신한다) 제출 시점에 파싱해 실제 요청 모양으로 바꾼다.
 *
 * 보너스 번호가 당첨 번호 6개와 겹치면 안 된다는 규칙은 필드 하나로 못 끝나는
 * 교차 필드 검증이라 스키마에 넣지 않는다 — Valibot 객체 레벨 `v.check`는 이슈에
 * `path`가 없어 `useForm`의 필드별 오류 수집(`collectIssues`)이 조용히 무시해
 * 버린다(handleSubmit이 필드 오류 0개로 보고 그대로 제출을 통과시킨다). 대신
 * `onSubmit` 안에서 직접 검사해 던지면 `useForm`의 기존 `formError` 경로를 탄다.
 */

function isNonNegativeIntegerString(text: string): boolean {
  return /^\d+$/.test(text);
}

function isPositiveIntegerString(text: string): boolean {
  return isNonNegativeIntegerString(text) && Number(text) >= 1;
}

function isLottoNumberString(text: string): boolean {
  return isPositiveIntegerString(text) && Number(text) <= 45;
}

function parseNumbersText(text: string): number[] | null {
  const parts = text
    .split(/[,\s]+/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
  if (parts.length !== 6 || !parts.every(isLottoNumberString)) return null;

  const numbers = parts.map(Number);
  return new Set(numbers).size === 6 ? numbers : null;
}

const optionalNonNegativeString = v.pipe(
  v.string(),
  v.check(
    (text) => text === "" || isNonNegativeIntegerString(text),
    "비워두거나 0 이상의 정수를 입력해 주세요.",
  ),
);

const manualEntryFormSchema = v.object({
  round: v.pipe(v.string(), v.check(isPositiveIntegerString, "1 이상의 정수를 입력해 주세요.")),
  drawDate: v.pipe(v.string(), v.minLength(1, "추첨일을 입력해 주세요.")),
  numbersText: v.pipe(
    v.string(),
    v.check(
      (text) => parseNumbersText(text) !== null,
      "1~45 사이의 서로 다른 정수 6개를 쉼표나 공백으로 구분해 입력해 주세요.",
    ),
  ),
  bonusNumber: v.pipe(
    v.string(),
    v.check(isLottoNumberString, "1~45 사이의 정수를 입력해 주세요."),
  ),
  firstPrizeAmount: v.pipe(
    v.string(),
    v.check(isNonNegativeIntegerString, "0 이상의 정수를 입력해 주세요."),
  ),
  secondPrize: optionalNonNegativeString,
  secondWinners: optionalNonNegativeString,
  totalSales: optionalNonNegativeString,
  firstAccumAmount: optionalNonNegativeString,
});

type ManualEntryFormValues = v.InferOutput<typeof manualEntryFormSchema>;

const INITIAL_VALUES: ManualEntryFormValues = {
  round: "",
  drawDate: "",
  numbersText: "",
  bonusNumber: "",
  firstPrizeAmount: "",
  secondPrize: "",
  secondWinners: "",
  totalSales: "",
  firstAccumAmount: "",
};

function toOptionalNumber(text: string): number | undefined {
  return text === "" ? undefined : Number(text);
}

function buildPayload(
  values: ManualEntryFormValues,
  numbers: number[],
): WinningNumberUpsertRequest {
  return {
    round: Number(values.round),
    drawDate: values.drawDate,
    numbers,
    bonusNumber: Number(values.bonusNumber),
    firstPrizeAmount: Number(values.firstPrizeAmount),
    secondPrize: toOptionalNumber(values.secondPrize),
    secondWinners: toOptionalNumber(values.secondWinners),
    totalSales: toOptionalNumber(values.totalSales),
    firstAccumAmount: toOptionalNumber(values.firstAccumAmount),
  };
}

type ManualEntryFormProps = {
  token: string;
  onStart: () => void;
  onSuccess: (result: WinningNumberResult) => void;
  onError: (message: string) => void;
};

export function ManualEntryForm({ token, onStart, onSuccess, onError }: ManualEntryFormProps) {
  const form = useForm<ManualEntryFormValues>({
    initialValues: INITIAL_VALUES,
    schema: manualEntryFormSchema,
    onSubmit: async (values) => {
      const numbers = parseNumbersText(values.numbersText);
      if (numbers === null) {
        // 스키마가 이미 통과시켰다면 있을 수 없지만, 타입을 좁히기 위해 다시 확인한다.
        throw new ApiError("client", "번호 6개를 다시 확인해 주세요.");
      }
      if (numbers.includes(Number(values.bonusNumber))) {
        throw new ApiError("client", "보너스 번호는 당첨 번호 6개와 달라야 합니다.");
      }

      onStart();
      try {
        const result = await upsertRound(token, buildPayload(values, numbers));
        onSuccess(result);
      } catch (cause) {
        onError(cause instanceof ApiError ? cause.message : "수동 적재에 실패했습니다.");
        throw cause;
      }
    },
  });

  const round = form.field("round");
  const drawDate = form.field("drawDate");
  const numbersText = form.field("numbersText");
  const bonusNumber = form.field("bonusNumber");
  const firstPrizeAmount = form.field("firstPrizeAmount");
  const secondPrize = form.field("secondPrize");
  const secondWinners = form.field("secondWinners");
  const totalSales = form.field("totalSales");
  const firstAccumAmount = form.field("firstAccumAmount");

  return (
    <form className="stack" onSubmit={form.handleSubmit} noValidate>
      <TextField
        label="회차"
        name="round"
        inputMode="numeric"
        value={round.value}
        onChange={(event) => round.onChange(event.target.value)}
        onBlur={round.onBlur}
        error={round.error}
      />
      <TextField
        label="추첨일"
        name="drawDate"
        type="date"
        value={drawDate.value}
        onChange={(event) => drawDate.onChange(event.target.value)}
        onBlur={drawDate.onBlur}
        error={drawDate.error}
      />
      <TextField
        label="당첨 번호 6개"
        name="numbersText"
        hint="쉼표나 공백으로 구분해 입력하세요. 예: 1, 8, 17, 24, 33, 41"
        value={numbersText.value}
        onChange={(event) => numbersText.onChange(event.target.value)}
        onBlur={numbersText.onBlur}
        error={numbersText.error}
      />
      <TextField
        label="보너스 번호"
        name="bonusNumber"
        inputMode="numeric"
        value={bonusNumber.value}
        onChange={(event) => bonusNumber.onChange(event.target.value)}
        onBlur={bonusNumber.onBlur}
        error={bonusNumber.error}
      />
      <TextField
        label="1등 당첨금"
        name="firstPrizeAmount"
        inputMode="numeric"
        value={firstPrizeAmount.value}
        onChange={(event) => firstPrizeAmount.onChange(event.target.value)}
        onBlur={firstPrizeAmount.onBlur}
        error={firstPrizeAmount.error}
      />
      <TextField
        label="2등 당첨금 (선택)"
        name="secondPrize"
        inputMode="numeric"
        value={secondPrize.value}
        onChange={(event) => secondPrize.onChange(event.target.value)}
        onBlur={secondPrize.onBlur}
        error={secondPrize.error}
      />
      <TextField
        label="2등 당첨자 수 (선택)"
        name="secondWinners"
        inputMode="numeric"
        value={secondWinners.value}
        onChange={(event) => secondWinners.onChange(event.target.value)}
        onBlur={secondWinners.onBlur}
        error={secondWinners.error}
      />
      <TextField
        label="총 판매액 (선택)"
        name="totalSales"
        inputMode="numeric"
        value={totalSales.value}
        onChange={(event) => totalSales.onChange(event.target.value)}
        onBlur={totalSales.onBlur}
        error={totalSales.error}
      />
      <TextField
        label="1등 누적금 (선택)"
        name="firstAccumAmount"
        inputMode="numeric"
        value={firstAccumAmount.value}
        onChange={(event) => firstAccumAmount.onChange(event.target.value)}
        onBlur={firstAccumAmount.onBlur}
        error={firstAccumAmount.error}
      />

      {form.state.formError !== null && (
        <InlineAlert tone="danger">{form.state.formError}</InlineAlert>
      )}

      {form.state.isSubmitting ? (
        <Button type="submit" loading loadingLabel="적재 중" disabled={token.trim().length === 0}>
          적재
        </Button>
      ) : (
        <Button type="submit" disabled={token.trim().length === 0}>
          적재
        </Button>
      )}
    </form>
  );
}
