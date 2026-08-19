"use client";

import { useEffect } from "react";

/**
 * 배경 스크롤 잠금
 *
 * I-26: Dialog에 처음 있던 문제 — 열려 있는 동안 배경 스크롤이 잠기지 않아, 백드롭
 * 위에서 휠을 굴리면 뒷 페이지가 스크롤됐다. Drawer는 포커스 트랩·Escape는 있었지만
 * 이 잠금이 아예 없었다 — 오버레이마다 따로 구현하면 하나씩 빠뜨리기 쉬우므로 공유
 * 훅으로 뽑는다.
 *
 * iOS Safari의 탄성(rubber-band) 오버스크롤은 `overflow: hidden`만으로는 완전히
 * 막히지 않는 알려진 한계다 — `position: fixed` 기반 우회는 이번 범위에 넣지 않는다.
 *
 * KF-17(docs/improvement.md): 오버레이 2개가 동시에 열리면(예: comment-section.tsx의
 * ReportDialog + ConfirmDialog) 먼저 닫히는 쪽이 아직 열린 쪽의 잠금을 풀어버렸다 —
 * 각 인스턴스가 독립적으로 overflow를 캡처/복원했기 때문이다. 모듈 스코프 참조
 * 카운터로 0→1일 때만 잠그고 1→0일 때만 복원한다.
 */
let lockCount = 0;
let previousOverflow = "";

function lock() {
  if (lockCount === 0) {
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
  }
  lockCount += 1;
}

function unlock() {
  lockCount = Math.max(0, lockCount - 1);
  if (lockCount === 0) {
    document.body.style.overflow = previousOverflow;
  }
}

export function useScrollLock(active: boolean) {
  useEffect(() => {
    if (!active) return;
    lock();
    return unlock;
  }, [active]);
}

/** 테스트 격리 전용 — 모듈 스코프 카운터가 테스트 파일 간에 새지 않게 리셋한다. */
export function __resetScrollLockForTests() {
  lockCount = 0;
  previousOverflow = "";
  document.body.style.overflow = "";
}
