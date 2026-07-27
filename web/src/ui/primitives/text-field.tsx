import type { TextFieldContract } from "./contracts";
import styles from "./field.module.css";

export function TextField({
  id,
  label,
  value,
  onChange,
  placeholder,
  disabled,
  required,
  invalid,
  errorMessageId,
}: TextFieldContract) {
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        id={id}
        type="text"
        className={`${styles.control} ${invalid ? styles.invalid : ""}`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        required={required}
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? errorMessageId : undefined}
      />
    </div>
  );
}
