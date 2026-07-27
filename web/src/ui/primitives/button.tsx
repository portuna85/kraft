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

export function Button({
  variant,
  size = "md",
  disabled = false,
  loading = false,
  loadingLabel,
  type = "button",
  onClick,
  children,
}: ButtonContract) {
  const sizeClass = SIZE_CLASS[size] ?? "";
  return (
    <button
      type={type}
      className={`${styles.button} ${VARIANT_CLASS[variant]} ${sizeClass}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      onClick={onClick}
    >
      {loading && <span className={styles.spinner} aria-hidden="true" />}
      <span aria-hidden={loading || undefined}>{children}</span>
      {loading && loadingLabel && <span className={styles.hiddenLabel}>{loadingLabel}</span>}
    </button>
  );
}
