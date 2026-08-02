"use client";

import { useState } from "react";
import { loginUrl, logout, withdraw } from "@/lib/community-client";
import { useCommunitySession } from "@/lib/community-session-provider";
// next/dynamic으로 지연 로딩도 시도했으나 로더 오버헤드가 절감분보다 커
// 전 라우트 번들이 오히려 늘었다(홈 64.1 → 65.5KB). 정적 import를 유지한다.
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";

export function AccountMenu() {
  const { session, loading, error: sessionError, retry } = useCommunitySession();
  const [logoutError, setLogoutError] = useState(false);
  const [withdrawError, setWithdrawError] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

  if (loading) {
    return null;
  }

  // KF-05: 공개 페이지에서는 세션을 아예 조회하지 않으므로 session이 null이다 — 실제
  // 로그인 여부를 모르는 채 "확인됨(비로그인)"과 혼동하지 않도록 별도로 일반 진입
  // 링크만 보여준다. 실제 로그인 상태는 /community·/saved 진입 시 확인된다.
  if (sessionError) {
    return (
      <div className="account-menu" role="alert">
        <span>세션을 확인하지 못했습니다.</span>
        <button type="button" className="account-login-link" onClick={retry}>
          다시 시도
        </button>
      </div>
    );
  }

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

  // FE-003: window.confirm은 긴 안내를 읽기 어렵게 붙여 놓을 수밖에 없었다.
  // 다이얼로그에서는 제목과 설명을 나눠 보여주고 실패도 그 안에서 알린다.
  const confirmWithdraw = async () => {
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
        onClick={() => setWithdrawOpen(true)}
      >
        {withdrawing ? "탈퇴 처리 중…" : "탈퇴"}
      </button>
      {logoutError && <p role="alert">로그아웃에 실패했습니다. 다시 시도해 주세요.</p>}
      <ConfirmDialog
        open={withdrawOpen}
        title="정말 탈퇴할까요?"
        description={
          "탈퇴하면 계정, 저장 번호, 추천 이력, 기기 귀속과 커뮤니티 상호작용 기록이 삭제됩니다. "
          + "기존 글·댓글은 다른 이용자의 대화 맥락을 보호하기 위해 내용과 작성자 정보가 제거된 삭제 표시로만 남습니다. "
          + "같은 소셜 계정으로 다시 로그인하면 새 계정이 만들어집니다."
        }
        confirmLabel="탈퇴"
        pending={withdrawing}
        errorMessage={withdrawError ? "탈퇴에 실패했습니다. 다시 시도해 주세요." : undefined}
        onConfirm={confirmWithdraw}
        onCancel={() => {
          setWithdrawOpen(false);
          setWithdrawError(false);
        }}
      />
    </div>
  );
}
