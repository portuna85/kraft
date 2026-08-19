/**
 * 익명 디바이스 토큰
 *
 * 익명 사용자의 저장 기록은 이 토큰 하나에 매여 있다. **이 모듈은 생성·읽기·쓰기만
 * 한다.** 회전(rotate)은 로그인 시 계정 귀속(claim)이 성공한 경로에서만 일어나야
 * 하므로(불변식 I-2, 레거시 F-P0-10) 그 판단은 Phase 4의 identity-session이 소유한다.
 * 여기에 "편의상" 회전 함수를 두면 언젠가 실패 경로에서 호출되고, 그 순간 그 브라우저의
 * 익명 기록이 영구 고립된다 — 실제로 한 번 일어난 사고다.
 */

export const DEVICE_TOKEN_STORAGE_KEY = "kraft-device-token";
export const DEVICE_TOKEN_HEADER_NAME = "X-Device-Token";

function createToken(): string {
  if (typeof crypto === "undefined") {
    throw new Error("crypto unavailable");
  }
  if (typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** 없으면 만들어 저장한다. localStorage 접근 실패(프라이빗 모드 등)는 null로 흡수한다. */
export function ensureDeviceToken(): string | null {
  if (typeof window === "undefined") return null;

  try {
    const existing = window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY);
    if (existing) return existing;

    const created = createToken();
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, created);
    return created;
  } catch {
    return null;
  }
}

/** 이미 있는 토큰만 읽는다. 조회 목적으로 새 토큰을 만들지 않는다. */
export function readDeviceToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

/**
 * 토큰을 새 값으로 교체한다.
 *
 * **계정 귀속(claim)이 성공한 직후에만 부른다**(불변식 I-2). 회전의 목적은 계정으로
 * 옮겨간 기록에 옛 토큰으로 다시 접근하지 못하게 하는 것이라, 옮기기에 실패한 상태에서
 * 회전하면 옮기지도 못한 기록에 접근 경로만 잃는다 — 익명 저장 기록이 영구 고립된다.
 * 실제로 일어났던 사고이고(레거시 F-P0-10), 그래서 이 함수는 이름부터 조건을 말한다.
 */
export function rotateDeviceTokenAfterSuccessfulClaim(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const rotated = createToken();
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, rotated);
    return rotated;
  } catch {
    return null;
  }
}
