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
  required,
  invalid,
  descriptionId,
  errorMessageId,
}: TextAreaContract) {
  const describedBy = [descriptionId, invalid ? errorMessageId : undefined].filter(Boolean).join(" ") || undefined;

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
        required={required}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
      />
    </div>
  );
}
