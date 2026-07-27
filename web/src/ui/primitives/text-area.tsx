import type { TextAreaContract } from "./contracts";
import styles from "./field.module.css";

export function TextArea({
  id,
  label,
  value,
  onChange,
  maxLength,
  rows = 4,
  disabled,
  invalid,
  errorMessageId,
}: TextAreaContract) {
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <textarea
        id={id}
        className={`${styles.control} ${invalid ? styles.invalid : ""}`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        maxLength={maxLength}
        rows={rows}
        disabled={disabled}
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? errorMessageId : undefined}
      />
    </div>
  );
}
