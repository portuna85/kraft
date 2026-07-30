const RETURN_TO_KEY = "kraft-community-return-to";

export function saveReturnTo(pathname: string): void {
  if (!pathname.startsWith("/") || pathname.startsWith("//")) return;
  window.sessionStorage.setItem(RETURN_TO_KEY, pathname);
}

export function consumeReturnTo(): string | null {
  const value = window.sessionStorage.getItem(RETURN_TO_KEY);
  window.sessionStorage.removeItem(RETURN_TO_KEY);
  if (!value || !value.startsWith("/") || value.startsWith("//")) return null;
  return value;
}
