"use client";

import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";

import { AccountMenu } from "./account-menu";
import { LoginPopover } from "./login-popover";

/**
 * `(session)` 셸의 계정 영역 — improvement_fe_codex.md §4.4
 *
 * `(public)` 셸은 세션을 조회하지 않으므로(불변식 I-4) 이 컴포넌트를 쓰지 않고
 * `LoginPopover`를 직접 꽂는다. 여기서만 로그인 여부에 따라 실제 계정 메뉴 또는
 * 로그인 팝오버를 고를 수 있다.
 */
export function AccountControl() {
  const session = useSession();

  // 세션 판정 전에 로그인 팝오버를 먼저 보여줬다가 계정 메뉴로 바뀌면 헤더가
  // 깜빡인다 — 판정이 끝날 때까지 아무것도 그리지 않는다.
  if (session.loading) return null;

  if (canQueryOwnerScope(session)) {
    // 닉네임이 비어 있어도 계정 메뉴를 보여준다 — 로그인 상태인데 로그인 팝오버가
    // 다시 뜨면 이미 로그인한 사용자가 또 로그인하라는 오작동으로 보인다.
    return <AccountMenu nickname={session.session?.nickname ?? "계정"} />;
  }

  return <LoginPopover />;
}
