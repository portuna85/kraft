"use client";

import {
  canQueryOwnerScope,
  sessionReadiness,
  useSession,
} from "@/entities/user-session/session-context";

import { AccountMenu } from "./account-menu";
import { LoginPopover } from "./login-popover";
import styles from "./shell.module.css";

/**
 * `(session)` 셸의 계정 영역
 *
 * `(public)` 셸은 세션을 조회하지 않으므로(불변식 I-4) 이 컴포넌트를 쓰지 않고
 * `LoginPopover`를 직접 꽂는다. 여기서만 로그인 여부에 따라 실제 계정 메뉴 또는
 * 로그인 팝오버를 고를 수 있다.
 */
export function AccountControl() {
  const session = useSession();

  // KF-25①(docs/improvement.md): 세션 판정 전에 로그인 팝오버를 먼저 보여줬다가
  // 계정 메뉴로 바뀌면 헤더가 깜빡이는 문제뿐 아니라, `null`을 반환하면 자리
  // 자체가 없어 최종 콘텐츠가 나타날 때 `.headerActions`가 커지며 레이아웃이
  // 밀린다(실측 CLS). `ThemeToggle`의 `.themeTogglePlaceholder`와 같은 패턴으로
  // 자리를 먼저 예약한다. `session.loading` 대신 `sessionReadiness()`를 써서
  // `error` 상태도 같은 "미확정" 취급으로 일관되게 둔다(KF-09의 3상태 모델).
  if (sessionReadiness(session) === "unsettled") {
    return <span className={styles.accountControlPlaceholder} aria-hidden="true" />;
  }

  if (canQueryOwnerScope(session)) {
    // 닉네임이 비어 있어도 계정 메뉴를 보여준다 — 로그인 상태인데 로그인 팝오버가
    // 다시 뜨면 이미 로그인한 사용자가 또 로그인하라는 오작동으로 보인다.
    return <AccountMenu nickname={session.session?.nickname ?? "계정"} />;
  }

  return <LoginPopover />;
}
