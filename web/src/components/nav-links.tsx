"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { BP } from "@/lib/breakpoints";

const primaryLinks = [
  { href: "/", label: "홈" },
  { href: "/recommend", label: "번호 추천" },
  { href: "/saved", label: "저장 번호" },
  { href: "/community", label: "커뮤니티" },
];

// 통계 3종 + 번호 분석은 데스크톱에서 "통계" 드롭다운으로 묶는다. 발견성을 위해
// 모바일 전체 메뉴에는 그대로 펼쳐서 노출하고, 링크는 항상 실제 <a href>로
// 렌더링해 드롭다운이 닫혀 있어도 크롤 가능성을 유지한다.
const statsLinks = [
  { href: "/frequency", label: "출현 통계" },
  { href: "/stats", label: "패턴 통계" },
  { href: "/companion", label: "동반 출현" },
  { href: "/analysis", label: "번호 분석" },
];

const allLinks = [...primaryLinks, ...statsLinks];

function isCurrent(href: string, pathname: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function NavLinks() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [statsOpen, setStatsOpen] = useState(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const mobileNavRef = useRef<HTMLElement>(null);
  const statsGroupRef = useRef<HTMLDivElement>(null);
  const statsToggleRef = useRef<HTMLButtonElement>(null);
  const statsActive = statsLinks.some((link) => isCurrent(link.href, pathname));

  const closeAndReturnFocus = () => {
    setOpen(false);
    toggleRef.current?.focus();
  };

  useEffect(() => {
    if (!statsOpen) return;

    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setStatsOpen(false);
        statsToggleRef.current?.focus();
      }
    };
    const onClickOutside = (event: MouseEvent) => {
      if (!statsGroupRef.current?.contains(event.target as Node)) {
        setStatsOpen(false);
      }
    };

    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onClickOutside);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onClickOutside);
    };
  }, [statsOpen]);

  useEffect(() => {
    if (!open) return;

    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        closeAndReturnFocus();
        return;
      }
      if (event.key !== "Tab" || !mobileNavRef.current) return;

      const focusable = mobileNavRef.current.querySelectorAll<HTMLElement>(
        "a[href], button:not([disabled])"
      );
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  useEffect(() => {
    const main = document.getElementById("main-content");
    const footer = document.querySelector("footer");

    if (!open) {
      document.body.style.overflow = "";
      main?.removeAttribute("inert");
      footer?.removeAttribute("inert");
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    // R-42: 드로어가 열린 동안 스크린리더 가상 커서가 뒤의 본문을 계속 탐색하지
    // 못하도록 배경 콘텐츠를 inert 처리한다(dialog 패턴의 일부).
    main?.setAttribute("inert", "");
    footer?.setAttribute("inert", "");
    mobileNavRef.current?.querySelector<HTMLElement>("a[href]")?.focus();

    return () => {
      document.body.style.overflow = previousOverflow;
      main?.removeAttribute("inert");
      footer?.removeAttribute("inert");
    };
  }, [open]);

  useEffect(() => {
    const mq = window.matchMedia(`(min-width: ${BP.desktop}px)`);
    const onResize = (e: MediaQueryListEvent) => {
      if (e.matches) setOpen(false);
    };
    mq.addEventListener("change", onResize);
    return () => mq.removeEventListener("change", onResize);
  }, []);

  const desktopItems = primaryLinks.map((link) => (
    <Link
      key={link.href}
      href={link.href}
      aria-current={isCurrent(link.href, pathname) ? "page" : undefined}
    >
      {link.label}
    </Link>
  ));

  const mobileItems = allLinks.map((link) => (
    <Link
      key={link.href}
      href={link.href}
      onClick={() => setOpen(false)}
      aria-current={isCurrent(link.href, pathname) ? "page" : undefined}
    >
      {link.label}
    </Link>
  ));

  return (
    <>
      <nav className="nav nav-desktop" aria-label="주요 메뉴">
        {desktopItems}

        <div
          className="nav-stats-group"
          ref={statsGroupRef}
          onBlur={(event) => {
            // R-25: Tab으로 그룹 밖으로 포커스가 이동하면(마우스 클릭·Escape 외의 경로)
            // 열린 채 남지 않도록 닫는다.
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
              setStatsOpen(false);
            }
          }}
        >
          <button
            ref={statsToggleRef}
            type="button"
            className={`nav-stats-toggle${statsActive ? " active" : ""}`}
            aria-expanded={statsOpen}
            aria-controls="nav-stats-menu"
            aria-current={statsActive ? "page" : undefined}
            onClick={() => setStatsOpen((value) => !value)}
          >
            통계
          </button>
          <div id="nav-stats-menu" className={`nav-stats-menu${statsOpen ? " open" : ""}`}>
            {statsLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                onClick={() => setStatsOpen(false)}
                aria-current={isCurrent(link.href, pathname) ? "page" : undefined}
              >
                {link.label}
              </Link>
            ))}
          </div>
        </div>
      </nav>

      <button
        ref={toggleRef}
        type="button"
        className="nav-toggle"
        onClick={() => setOpen((value) => !value)}
        aria-label={open ? "메뉴 닫기" : "메뉴 열기"}
        aria-expanded={open}
        aria-controls="nav-mobile"
      >
        <span className={`hamburger${open ? " open" : ""}`} />
      </button>

      {open && (
        <>
          <div className="nav-backdrop" aria-hidden="true" onClick={closeAndReturnFocus} />
          <div className="nav-mobile-wrap" onClick={closeAndReturnFocus}>
            <nav
              id="nav-mobile"
              ref={mobileNavRef}
              className="nav-mobile"
              aria-label="주요 메뉴"
              role="dialog"
              aria-modal="true"
            >
              {mobileItems}
            </nav>
          </div>
        </>
      )}
    </>
  );
}
