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
 *
 * I-03: 로그인 직전 경로가 지금 있는 경로(예: `/`에서 로그인)와 같으면 이동은
 * 필요 없지만, 서버 컴포넌트가 방금 심어진 kraft_logged_in 쿠키를 반영해 다시
 * 렌더링되도록 router.refresh()는 호출한다 — 안 하면 로그인 성공 화면이 직전의
 * "로그인" 버튼 그대로 남는다.
 */
export function ReturnToRedirect() {
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const target = consumeReturnTo();
    if (!target) return;
    if (target !== pathname) {
      router.replace(target);
    } else {
      router.refresh();
    }
  }, [pathname, router]);

  return null;
}
