/**
 * M-10: 여러 ThemeToggle 인스턴스(데스크톱 헤더·모바일 드로어)가 각자 로컬 state로
 * 테마를 들고 있어 서로 동기화되지 않았다 — `storage` 이벤트는 다른 탭/문서에서만
 * 발화하므로 같은 문서 안의 다른 인스턴스는 알 방법이 없었다. DOM 테마 자체는
 * 전역이라 시각적으로는 맞았지만, 낡은 인스턴스의 aria-pressed/aria-label이 실제
 * 상태와 어긋났다(이미 다크 모드인데 "다크 모드로 전환"이라고 안내).
 *
 * useSyncExternalStore로 이 document.documentElement.dataset.theme 하나를 단일
 * 진실 공급원으로 구독한다 — 같은 문서 안의 모든 인스턴스가 항상 동기화된다.
 */
const STORAGE_KEY = "kraft-theme";
// 같은 문서 안의 다른 인스턴스에게 알리는 커스텀 이벤트 — storage 이벤트는 다른
// 탭/문서에서만 발화해 이 용도로 쓸 수 없다.
const THEME_CHANGE_EVENT = "kraft-theme-change";

export function readTheme(): boolean {
  if (typeof document === "undefined") return false;
  return document.documentElement.dataset.theme === "dark";
}

// layout.tsx의 인라인 초기화 스크립트(THEME_INIT_SCRIPT)와 서버 렌더 값을 맞춘다 —
// 서버는 항상 false로 렌더링하므로 hydration 시점에도 동일해야 mismatch가 없다.
export function getServerSnapshot(): boolean {
  return false;
}

export function subscribeToTheme(callback: () => void): () => void {
  window.addEventListener("storage", callback);
  window.addEventListener(THEME_CHANGE_EVENT, callback);
  return () => {
    window.removeEventListener("storage", callback);
    window.removeEventListener(THEME_CHANGE_EVENT, callback);
  };
}

export function setTheme(dark: boolean): void {
  if (dark) {
    document.documentElement.setAttribute("data-theme", "dark");
    localStorage.setItem(STORAGE_KEY, "dark");
  } else {
    document.documentElement.removeAttribute("data-theme");
    localStorage.setItem(STORAGE_KEY, "light");
  }
  window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
}
