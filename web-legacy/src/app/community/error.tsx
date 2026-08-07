"use client";

import { RouteError } from "@/components/route-error";

// M-3: 세그먼트 error.tsx가 없으면 getCommunityPosts 실패가 최상위 error.tsx까지
// 전파돼야만 잡힌다. 세그먼트 경계를 두면 실패 지점이 더 가까운 곳에서 잡힌다.
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return <RouteError error={error} reset={reset} />;
}
