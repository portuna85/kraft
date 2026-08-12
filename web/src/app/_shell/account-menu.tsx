"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import { logout, withdraw } from "@/entities/user-session/api";
import { DropdownMenu } from "@/shared/ui/dropdown-menu";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { InlineAlert } from "@/shared/ui/states";

/**
 * 로그인 상태 계정 메뉴 — improvement_fe_codex.md §4.4 ("Signed in: avatar/initial + menu")
 *
 * `(session)` 셸에서만 렌더된다 — 세션을 아는 곳이 여기뿐이다(불변식 I-4).
 *
 * H-02: 회원 탈퇴는 되돌릴 수 없는 동작이라 ConfirmDialog(레거시 FE-003과 같은
 * 단일 확인 경로 원칙, `post-owner-actions.tsx`가 파괴적 액션에 쓰는 것과 동일한
 * 컴포넌트)를 반드시 거친다. 탈퇴 성공 시 백엔드가 같은 요청 안에서 세션을 이미
 * 무효화하므로, `router.refresh()`로 로그아웃 상태를 반영한다(`handleLogout`과
 * 동일 패턴).
 */
export function AccountMenu({ nickname }: { nickname: string }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [withdrawError, setWithdrawError] = useState<string | null>(null);

  async function handleLogout() {
    setPending(true);
    setError(false);
    const ok = await logout();
    if (ok) {
      router.refresh();
    } else {
      setPending(false);
      setError(true);
    }
  }

  async function handleWithdraw() {
    setWithdrawing(true);
    setWithdrawError(null);
    const ok = await withdraw();
    if (ok) {
      setConfirmOpen(false);
      router.refresh();
    } else {
      setWithdrawing(false);
      setWithdrawError("탈퇴에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  if (pending) {
    return (
      <Button variant="quiet" loading loadingLabel="로그아웃 중">
        {nickname}님
      </Button>
    );
  }

  return (
    <div className="stack">
      <DropdownMenu
        aria-label="계정 메뉴"
        trigger={(props) => (
          <Button {...props} variant="quiet">
            {nickname}님
          </Button>
        )}
        items={[
          { label: "로그아웃", onSelect: () => void handleLogout() },
          { label: "회원 탈퇴", onSelect: () => setConfirmOpen(true) },
        ]}
      />
      {error && (
        <InlineAlert tone="danger">로그아웃에 실패했습니다. 다시 시도해 주세요.</InlineAlert>
      )}

      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        title="정말 탈퇴할까요?"
        variant="danger"
        description="탈퇴하면 계정 데이터가 삭제되고 로그인 세션이 즉시 종료됩니다. 되돌릴 수 없습니다."
        confirmLabel="탈퇴"
        onConfirm={handleWithdraw}
        pending={withdrawing}
        errorMessage={withdrawError}
      />
    </div>
  );
}
