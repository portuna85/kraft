/**
 * 탭 세션당 claim 1회
 *
 * sessionStorage를 쓰는 이유가 있다. localStorage면 브라우저를 다시 열어도 플래그가
 * 남아 재시도 기회가 영영 사라지고, 메모리면 라우트 이동마다 다시 시도한다.
 * 탭 하나의 수명이 "한 번은 시도하되 무한히 반복하지 않는" 적절한 경계다.
 *
 * **플래그는 claim이 성공했을 때만 기록한다.** 실패에도 기록하면 그 탭에서는 두 번 다시
 * 시도하지 않아, 일시적 네트워크 오류 하나로 익명 기록이 계정에 합쳐지지 못한 채 남는다.
 */
const CLAIM_ATTEMPTED_KEY = "kraft-device-claim-attempted";

export function hasClaimSettled(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return window.sessionStorage.getItem(CLAIM_ATTEMPTED_KEY) === "true";
  } catch {
    // 저장소를 못 쓰면 "아직 안 했다"로 본다 — 중복 시도는 백엔드가 멱등 처리한다.
    return false;
  }
}

export function markClaimSettled(): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(CLAIM_ATTEMPTED_KEY, "true");
  } catch {
    // 기록 실패는 무시한다. 최악의 경우 이 탭에서 한 번 더 시도할 뿐이다.
  }
}

/** 테스트 격리용. */
export function clearClaimFlagForTests(): void {
  try {
    window.sessionStorage.removeItem(CLAIM_ATTEMPTED_KEY);
  } catch {
    // 무시
  }
}
