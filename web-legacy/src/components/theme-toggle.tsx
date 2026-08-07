"use client";

import { useSyncExternalStore } from "react";
import { getServerSnapshot, readTheme, setTheme, subscribeToTheme } from "@/lib/theme-store";

export function ThemeToggle() {
  // M-10: useSyncExternalStore가 SSR/hydration 시점에는 getServerSnapshot(항상 false —
  // 인라인 초기화 스크립트가 반영되기 전 서버 렌더와 동일)을, 그 이후에는 실제 DOM
  // 테마를 반환한다. 같은 문서의 다른 ThemeToggle 인스턴스가 테마를 바꾸면 이 구독을
  // 통해 즉시 갱신된다 — 예전에는 window "storage"만 들어 다른 탭에서만 반응했다.
  const isDark = useSyncExternalStore(subscribeToTheme, readTheme, getServerSnapshot);

  function toggle() {
    setTheme(!isDark);
  }

  return (
    <button
      type="button"
      onClick={toggle}
      className="theme-toggle"
      aria-label={isDark ? "라이트 모드로 전환" : "다크 모드로 전환"}
      aria-pressed={isDark}
    >
      {isDark ? (
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
          <path d="M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.4 5.4 0 0 1-7.54-7.54A9 9 0 0 0 12 3Z" />
        </svg>
      ) : (
        <svg
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
      )}
    </button>
  );
}
