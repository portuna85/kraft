const STORAGE_KEY = "kraft-device-token";

function createRandomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function generateToken(): string {
  return typeof crypto.randomUUID === "function" ? crypto.randomUUID() : createRandomToken();
}

export function getDeviceToken(): string {
  if (typeof window === "undefined") return "";
  const existing = window.localStorage.getItem(STORAGE_KEY);
  if (existing) return existing;
  const created = generateToken();
  window.localStorage.setItem(STORAGE_KEY, created);
  return created;
}

// 로그인 계정 귀속(Phase 4) 성공 직후 호출한다 — 이전 토큰이 가리키던 기록은 이미 계정으로
// 옮겨졌으므로(서버가 client_token_hash를 null로 비움), 공유 기기에서 다음 익명 사용자가
// 이전 계정의 기록을 이어받지 않도록 새 토큰을 발급한다(문서 10.2 8단계).
export function rotateDeviceToken(): string {
  if (typeof window === "undefined") return "";
  const created = generateToken();
  window.localStorage.setItem(STORAGE_KEY, created);
  return created;
}
