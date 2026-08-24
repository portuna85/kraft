/**
 * `(public)`·`(session)` 두 셸이 함께 읽는 로그인 여부 쿠키.
 *
 * 식별정보를 담지 않는 boolean 쿠키다(`CommunityLoginHandler`가 로그인 성공 시
 * 심고, 로그아웃·탈퇴 시 지운다) — 값은 `"1"`일 때만 로그인으로 본다.
 */
export const LOGGED_IN_COOKIE_NAME = "kraft_logged_in";
