"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { consumeReturnTo } from "@/shared/lib/return-to";

/**
 * web-legacy(components/return-to-redirect.tsx)와 같은 계약
 *
 * 백엔드 OAuth 콜백은 항상 공개 기본 URL로 돌아온다(서버 리다이렉트 대상은 이
 * 컴포넌트가 바꾸지 않는다). 로그인 진입 직전 저장해 둔 경로가 있으면 여기서
 * 한 번만 소비해 클라이언트에서 이동시킨다.
 */
export function ReturnToRedirect() {
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const target = consumeReturnTo();
    if (target && target !== pathname) router.replace(target);
  }, [pathname, router]);

  return null;
}
