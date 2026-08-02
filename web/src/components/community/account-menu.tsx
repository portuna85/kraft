"use client";

import { useState } from "react";
import { loginUrl, logout, withdraw } from "@/lib/community-client";
import { useCommunitySession } from "@/lib/community-session-provider";

export function AccountMenu() {
  const { session, loading } = useCommunitySession();
  const [logoutError, setLogoutError] = useState(false);
  const [withdrawError, setWithdrawError] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);

  if (loading) {
    return null;
  }

  // KF-05: 공개 페이지에서는 세션을 아예 조회하지 않으므로 session이 null이다 — 실제
  // 로그인 여부를 모르는 채 "확인됨(비로그인)"과 혼동하지 않도록 별도로 일반 진입
  // 링크만 보여준다. 실제 로그인 상태는 /community·/saved 진입 시 확인된다.
  if (session === null) {
    return (
      <div className="account-menu">
        <a href="/community" className="account-login-link">
          로그인
        </a>
      </div>
    );
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
            provider로 압축한다(Header의 desktopOnly/mobileOnly와 같은 CSS 토글 방식). */}
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

  const handleWithdraw = async () => {
    if (!window.confirm(
      "탈퇴하면 닉네임이 가명으로 바뀌고 기존 글·댓글은 그 가명으로 표시됩니다. "
        + "계정과 저장 번호·추천 이력은 삭제되지 않으며, 같은 소셜 계정으로 다시 로그인하면 계정이 다시 활성화됩니다. "
        + "완전한 삭제는 문의하기로 요청해 주세요. 계속할까요?",
    )) {
      return;
    }
    setWithdrawError(false);
    setWithdrawing(true);
    const ok = await withdraw();
    if (ok) {
      window.location.reload();
    } else {
      setWithdrawing(false);
      setWithdrawError(true);
    }
  };

  return (
    <div className="account-menu">
      <span className="account-nickname account-login-full">{session.nickname}님</span>
      <button type="button" className="account-logout-button" onClick={handleLogout}>
        로그아웃
      </button>
      <button
        type="button"
        className="account-withdraw-button"
        disabled={withdrawing}
        onClick={handleWithdraw}
      >
        {withdrawing ? "탈퇴 처리 중…" : "탈퇴"}
      </button>
      {logoutError && <p role="alert">로그아웃에 실패했습니다. 다시 시도해 주세요.</p>}
      {withdrawError && <p role="alert">탈퇴에 실패했습니다. 다시 시도해 주세요.</p>}
    </div>
  );
}
