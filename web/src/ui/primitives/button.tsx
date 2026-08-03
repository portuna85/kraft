import { forwardRef } from "react";
import type { ButtonContract, ButtonVariant, PrimitiveSize } from "./contracts";
import styles from "./button.module.css";

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

export const Button = forwardRef<HTMLButtonElement, ButtonContract>(function Button(
  {
    variant,
    size = "md",
    disabled = false,
    loading = false,
    loadingLabel,
    type = "button",
    onClick,
    children,
    className,
    ...rest
  },
  ref
) {
  const sizeClass = SIZE_CLASS[size] ?? "";
  return (
    <button
      ref={ref}
      type={type}
      className={[styles.button, VARIANT_CLASS[variant], sizeClass, className].filter(Boolean).join(" ")}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      onClick={onClick}
      {...rest}
    >
      {loading && <span className={styles.spinner} aria-hidden="true" />}
      <span aria-hidden={loading || undefined}>{children}</span>
      {loading && loadingLabel && <span className={styles.hiddenLabel}>{loadingLabel}</span>}
    </button>
  );
});
