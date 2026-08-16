"use client";

import { useSyncExternalStore } from "react";

import { isInAppBrowser } from "@/shared/lib/in-app-browser";

const noopSubscribe = () => () => {};

/**
 * I-02: `use-media-query.ts`와 같은 이유로 `useSyncExternalStore`를 쓴다 — SSR/최초
 * 렌더에서는 항상 false이고, User-Agent는 마운트 후 바뀌지 않으므로 구독은 no-op이다.
 * 이펙트 안에서 setState를 직접 호출하면 리렌더가 하나 더 생긴다(react-hooks/set-state-in-effect).
 */
export function useIsInAppBrowser(): boolean {
  return useSyncExternalStore(
    noopSubscribe,
    () => isInAppBrowser(navigator.userAgent),
    () => false,
  );
}
