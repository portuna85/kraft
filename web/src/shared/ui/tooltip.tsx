"use client";

import { useId, type ReactNode } from "react";

import { useDisclosure } from "@/shared/hooks/use-disclosure";

import styles from "./overlay.module.css";

/**
 * Tooltip — improvement_fe.md §9.3
 *
 * **호버 전용은 금지다.** 포커스로도 열려야 키보드·터치 사용자가 내용을 볼 수 있다.
 * 그리고 툴팁에만 있는 정보는 두지 않는다 — 툴팁은 보조 설명이고, 없어도 화면이
 * 이해돼야 한다.
 */
export function Tooltip({ label, children }: { label: string; children: ReactNode }) {
  const { isOpen, open, close } = useDisclosure();
  const tooltipId = useId();

  return (
    <span
      className={styles.tooltipWrap}
      onMouseEnter={open}
      onMouseLeave={close}
      onFocus={open}
      onBlur={close}
    >
      <span aria-describedby={isOpen ? tooltipId : undefined}>{children}</span>
      {isOpen && (
        <span id={tooltipId} role="tooltip" className={styles.tooltip}>
          {label}
        </span>
      )}
    </span>
  );
}
