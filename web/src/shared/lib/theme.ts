/**
 * 테마 (다크/라이트 + 하이드레이션 전 적용)
 *
 * 테마는 하이드레이션 **전에** 적용돼야 한다. React가 붙은 뒤에 적용하면 첫 페인트가
 * 라이트로 나갔다가 다크로 뒤집히는 번쩍임이 생긴다. 그래서 아래 스크립트를 <head>에
 * 인라인으로 두고, nonce를 붙여 CSP를 통과시킨다.
 */

export const THEME_STORAGE_KEY = "kraft-theme";

export type Theme = "light" | "dark";

export function isTheme(value: unknown): value is Theme {
  return value === "light" || value === "dark";
}

/**
 * 저장된 선택이 없으면 OS 설정을 따른다. 사이트 테마는 토글식이라 OS와 완전히 일치하는
 * 것이 목표가 아니라 "첫 인상만 어긋나지 않게" 하는 것이 목표다(레거시 R-23).
 */
export const THEME_INIT_SCRIPT = `
(function () {
  try {
    var stored = localStorage.getItem("${THEME_STORAGE_KEY}");
    var theme = stored === "light" || stored === "dark"
      ? stored
      : (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    document.documentElement.dataset.theme = theme;
  } catch (e) {
    document.documentElement.dataset.theme = "light";
  }
})();
`.trim();

export function readStoredTheme(): Theme | null {
  if (typeof window === "undefined") return null;
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isTheme(stored) ? stored : null;
  } catch {
    return null;
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // 프라이빗 모드 등 저장 실패는 무시한다 — 이번 세션 동안은 적용된 상태로 남는다.
  }
}
