"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import { logout } from "@/entities/user-session/api";
import { DropdownMenu } from "@/shared/ui/dropdown-menu";
import { Button } from "@/shared/ui/button";
import { InlineAlert } from "@/shared/ui/states";

/**
 * 로그인 상태 계정 메뉴 — improvement_fe_codex.md §4.4 ("Signed in: avatar/initial + menu")
 *
 * `(session)` 셸에서만 렌더된다 — 세션을 아는 곳이 여기뿐이다(불변식 I-4). 회원
 * 탈퇴는 이번 범위가 아니다(codex가 요구하지 않았고, 시각 재설계가 아니라 기능
 * 패리티 문제다 — web-legacy의 `AccountMenu` 참고).
 */
export function AccountMenu({ nickname }: { nickname: string }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(false);

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
        items={[{ label: "로그아웃", onSelect: () => void handleLogout() }]}
      />
      {error && (
        <InlineAlert tone="danger">로그아웃에 실패했습니다. 다시 시도해 주세요.</InlineAlert>
      )}
    </div>
  );
}
