"use client";

import { useEffect, useRef, type KeyboardEvent, type ReactNode } from "react";

import { useDisclosure } from "@/shared/hooks/use-disclosure";
import { useEventCallback } from "@/shared/hooks/use-event-callback";

import styles from "./overlay.module.css";

export type MenuItem = {
  label: string;
  /** I-33: 되돌릴 수 없는 파괴적 항목(회원 탈퇴 등) 시각 구분용. */
  tone?: "danger";
  /** I-33: 이 항목 바로 위에 구분선을 그린다 — 파괴적 항목을 일상 항목과 갈라 둔다. */
  separatorBefore?: boolean;
} & ({ onSelect: () => void } | { href: string; onClick?: () => void });

/**
 * DropdownMenu
 *
 * `role="menu"` + 화살표/Home/End/Esc. 링크 목록으로 만들면 스크린리더 사용자에게
 * "메뉴가 열렸다"는 사실과 항목 수가 전달되지 않는다.
 *
 * 바깥 클릭으로 닫는 리스너는 열려 있을 때만 붙인다 — 항상 붙여두면 페이지의 모든
 * 클릭이 닫힌 메뉴 수만큼 핸들러를 거친다.
 */
export function DropdownMenu({
  trigger,
  items,
  "aria-label": label,
}: {
  trigger: (props: { onClick: () => void; "aria-expanded": boolean }) => ReactNode;
  items: readonly MenuItem[];
  "aria-label": string;
}) {
  const { isOpen, close, toggle } = useDisclosure();
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  // TD-013: close는 useDisclosure가 useCallback([])으로 이미 안정적이지만, 그 사실은
  // useDisclosure의 구현 세부사항이라 이 컴포넌트가 암묵적으로 의존하고 있었다 —
  // useEventCallback으로 감싸 이 컴포넌트 자체의 안전성이 되게 만든다.
  const stableClose = useEventCallback(close);

  useEffect(() => {
    if (!isOpen) return;

    menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();

    function onPointerDown(event: PointerEvent) {
      if (!wrapRef.current?.contains(event.target as Node)) stableClose();
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [isOpen, stableClose]);

  function onMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      close();
      return;
    }

    const focusables = Array.from(
      menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [],
    );
    if (focusables.length === 0) return;

    const current = focusables.indexOf(document.activeElement as HTMLElement);
    const moves: Record<string, number> = { ArrowDown: 1, ArrowUp: -1 };
    const step = moves[event.key];

    if (event.key === "Home" || event.key === "End") {
      event.preventDefault();
      (event.key === "Home" ? focusables[0] : focusables[focusables.length - 1])?.focus();
      return;
    }

    if (step === undefined) return;
    event.preventDefault();
    focusables[(current + step + focusables.length) % focusables.length]?.focus();
  }

  return (
    <div className={styles.menuWrap} ref={wrapRef}>
      {trigger({ onClick: toggle, "aria-expanded": isOpen })}

      {isOpen && (
        <div
          ref={menuRef}
          className={styles.menu}
          role="menu"
          aria-label={label}
          onKeyDown={onMenuKeyDown}
        >
          {items.map((item) => (
            <div key={item.label}>
              {item.separatorBefore === true && (
                <hr className={styles.menuSeparator} role="separator" aria-orientation="horizontal" />
              )}
              {"href" in item ? (
                // OAuth 등 전체 페이지 이동이 필요한 항목 — onClick으로 fetch하면 안 된다.
                // onClick은 이동 자체가 아니라 이동 전 부수 작업(예: 복귀 경로 저장)에만 쓴다.
                <a
                  role="menuitem"
                  className={`${styles.menuItem} ${item.tone === "danger" ? styles.menuItemDanger : ""}`}
                  href={item.href}
                  onClick={item.onClick}
                >
                  {item.label}
                </a>
              ) : (
                <button
                  type="button"
                  role="menuitem"
                  className={`${styles.menuItem} ${item.tone === "danger" ? styles.menuItemDanger : ""}`}
                  onClick={() => {
                    close();
                    item.onSelect();
                  }}
                >
                  {item.label}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
