"use client";

import { useSyncExternalStore } from "react";

import { applyTheme, THEME_CHANGE_EVENT, type Theme } from "@/shared/lib/theme";
import { IconButton } from "@/shared/ui/button";

import styles from "./shell.module.css";

function subscribe(onChange: () => void) {
  window.addEventListener(THEME_CHANGE_EVENT, onChange);
  return () => window.removeEventListener(THEME_CHANGE_EVENT, onChange);
}

function getSnapshot(): Theme {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

/**
 * I-33: 다크 모드는 이미 완비돼 있었지만 전환할 방법이 OS 설정뿐이었다 — 이미
 * `theme.ts`가 갖고 있는 저장·적용 로직에 토글 버튼만 얹는다.
 *
 * SSR/최초 렌더에서는 실제 테마를 모른다(`<head>` 인라인 스크립트가 하이드레이션
 * 전에 `data-theme`를 정하므로 서버는 알 수 없다) — use-media-query.ts와 같은
 * 패턴으로 useSyncExternalStore를 써서 서버 스냅샷(null)과 클라이언트 스냅샷을
 * 갈라 하이드레이션 경고 없이 마운트 후 갱신되게 한다.
 */
export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, getSnapshot, () => null);

  if (theme === null) {
    return <span className={styles.themeTogglePlaceholder} aria-hidden="true" />;
  }

  function toggle() {
    applyTheme(theme === "dark" ? "light" : "dark");
  }

  return (
    <IconButton
      aria-label={theme === "dark" ? "라이트 모드로 전환" : "다크 모드로 전환"}
      icon={<span aria-hidden="true">{theme === "dark" ? "☀️" : "🌙"}</span>}
      onClick={toggle}
    />
  );
}
