/**
 * 테마 (다크/라이트 + 하이드레이션 전 적용)
 *
 * 테마는 하이드레이션 **전에** 적용돼야 한다. React가 붙은 뒤에 적용하면 첫 페인트가
 * 라이트로 나갔다가 다크로 뒤집히는 번쩍임이 생긴다. 그래서 아래 스크립트를 <head>에
 * 인라인으로 두고, nonce를 붙여 CSP를 통과시킨다.
 */

export const THEME_STORAGE_KEY = "kraft-theme";
/** I-33: 토글 버튼이 useSyncExternalStore로 구독할 변경 이벤트 이름. */
export const THEME_CHANGE_EVENT = "kraft-theme-change";

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

/**
 * layout.tsx의 `viewport.themeColor`와 같은 색이다. 그 정적 선언은
 * `prefers-color-scheme` 미디어쿼리로만 갈리므로, OS는 라이트인데 사용자가
 * 여기서 다크를 고르면(또는 그 반대) 브라우저 상단 바/상태 표시줄 색이
 * 실제 페이지 테마와 어긋난다. 두 `<meta name="theme-color">` 모두의
 * content를 덮어써 어느 쪽이 매칭되든 현재 테마 색이 나오게 한다.
 */
const THEME_COLOR: Record<Theme, string> = {
  light: "#f3f7ff",
  dark: "#050816",
};

function syncThemeColorMeta(theme: Theme): void {
  const color = THEME_COLOR[theme];
  document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => {
    meta.setAttribute("content", color);
  });
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  syncThemeColorMeta(theme);
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // 프라이빗 모드 등 저장 실패는 무시한다 — 이번 세션 동안은 적용된 상태로 남는다.
  }
  window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
}
