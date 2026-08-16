/**
 * I-02: 카카오톡·네이버·인스타그램 등 인앱 브라우저(임베디드 웹뷰) 감지
 *
 * 구글은 임베디드 웹뷰에서의 OAuth 로그인을 `disallowed_useragent` 오류로 거부한다.
 * 로그인 시도가 실패하고 나서야 알리면 이미 늦으므로, `LoginPopover`가 이 감지를 먼저
 * 보여준다. 클라이언트 전용 판정이다 — User-Agent는 백엔드 로그에 절대 남기지 않는다
 * (`web/src/app/api/vitals/route.ts` 참고).
 */
const IN_APP_BROWSER_PATTERN = /KAKAOTALK|NAVER\(inapp|Instagram|FBAN|FBAV|Line\//i;

export function isInAppBrowser(userAgent: string): boolean {
  return IN_APP_BROWSER_PATTERN.test(userAgent);
}
