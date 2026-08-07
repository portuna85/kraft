"use client";

import { NumberGrid } from "@/ui/domain/number-grid";
import styles from "./analysis.module.css";

const NUMBER_COUNT = 45;
const REQUIRED_COUNT = 6;

/**
 * 입력 문자열에서 유효 범위의 번호만 순서대로 뽑는다. 중복은 첫 등장만 남긴다.
 * 검증은 제출 시 validateLottoNumbers가 최종 판정하므로 여기서는 "지금 눌린 칸"을
 * 보여주기 위한 최선 해석만 한다.
 */
export function parseSelectedNumbers(input: string): number[] {
  const seen = new Set<number>();
  const result: number[] = [];
  for (const token of input.trim().split(/[\s,]+/)) {
    if (!token) continue;
    const n = Number(token);
    if (!Number.isInteger(n) || n < 1 || n > NUMBER_COUNT || seen.has(n)) continue;
    seen.add(n);
    result.push(n);
  }
  return result;
}

export function toInputValue(numbers: readonly number[]): string {
  return numbers.join(", ");
}

/**
 * FE-037: 예전에는 쉼표로 구분한 문자열 하나만 받아 모바일에서 오류율이 높았다.
 * 같은 저장소의 45칸 번호판을 재사용해 눌러서 고를 수 있게 한다.
 *
 * 텍스트 입력을 단일 진실 공급원으로 두고 격자는 그것을 반영·수정만 한다 — 두 상태를
 * 따로 두면 타이핑 도중 서로 덮어써서 어긋난다.
 */
export function AnalysisNumberPicker({
  input,
  onChange,
  disabled,
}: {
  input: string;
  onChange: (next: string) => void;
  disabled?: boolean;
}) {
  const selected = parseSelectedNumbers(input);
  const full = selected.length >= REQUIRED_COUNT;

  function toggle(n: number) {
    const next = selected.includes(n)
      ? selected.filter((value) => value !== n)
      : [...selected, n];
    onChange(toInputValue(next));
  }

  return (
    <div className={styles.picker}>
      <NumberGrid
        count={NUMBER_COUNT}
        ariaLabel="분석할 번호 선택판"
        onSelect={toggle}
        getState={(n) => {
          const isSelected = selected.includes(n);
          return {
            pressed: isSelected,
            stateText: isSelected ? "선택" : undefined,
            className: isSelected ? styles.cellSelected : undefined,
            // 6개를 채운 뒤에는 새 번호를 누를 수 없다는 것을 상태로 보여준다
            // (누르면 조용히 무시되는 대신).
            disabled: disabled || (full && !isSelected),
          };
        }}
      />
      <p className={styles.pickerStatus} role="status" aria-live="polite">
        {selected.length}/{REQUIRED_COUNT}개 선택
        {full ? " — 6개를 모두 골랐습니다." : ""}
      </p>
    </div>
  );
}
