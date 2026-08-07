import type { TextFieldContract } from "./contracts";
import styles from "./field.module.css";

export function TextField({
  id,
  label,
  value,
  onChange,
  placeholder,
  maxLength,
  name,
  autoComplete,
  inputMode,
  disabled,
  required,
  invalid,
  descriptionId,
  errorMessageId,
}: TextFieldContract) {
  const describedBy = [descriptionId, invalid ? errorMessageId : undefined].filter(Boolean).join(" ") || undefined;

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
        maxLength={maxLength}
        name={name}
        autoComplete={autoComplete}
        inputMode={inputMode}
        disabled={disabled}
        required={required}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
      />
    </div>
  );
}
