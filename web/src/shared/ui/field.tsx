import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";

import styles from "./field.module.css";

/**
 * 폼 필드 프리미티브
 *
 * 모든 필드가 라벨 연결·오류 연결을 **컴포넌트 내부에서** 처리한다. 호출부가
 * htmlFor/id/aria-describedby를 손으로 맞추게 두면 언젠가 어긋나고, 그때 오류 메시지는
 * 화면에는 보이지만 스크린리더에는 존재하지 않는 상태가 된다.
 */

type FieldFrameProps = {
  label: string;
  hint?: string;
  error?: string | null;
  children: (ids: { controlId: string; describedBy: string | undefined }) => ReactNode;
};

function FieldFrame({ label, hint, error, children }: FieldFrameProps) {
  const controlId = useId();
  const hintId = `${controlId}-hint`;
  const errorId = `${controlId}-error`;

  const describedBy =
    [error != null && error !== "" ? errorId : null, hint !== undefined ? hintId : null]
      .filter(Boolean)
      .join(" ") || undefined;

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={controlId}>
        {label}
      </label>
      {children({ controlId, describedBy })}
      {hint !== undefined && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
      {/* 오류는 등장 시점에 읽혀야 한다 — 조용히 나타나면 제출이 왜 막혔는지 알 수 없다. */}
      {error != null && error !== "" && (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

type ControlSize = "md" | "lg";

export function TextField({
  label,
  hint,
  error,
  size = "md",
  ...rest
}: {
  label: string;
  hint?: string;
  error?: string | null;
  size?: ControlSize;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "id" | "className" | "size">) {
  const invalid = error != null && error !== "";
  return (
    <FieldFrame label={label} hint={hint} error={error}>
      {({ controlId, describedBy }) => (
        <input
          {...rest}
          id={controlId}
          className={`${styles.control} ${size === "lg" ? styles.lg : ""} ${invalid ? styles.invalid : ""}`}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
        />
      )}
    </FieldFrame>
  );
}

export function TextArea({
  label,
  hint,
  error,
  ...rest
}: {
  label: string;
  hint?: string;
  error?: string | null;
} & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "id" | "className">) {
  const invalid = error != null && error !== "";
  return (
    <FieldFrame label={label} hint={hint} error={error}>
      {({ controlId, describedBy }) => (
        <textarea
          {...rest}
          id={controlId}
          className={`${styles.control} ${styles.textarea} ${invalid ? styles.invalid : ""}`}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
        />
      )}
    </FieldFrame>
  );
}

/** native select를 쓴다 — 모바일에서 OS 선택 UI가 뜨는 것이 커스텀 드롭다운보다 낫다. */
export function Select({
  label,
  hint,
  error,
  children,
  ...rest
}: {
  label: string;
  hint?: string;
  error?: string | null;
  children: ReactNode;
} & Omit<SelectHTMLAttributes<HTMLSelectElement>, "id" | "className">) {
  const invalid = error != null && error !== "";
  return (
    <FieldFrame label={label} hint={hint} error={error}>
      {({ controlId, describedBy }) => (
        <select
          {...rest}
          id={controlId}
          className={`${styles.control} ${invalid ? styles.invalid : ""}`}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
        >
          {children}
        </select>
      )}
    </FieldFrame>
  );
}

/**
 * Checkbox / Radio — 라벨 전체가 히트 영역이다. 작은 사각형만 눌러야 하면 모바일에서
 * 실패율이 높고, 44px 최소 타깃도 지킬 수 없다.
 */
export function Checkbox({
  label,
  ...rest
}: { label: ReactNode } & Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "className">) {
  return (
    <label className={styles.choice}>
      <input {...rest} type="checkbox" className={styles.choiceInput} />
      <span>{label}</span>
    </label>
  );
}

export function Radio({
  label,
  ...rest
}: { label: ReactNode } & Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "className">) {
  return (
    <label className={styles.choice}>
      <input {...rest} type="radio" className={styles.choiceInput} />
      <span>{label}</span>
    </label>
  );
}
