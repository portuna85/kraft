"use client";

import { useRef, useState } from "react";
import { validateManualEntry } from "@/lib/ops-manual-entry";
import {
  initialManualEntryForm,
  type ManualEntryField,
  type ManualEntryForm,
  type WinningNumberUpsertBody,
} from "@/lib/ops-types";

const MANUAL_ERROR_ID = "ops-manual-error";

/**
 * 수동 적재 폼.
 *
 * FE-078: 폼 값·필드 오류·포커스 복구가 대시보드 본체에 섞여 있었다. 여기서 스스로
 * 관리하고, 부모에는 **검증을 통과한 본문만** 넘긴다 — 부모가 원시 입력을 다시 만질
 * 일이 없으므로 FE-074 같은 검증 우회가 구조적으로 어려워진다.
 */
export function ManualEntryFormPanel({
  disabled,
  submitting,
  onSubmit,
  onValidationError,
}: {
  disabled: boolean;
  submitting: boolean;
  /** 성공하면 true를 돌려준다 — 그때만 폼을 비운다. */
  onSubmit: (body: WinningNumberUpsertBody) => Promise<boolean>;
  onValidationError: (message: string) => void;
}) {
  const [form, setForm] = useState<ManualEntryForm>(initialManualEntryForm);
  const [fieldError, setFieldError] = useState<{ field: ManualEntryField; message: string } | null>(null);
  const fieldRefs = useRef<Partial<Record<ManualEntryField, HTMLInputElement | null>>>({});

  function update(field: ManualEntryField, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  /**
   * 필드마다 반복되는 오류 연결·값 배선을 한곳에서 만든다.
   * ref는 여기서 만들지 않는다 — 렌더 중 호출되는 함수가 ref 콜백을 돌려주는 형태를
   * react-hooks/refs가 금지하므로 각 input에 직접 단다.
   */
  function fieldProps(field: ManualEntryField) {
    return {
      "aria-invalid": fieldError?.field === field || undefined,
      "aria-describedby": fieldError?.field === field ? MANUAL_ERROR_ID : undefined,
      value: form[field],
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => update(field, event.target.value),
    };
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validation = validateManualEntry(form);
    if (!validation.ok) {
      setFieldError(validation);
      onValidationError(validation.message);
      fieldRefs.current[validation.field]?.focus();
      return;
    }
    setFieldError(null);

    const saved = await onSubmit(validation.body);
    if (saved) setForm(initialManualEntryForm);
  }

  return (
    <article className="panel">
      <p className="eyebrow">수동 적재</p>
      <h2 className="ops-title">회차 직접 입력</h2>
      <p className="page-subtitle">외부 수집 실패나 데이터 보정이 필요한 경우 회차 정보를 직접 등록합니다.</p>
      <form className="ops-manual-form" onSubmit={handleSubmit}>
        <label className="ops-field">
          <span>회차</span>
          <input
            type="number"
            inputMode="numeric"
            min="1"
            required
            placeholder="예: 1201"
            ref={(el) => { fieldRefs.current.round = el; }}
            {...fieldProps("round")}
          />
        </label>
        <label className="ops-field">
          <span>추첨일</span>
          <input
            type="date"
            required
            ref={(el) => { fieldRefs.current.drawDate = el; }}
            {...fieldProps("drawDate")}
          />
        </label>
        <label className="ops-field">
          <span>당첨 번호 6개</span>
          <input
            required
            placeholder="예: 3, 11, 19, 28, 34, 42"
            ref={(el) => { fieldRefs.current.numbers = el; }}
            {...fieldProps("numbers")}
          />
        </label>
        <label className="ops-field">
          <span>보너스 번호</span>
          <input
            type="number"
            inputMode="numeric"
            min="1"
            max="45"
            required
            placeholder="예: 7"
            ref={(el) => { fieldRefs.current.bonusNumber = el; }}
            {...fieldProps("bonusNumber")}
          />
        </label>
        <label className="ops-field">
          <span>1등 당첨금</span>
          <input
            type="number"
            inputMode="numeric"
            min="0"
            required
            placeholder="예: 2100000000"
            ref={(el) => { fieldRefs.current.firstPrizeAmount = el; }}
            {...fieldProps("firstPrizeAmount")}
          />
        </label>
        {fieldError ? (
          <p id={MANUAL_ERROR_ID} className="status-text ops-status" role="alert">
            {fieldError.message}
          </p>
        ) : null}
        <button type="submit" disabled={disabled}>
          {submitting ? "저장 중..." : "수동 등록"}
        </button>
      </form>
    </article>
  );
}
