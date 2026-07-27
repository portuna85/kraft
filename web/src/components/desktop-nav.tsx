"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { dataLinks, isCurrent, primaryLinks } from "@/lib/nav-items";
import styles from "./desktop-nav.module.css";

export function DesktopNav() {
  const pathname = usePathname();
  const [dataOpen, setDataOpen] = useState(false);
  const groupRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const dataActive = dataLinks.some((link) => isCurrent(link.href, pathname));

  useEffect(() => {
    if (!dataOpen) return;

    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setDataOpen(false);
        toggleRef.current?.focus();
      }
    };
    const onClickOutside = (event: MouseEvent) => {
      if (!groupRef.current?.contains(event.target as Node)) {
        setDataOpen(false);
      }
    };

    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onClickOutside);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onClickOutside);
    };
  }, [dataOpen]);

  return (
    <nav className={styles.nav} aria-label="주요 메뉴" data-testid="desktop-nav">
      {primaryLinks.map((link) => (
        <Link key={link.href} href={link.href} aria-current={isCurrent(link.href, pathname) ? "page" : undefined}>
          {link.label}
        </Link>
      ))}

      <div
        className={styles.group}
        ref={groupRef}
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            setDataOpen(false);
          }
        }}
      >
        <button
          ref={toggleRef}
          type="button"
          className={`${styles.toggle} ${dataActive ? styles.active : ""}`}
          aria-expanded={dataOpen}
          aria-controls="nav-data-menu"
          aria-current={dataActive ? "page" : undefined}
          onClick={() => setDataOpen((value) => !value)}
        >
          데이터
        </button>
        <div id="nav-data-menu" className={`${styles.menu} ${dataOpen ? styles.open : ""}`}>
          {dataLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              onClick={() => setDataOpen(false)}
              aria-current={isCurrent(link.href, pathname) ? "page" : undefined}
            >
              {link.label}
            </Link>
          ))}
        </div>
      </div>
    </nav>
  );
}
