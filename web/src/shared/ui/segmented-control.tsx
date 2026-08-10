"use client";

import { useRef, type KeyboardEvent } from "react";

import styles from "./segmented-control.module.css";

/**
 * SegmentedControl — improvement_fe_codex.md §10.2, §15.2
 *
 * 2~4개 중 정확히 하나를 고르는 용도다(예: `/recommend`의 고정/제외 모드
 * 선택기). native radio 의미를 그대로 흉내낸다 — 화살표 키가 포커스만 옮기지
 * 않고 그 자리에서 선택도 바꾼다(§15.2 "arrow key navigation을 지원하거나
 * native radio semantics 사용"). roving tabindex라 Tab은 그룹에 한 번만 멈춘다.
 */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  "aria-label": label,
}: {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  "aria-label": string;
}) {
  const groupRef = useRef<HTMLDivElement | null>(null);

  function selectAt(index: number) {
    const wrapped = (index + options.length) % options.length;
    const option = options[wrapped];
    if (option === undefined) return;
    onChange(option.value);
    groupRef.current?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[wrapped]?.focus();
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const currentIndex = options.findIndex((option) => option.value === value);
    if (currentIndex === -1) return;

    if (event.key === "ArrowRight" || event.key === "ArrowDown") {
      event.preventDefault();
      selectAt(currentIndex + 1);
    } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
      event.preventDefault();
      selectAt(currentIndex - 1);
    } else if (event.key === "Home") {
      event.preventDefault();
      selectAt(0);
    } else if (event.key === "End") {
      event.preventDefault();
      selectAt(options.length - 1);
    }
  }

  return (
    <div
      ref={groupRef}
      role="radiogroup"
      aria-label={label}
      className={styles.group}
      onKeyDown={onKeyDown}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            className={styles.option}
            data-selected={selected}
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
