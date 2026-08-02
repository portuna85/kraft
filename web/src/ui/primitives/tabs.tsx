import { useRef, type KeyboardEvent } from "react";
import type { TabsContract } from "./contracts";
import styles from "./tabs.module.css";

export function Tabs<T extends string = string>({ items, value, onChange, panelIdPrefix }: TabsContract<T>) {
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const selectableIndexes = items.map((item, i) => (item.disabled ? -1 : i)).filter((i) => i !== -1);

  const moveSelection = (currentIndex: number, direction: 1 | -1) => {
    if (selectableIndexes.length === 0) return;
    const pos = selectableIndexes.indexOf(currentIndex);
    const nextPos = (pos + direction + selectableIndexes.length) % selectableIndexes.length;
    const nextIndex = selectableIndexes[nextPos];
    onChange(items[nextIndex].value);
    tabRefs.current[nextIndex]?.focus();
  };

  const selectIndex = (index: number) => {
    if (index < 0) return;
    onChange(items[index].value);
    tabRefs.current[index]?.focus();
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
    <div className={styles.tablist} role="tablist">
      {items.map((item, index) => {
        const isSelected = item.value === value;
        return (
          <button
            key={item.value}
            ref={(element) => { tabRefs.current[index] = element; }}
            type="button"
            role="tab"
            id={`${panelIdPrefix}-tab-${item.value}`}
            aria-selected={isSelected}
            aria-controls={`${panelIdPrefix}-panel-${item.value}`}
            tabIndex={isSelected ? 0 : -1}
            disabled={item.disabled}
            className={`${styles.tab} ${isSelected ? styles.selected : ""}`}
            onClick={() => onChange(item.value)}
            onKeyDown={(e) => handleKeyDown(e, index)}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
