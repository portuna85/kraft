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

export function IconButton({
  "aria-label": ariaLabel,
  variant,
  size = "md",
  disabled = false,
  icon,
  onClick,
}: IconButtonContract) {
  const sizeClass = SIZE_CLASS[size] ?? "";
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      className={`${styles.button} ${VARIANT_CLASS[variant]} ${sizeClass}`}
      disabled={disabled}
      onClick={onClick}
    >
      <span aria-hidden="true">{icon}</span>
    </button>
  );
}
