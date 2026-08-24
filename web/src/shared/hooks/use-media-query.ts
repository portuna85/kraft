"use client";

import { useSyncExternalStore } from "react";

/**
 * FE-PERF-02: subscribe/getSnapshot을 훅 본문에서 인라인 화살표로 만들면 매 렌더 새
 * 함수 정체성이 생겨 useSyncExternalStore가 구독을 매번 해제·재등록한다(소비자가
 * 리렌더될 때마다). 쿼리 문자열별로 함수 정체성을 모듈 스코프에 고정해 재구독을 막는다
 * — theme-toggle.tsx가 이미 쓰는 모듈 스코프 패턴과 같되, 여기서는 쿼리 문자열이
 * 가변이라 캐시로 관리한다.
 */
const entryCache = new Map<
  string,
  { subscribe: (onChange: () => void) => () => void; getSnapshot: () => boolean }
>();

function getEntry(query: string) {
  let entry = entryCache.get(query);
  if (entry === undefined) {
    entry = {
      subscribe: (onChange: () => void) => {
        const mql = window.matchMedia(query);
        mql.addEventListener("change", onChange);
        return () => mql.removeEventListener("change", onChange);
      },
      getSnapshot: () => window.matchMedia(query).matches,
    };
    entryCache.set(query, entry);
  }
  return entry;
}

/**
 * 미디어쿼리 구독
 *
 * SSR/최초 렌더에서는 항상 false를 반환하고, 마운트 후 실제 뷰포트에 맞춰 갱신한다.
 * 이펙트 안에서 setState를 직접 호출하는 대신 useSyncExternalStore로 matchMedia를
 * 외부 저장소처럼 구독한다.
 */
export function useMediaQuery(query: string): boolean {
  const entry = getEntry(query);
  return useSyncExternalStore(entry.subscribe, entry.getSnapshot, () => false);
}
