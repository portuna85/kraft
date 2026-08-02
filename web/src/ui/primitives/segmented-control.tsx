import { useRef, type KeyboardEvent } from "react";
import type { SegmentedControlContract } from "./contracts";
import styles from "./segmented-control.module.css";

export function SegmentedControl<T extends string = string>({
  options,
  value,
  onChange,
  "aria-label": ariaLabel,
}: SegmentedControlContract<T>) {
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const selectableIndexes = options.map((o, i) => (o.disabled ? -1 : i)).filter((i) => i !== -1);

  const moveSelection = (currentIndex: number, direction: 1 | -1) => {
    if (selectableIndexes.length === 0) return;
    const pos = selectableIndexes.indexOf(currentIndex);
    const nextPos = (pos + direction + selectableIndexes.length) % selectableIndexes.length;
    const nextIndex = selectableIndexes[nextPos];
    onChange(options[nextIndex].value);
    optionRefs.current[nextIndex]?.focus();
  };

  const selectIndex = (index: number) => {
    if (index < 0) return;
    onChange(options[index].value);
    optionRefs.current[index]?.focus();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (e.key === "ArrowRight" || e.key === "ArrowDown") {
      e.preventDefault();
      moveSelection(index, 1);
    } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
      e.preventDefault();
      moveSelection(index, -1);
    } else if (e.key === "Home") {
      e.preventDefault();
      selectIndex(selectableIndexes[0] ?? -1);
    } else if (e.key === "End") {
      e.preventDefault();
      selectIndex(selectableIndexes.at(-1) ?? -1);
    }
  };

  return (
    <div className={styles.group} role="radiogroup" aria-label={ariaLabel}>
      {options.map((option, index) => {
        const isSelected = option.value === value;
        return (
          <button
            key={option.value}
            ref={(element) => { optionRefs.current[index] = element; }}
            type="button"
            role="radio"
            aria-checked={isSelected}
            tabIndex={isSelected ? 0 : -1}
            disabled={option.disabled}
            className={`${styles.option} ${isSelected ? styles.selected : ""}`}
            onClick={() => onChange(option.value)}
            onKeyDown={(e) => handleKeyDown(e, index)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
