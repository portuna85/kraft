import { cookies } from "next/headers";

import { PublicAccountArea } from "./public-account-area";

const LOGGED_IN_COOKIE_NAME = "kraft_logged_in";

/**
 * I-03: `(public)` 셸의 계정 영역.
 *
 * 익명 방문자에게는 세션 API를 부르지 않는다(불변식 I-4의 의도) — 루트 레이아웃이
 * 이미 CSP nonce 때문에 `headers()`를 읽어 전 라우트가 동적 렌더이므로(§I-09),
 * 여기서 요청 쿠키 중 식별정보 없는 boolean(`kraft_logged_in`,
 * `CommunityLoginHandler`가 로그인 성공 시 심는다)만 읽어 `PublicAccountArea`에
 * 넘긴다. 로그인 사용자에 한해서만 `PublicAccountMenu`(세션 API 호출 + `(session)`
 * 셸과 동일한 닉네임/로그아웃 메뉴)가 로드된다 — 그래서 페이지 그룹과 무관하게
 * 로그인 사용자는 항상 같은 계정 메뉴를 본다. 예전에는 이 자리가 항상
 * `LoginPopover`였다 — OAuth 성공이 항상 `/`로 착지하는데 `/`가 세션을 못 읽어
 * 로그인 성공과 로그아웃 상태가 픽셀 단위로 같았다.
 *
 * 분기 자체를 여기(Server Component)가 아니라 `PublicAccountArea`(client)
 * 안에서 한다 — 이유는 그 파일의 주석 참조.
 */
export async function PublicAccountSlot() {
  const cookieStore = await cookies();
  const loggedIn = cookieStore.get(LOGGED_IN_COOKIE_NAME)?.value === "1";

  return <PublicAccountArea loggedIn={loggedIn} />;
}
