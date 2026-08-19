"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

/**
 * KF-11(docs/improvement.md): `beforeunload`는 하드 새로고침·탭 닫기만 막는다 —
 * App Router의 `<Link>` 클릭(브레드크럼·탭바·헤더 전부)이나 `router.push`로 가는
 * 인앱 이동은 그 이벤트를 아예 발생시키지 않아 더티 폼 내용이 확인 없이 버려진다.
 *
 * `isDirty`가 true인 동안 문서 전체에 클릭 캡처를 걸어, 좌클릭·수정키 없음·
 * `target="_blank"` 아님·같은 출처(`/`로 시작하는 href) 앵커 클릭을 가로챈다.
 * 취소 버튼처럼 href가 아니라 콜백으로 "나가는" 경로는 `requestLeave(leave)`로
 * 같은 확인 상태를 공유한다. 호출부가 `ConfirmDialog` 등으로 확인을 받은 뒤
 * `confirmLeave()` 또는 `cancelLeave()`를 부른다.
 *
 * **프로그램적 `router.push`(예: 제출 성공 후 이동)는 클릭 이벤트를 발생시키지
 * 않으므로 이 가드에 애초에 걸리지 않는다** — isDirty를 리셋하거나 우회 플래그를
 * 둘 필요가 없다.
 *
 * **브라우저 뒤로가기(popstate)는 다루지 않는다** — App Router의 클라이언트 사이드
 * back/forward는 취소 가능한 표준 이벤트가 없고, 유일한 완화책(더미 history 엔트리
 * push + popstate마다 재-push)은 Next 자체 라우터의 popstate 처리와 경합해 깜빡임·
 * 이중 히스토리를 만들 위험이 크다.
 */
export function useDirtyNavigationGuard(isDirty: boolean) {
  const router = useRouter();
  const [isPromptOpen, setIsPromptOpen] = useState(false);
  const pendingLeaveRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    if (!isDirty) return;

    function onClick(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

      const anchor = (event.target as HTMLElement | null)?.closest("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) return;
      if (anchor.target === "_blank") return;

      const href = anchor.getAttribute("href");
      if (!href || !href.startsWith("/")) return;

      event.preventDefault();
      pendingLeaveRef.current = () => router.push(href);
      setIsPromptOpen(true);
    }

    document.addEventListener("click", onClick);
    return () => document.removeEventListener("click", onClick);
  }, [isDirty, router]);

  function requestLeave(leave: () => void) {
    if (!isDirty) {
      leave();
      return;
    }
    pendingLeaveRef.current = leave;
    setIsPromptOpen(true);
  }

  function confirmLeave() {
    const leave = pendingLeaveRef.current;
    pendingLeaveRef.current = null;
    setIsPromptOpen(false);
    leave?.();
  }

  function cancelLeave() {
    pendingLeaveRef.current = null;
    setIsPromptOpen(false);
  }

  return { isPromptOpen, requestLeave, confirmLeave, cancelLeave };
}
