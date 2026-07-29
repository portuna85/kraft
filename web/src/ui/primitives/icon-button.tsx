import { forwardRef } from "react";
import type { ButtonVariant, IconButtonContract, PrimitiveSize } from "./contracts";
import styles from "./icon-button.module.css";

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: styles.primary,
  secondary: styles.secondary,
  quiet: styles.quiet,
  danger: styles.danger,
};

const SIZE_CLASS: Partial<Record<PrimitiveSize, string>> = {
  sm: styles.sm,
  lg: styles.lg,
};

// forwardRef: Dialog/Drawer의 restoreFocusRef가 트리거 버튼(예: 햄버거 IconButton) 자체를
// 직접 가리켜야 닫힌 뒤 포커스를 정확히 되돌릴 수 있다.
export const IconButton = forwardRef<HTMLButtonElement, IconButtonContract>(function IconButton(
  { "aria-label": ariaLabel, variant, size = "md", disabled = false, icon, onClick },
  ref
) {
  const sizeClass = SIZE_CLASS[size] ?? "";
  return (
    <button
      ref={ref}
      type="button"
      aria-label={ariaLabel}
      className={`${styles.button} ${VARIANT_CLASS[variant]} ${sizeClass}`}
      disabled={disabled}
      onClick={onClick}
    >
      <span aria-hidden="true">{icon}</span>
    </button>
  );
});
