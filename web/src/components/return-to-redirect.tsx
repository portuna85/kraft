"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { consumeReturnTo } from "@/lib/return-to";

// 커뮤니티 로그인은 OAuth 왕복 후 항상 공개 기본 URL 루트로 돌아온다(백엔드
// CommunitySecurityConfig의 redirectTarget()). 로그인 전 있던 페이지로 되돌리는 것은
// 서버 리다이렉트 대상을 바꾸지 않고, 로그인 진입 직전 저장해 둔 경로를 여기서 소비해
// 클라이언트에서 한 번만 이동시킨다.
export function ReturnToRedirect() {
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const target = consumeReturnTo();
    if (target && target !== pathname) router.replace(target);
  }, [pathname, router]);

  return null;
}
