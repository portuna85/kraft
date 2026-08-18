"use client";

import dynamic from "next/dynamic";

import { LoginPopover } from "./login-popover";

/**
 * `next/dynamic`을 Server Component(`PublicAccountSlot`)가 아니라 이미 client
 * 경계를 넘은 이 컴포넌트 안에서 호출한다 — report-dialog.tsx와 같은 이유다.
 * Server Component 쪽에서 직접 분기해 두 client 컴포넌트 중 하나를 조건부로
 * 반환하면, Next가 두 갈래 모두를 그 레이아웃의 client-reference-manifest에
 * 정적으로 등록해 버려 로그인 여부와 무관하게 `PublicAccountMenu`(세션
 * 프로바이더·계정 메뉴 등)의 청크가 (public) 셸 전체의 초기 JS에 포함된다
 * (bundle-budget.mjs가 이 정적 도달 가능성을 측정한다). 여기서처럼 이미
 * client인 컴포넌트 안에서 `next/dynamic`을 호출해야 실제로 지연 로드되는
 * 별도 청크가 된다.
 */
const PublicAccountMenu = dynamic(() =>
  import("./public-account-menu").then((module) => module.PublicAccountMenu),
);

export function PublicAccountArea({ loggedIn }: { loggedIn: boolean }) {
  if (!loggedIn) return <LoginPopover />;
  return <PublicAccountMenu />;
}
