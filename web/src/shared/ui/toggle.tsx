"use client";

import styles from "./field.module.css";

/**
 * Switch
 *
 * `role="switch"` + `aria-checked`를 쓴다. 체크박스로 대신하면 스크린리더가 "선택됨"
 * 이라고만 읽어, 즉시 적용되는 설정 토글인지 제출해야 반영되는 선택인지 구분되지 않는다.
 */
export function Switch({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <label className={styles.choice}>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={disabled}
        className={styles.switchTrack}
        onClick={() => onChange(!checked)}
      >
        <span className={styles.switchThumb} aria-hidden="true" />
      </button>
      <span aria-hidden="true">{label}</span>
    </label>
  );
}

export type SegmentedOption<T extends string> = { value: T; label: string };

/**
 * SegmentedControl — 상호 배타 선택.
 *
 * `role="radiogroup"`이라 화살표로 이동하고 그룹 전체가 탭 정지점 하나다. 버튼 여러 개로
 * 만들면 선택 상태가 보조 기술에 전달되지 않고 Tab이 옵션 수만큼 늘어난다.
 * `aria-label`이 필수인 이유는 "무엇을 고르는 그룹인지"가 시각적 맥락에만 있기 때문이다.
 */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  "aria-label": label,
}: {
  options: readonly SegmentedOption<T>[];
  value: T;
  onChange: (next: T) => void;
  "aria-label": string;
}) {
  function onKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0) return;

    event.preventDefault();
    const current = options.findIndex((option) => option.value === value);
    const next = options[(current + step + options.length) % options.length];
    if (next) onChange(next.value);
  }

  return (
    <div className={styles.segmented} role="radiogroup" aria-label={label} onKeyDown={onKeyDown}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          tabIndex={option.value === value ? 0 : -1}
          className={styles.segment}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
