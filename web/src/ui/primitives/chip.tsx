import type { ChipContract } from "./contracts";
import styles from "./chip.module.css";

export function Chip({ selected = false, disabled = false, onToggle, children }: ChipContract) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      className={`${styles.chip} ${selected ? styles.selected : ""}`}
      onClick={onToggle}
    >
      {children}
    </button>
  );
}
