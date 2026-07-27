import type { NumberFieldContract } from "./contracts";
import styles from "./field.module.css";

export function NumberField({
  id,
  label,
  value,
  onChange,
  min,
  max,
  step,
  disabled,
  invalid,
  errorMessageId,
}: NumberFieldContract) {
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        id={id}
        type="number"
        className={`${styles.control} ${invalid ? styles.invalid : ""}`}
        value={value ?? ""}
        onChange={(e) => {
          const raw = e.target.value;
          onChange(raw === "" ? null : Number(raw));
        }}
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? errorMessageId : undefined}
      />
    </div>
  );
}
