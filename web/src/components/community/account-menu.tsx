"use client";

import { useState } from "react";
import { loginUrl, logout } from "@/lib/community-client";
import { useCommunitySession } from "@/components/community/community-session-provider";

export function AccountMenu() {
  const { session, loading } = useCommunitySession();
  const [logoutError, setLogoutError] = useState(false);

  if (loading || !session) {
    return null;
  }

  if (!session.loggedIn) {
    const providers = session.activeProviders;
    if (providers.length === 0) {
      return null;
    }
    const providerLabel = (provider: "google" | "naver") =>
      provider === "google" ? "Google 로그인" : "Naver 로그인";
    return (
      <div className="account-menu">
        {/* 좁은 화면에서는 여러 provider 링크를 나란히 둘 자리가 없어 첫 번째 활성
            provider로 압축한다(NavLinks의 nav-desktop/nav-mobile 이중 렌더링과 같은 CSS 토글 방식). */}
        <a href={loginUrl(providers[0])} className="account-login-link account-login-compact">
          로그인
        </a>
        <span className="account-login-full">
          {providers.map((provider) => (
            <a key={provider} href={loginUrl(provider)} className="account-login-link">
              {providerLabel(provider)}
            </a>
          ))}
        </span>
      </div>
    );
  }

  const handleLogout = async () => {
    setLogoutError(false);
    const ok = await logout();
    if (ok) {
      window.location.reload();
    } else {
      setLogoutError(true);
    }
  };

  return (
    <div className="account-menu">
      <span className="account-nickname account-login-full">{session.nickname}님</span>
      <button type="button" className="account-logout-button" onClick={handleLogout}>
        로그아웃
      </button>
      {logoutError && <p role="alert">로그아웃에 실패했습니다. 다시 시도해 주세요.</p>}
    </div>
  );
}
