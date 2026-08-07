"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { loginUrl } from "@/lib/community-client";
import { saveReturnTo } from "@/lib/return-to";

// 기존 PostForm·AccountMenu가 쓰던 표기를 그대로 유지한다.
export const PROVIDER_LABELS: Record<"google" | "naver", string> = {
  google: "Google 로그인",
  naver: "Naver 로그인",
};

/**
 * FE-060: 로그인이 필요한 지점마다 안내 문구만 있고 실제 로그인 경로가 없는 곳이 있었다
 * (ReactionBar는 "로그인 후 이용할 수 있습니다."만 띄웠다). PostForm이 이미 쓰던
 * "provider 링크 + 현재 경로 저장" 패턴을 공유해, 어디서 막히든 같은 방식으로 이어지게 한다.
 *
 * `activeProviders`가 비어 있으면(환경에 provider가 없거나 세션을 아직 모르는 경우)
 * 커뮤니티 진입 링크로 폴백한다 — 링크가 하나도 없는 막다른 화면을 만들지 않는다.
 */
export function LoginLinks({
  providers,
  className,
}: {
  providers: readonly ("google" | "naver")[];
  className?: string;
}) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  // L-10: pathname만 저장하면 필터·검색 파라미터(예: /community?category=notice)가
  // 로그인 왕복 후 사라진다 — 되돌아왔을 때 사용자가 보던 화면과 달라진다.
  const query = searchParams.toString();
  const returnPath = query ? `${pathname}?${query}` : pathname;

  if (providers.length === 0) {
    return (
      <a href="/community" className={className ?? "account-login-link"}>
        로그인하러 가기
      </a>
    );
  }

  return (
    <>
      {providers.map((provider) => (
        <a
          key={provider}
          href={loginUrl(provider)}
          className={className ?? "account-login-link"}
          onClick={() => saveReturnTo(returnPath)}
        >
          {PROVIDER_LABELS[provider]}
        </a>
      ))}
    </>
  );
}
