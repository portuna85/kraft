"use client";

import { useSyncExternalStore } from "react";

// SSR/최초 렌더에서는 항상 false를 반환하고, 마운트 후 실제 뷰포트에 맞춰 갱신한다.
// 이펙트 안에서 setState를 직접 호출하는 대신 useSyncExternalStore로 matchMedia를
// 외부 저장소처럼 구독한다.
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const mql = window.matchMedia(query);
      mql.addEventListener("change", onChange);
      return () => mql.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}
