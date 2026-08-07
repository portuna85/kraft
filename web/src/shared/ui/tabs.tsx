"use client";

import { useId, type KeyboardEvent, type ReactNode } from "react";

import styles from "./surface.module.css";

export type TabItem<T extends string> = { value: T; label: string; panel: ReactNode };

/**
 * Tabs — improvement_fe.md §9.3
 *
 * 화살표로 이동하고 탭 목록 전체가 탭 정지점 하나다. 각 탭과 패널을
 * aria-controls/aria-labelledby로 연결한다 — 연결이 없으면 스크린리더 사용자는
 * 탭을 바꿔도 어느 내용이 바뀌었는지 알 수 없다.
 *
 * 값을 URL에 둘지 여부는 호출부가 정한다. 목록 필터처럼 공유돼야 하는 상태라면
 * URL이 단일 진실 공급원이어야 한다(§14.2).
 */
export function Tabs<T extends string>({
  items,
  value,
  onChange,
  "aria-label": label,
}: {
  items: readonly TabItem<T>[];
  value: T;
  onChange: (next: T) => void;
  "aria-label": string;
}) {
  const baseId = useId();

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0) return;

    event.preventDefault();
    const current = items.findIndex((item) => item.value === value);
    const next = items[(current + step + items.length) % items.length];
    if (next) onChange(next.value);
  }

  const active = items.find((item) => item.value === value) ?? items[0];

  return (
    <div>
      <div className={styles.tabList} role="tablist" aria-label={label} onKeyDown={onKeyDown}>
        {items.map((item) => (
          <button
            key={item.value}
            type="button"
            role="tab"
            id={`${baseId}-tab-${item.value}`}
            aria-selected={item.value === value}
            aria-controls={`${baseId}-panel-${item.value}`}
            tabIndex={item.value === value ? 0 : -1}
            className={styles.tab}
            onClick={() => onChange(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {active !== undefined && (
        <div
          role="tabpanel"
          id={`${baseId}-panel-${active.value}`}
          aria-labelledby={`${baseId}-tab-${active.value}`}
          tabIndex={0}
        >
          {active.panel}
        </div>
      )}
    </div>
  );
}
