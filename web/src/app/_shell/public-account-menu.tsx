"use client";

import { SessionProvider } from "@/features/identity-session/session-provider";

import { AccountControl } from "./account-control";

/**
 * `(public)` 셸의 로그인 사용자 전용 계정 메뉴.
 *
 * `PublicAccountSlot`이 서버에서 `kraft_logged_in` 쿠키를 이미 확인한 뒤에만 이
 * 컴포넌트를 마운트한다 — 익명 방문자는 이 파일 자체가 번들에 없으므로 세션 API를
 * 호출하지 않는다(불변식 I-4의 의도는 그대로 유지한다). 로그인 사용자에 한해서만
 * `(session)` 셸과 동일한 `AccountControl`/`AccountMenu`(닉네임+로그아웃)를 재사용해,
 * 페이지 그룹에 따라 계정 영역이 달라 보이던 문제를 없앤다.
 */
export function PublicAccountMenu() {
  return (
    <SessionProvider>
      <AccountControl />
    </SessionProvider>
  );
}
