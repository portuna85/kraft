import type { ButtonHTMLAttributes } from "react";

import styles from "./button.module.css";
import type { ButtonContract, IconButtonContract } from "./contracts";

type NativeProps = Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  "className" | "children" | "disabled"
>;

/**
 * Button — improvement_fe.md §9.3
 *
 * `loading`일 때 `loadingLabel`이 타입 차원에서 필수라, 진행 상태를 시각적으로만
 * 알리는 버튼을 만들 수 없다. 로딩 중에는 disabled도 함께 걸어 중복 제출을 막는다
 * (§16.2) — 두 가지를 호출부가 각각 기억하게 두면 언젠가 한쪽이 빠진다.
 */
export function Button({
  variant = "primary",
  size = "md",
  disabled,
  loading,
  loadingLabel,
  children,
  ...rest
}: ButtonContract & NativeProps) {
  return (
    <button
      type="button"
      {...rest}
      className={`${styles.button} ${styles[variant]} ${styles[size]}`}
      disabled={disabled === true || loading === true}
      aria-busy={loading === true || undefined}
    >
      {loading === true ? (
        <>
          <span className={styles.spinner} aria-hidden="true" />
          <span>{loadingLabel}</span>
        </>
      ) : (
        children
      )}
    </button>
  );
}

/**
 * IconButton — 아이콘만 있는 버튼은 접근 이름이 없으므로 `aria-label`이 필수다.
 *
 * 로딩 중에는 aria-label 자체를 loadingLabel로 바꾼다. sr-only 텍스트를 안에 넣는
 * 방식은 동작하지 않는다 — aria-label이 자식 텍스트를 덮어써서 진행 상태가 영영
 * 읽히지 않는다(테스트가 잡아낸 실제 오작동이다).
 */
export function IconButton({
  variant = "quiet",
  size = "md",
  disabled,
  loading,
  loadingLabel,
  icon,
  "aria-label": label,
  ...rest
}: IconButtonContract & NativeProps) {
  return (
    <button
      type="button"
      {...rest}
      className={`${styles.button} ${styles.iconButton} ${styles[variant]} ${styles[size]}`}
      disabled={disabled === true || loading === true}
      aria-busy={loading === true || undefined}
      aria-label={loading === true ? loadingLabel : label}
    >
      {loading === true ? (
        <span className={styles.spinner} aria-hidden="true" />
      ) : (
        <span aria-hidden="true">{icon}</span>
      )}
    </button>
  );
}
