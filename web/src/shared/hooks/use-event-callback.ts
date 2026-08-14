"use client";

import { useCallback, useEffect, useRef } from "react";

/**
 * 항상 최신 클로저를 호출하는 안정적인 콜백 — TD-013.
 *
 * dialog/drawer/dropdown-menu가 `useEffect` 의존성에서 onClose/close를 빼고
 * `eslint-disable-next-line react-hooks/exhaustive-deps`로 억제해 왔다. `open`이
 * true로 유지되는 동안 effect가 재구독되지 않으므로, 호출부가 그사이 새 콜백
 * 클로저를 넘기면(예: per-item 상태를 캡처한 onClose) Escape가 오래된 콜백을
 * 호출하는 실제 stale-closure 버그가 될 수 있다.
 *
 * ref에 최신 콜백을 담아 두고(매 렌더 갱신), 참조가 절대 바뀌지 않는 래퍼를 돌려준다 —
 * 래퍼를 의존성 배열에 넣어도 재구독을 유발하지 않으면서 항상 최신 콜백을 호출한다.
 * ref 갱신은 useEffect로 하는데, 이 훅을 호출하는 지점이 소비하는 useEffect보다
 * 항상 먼저 호출되므로(같은 컴포넌트 안에서 훅 호출 순서가 effect 실행 순서를
 * 정한다) 소비 effect가 실행되는 시점엔 이미 최신 콜백으로 갱신돼 있다.
 */
export function useEventCallback<A extends unknown[], R>(
  callback: (...args: A) => R,
): (...args: A) => R {
  const callbackRef = useRef(callback);

  useEffect(() => {
    callbackRef.current = callback;
  });

  return useCallback((...args: A) => callbackRef.current(...args), []);
}
