import { cookies } from "next/headers";
import Link from "next/link";

import { ROUTES } from "@/shared/config/routes";

import { LoginPopover } from "./login-popover";
import styles from "./shell.module.css";

const LOGGED_IN_COOKIE_NAME = "kraft_logged_in";

/**
 * I-03: `(public)` 셸의 계정 영역.
 *
 * 세션 API를 부르지 않는다(불변식 I-4) — 루트 레이아웃이 이미 CSP nonce 때문에
 * `headers()`를 읽어 전 라우트가 동적 렌더이므로(§I-09), 여기서 요청 쿠키 중
 * 식별정보 없는 boolean(`kraft_logged_in`, `CommunityLoginHandler`가 로그인 성공 시
 * 심는다)만 읽어 판단한다. 이전에는 이 자리가 항상 `LoginPopover`였다 — OAuth 성공이
 * 항상 `/`로 착지하는데 `/`가 세션을 못 읽어 로그인 성공과 로그아웃 상태가 픽셀
 * 단위로 같았다. 닉네임·로그아웃 등 실제 계정 메뉴는 `/community`·`/saved`·
 * `/recommend`(세션 셸)에서만 보여준다.
 */
export async function PublicAccountSlot() {
  const cookieStore = await cookies();
  const loggedIn = cookieStore.get(LOGGED_IN_COOKIE_NAME)?.value === "1";

  if (!loggedIn) {
    return <LoginPopover />;
  }

  return (
    <Link className={styles.navLink} href={ROUTES.community}>
      로그인됨
    </Link>
  );
}
