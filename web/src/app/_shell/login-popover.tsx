"use client";

import { usePathname, useSearchParams } from "next/navigation";

import { useIsInAppBrowser } from "@/shared/hooks/use-in-app-browser";
import { saveReturnTo } from "@/shared/lib/return-to";
import { Button } from "@/shared/ui/button";
import { DropdownMenu } from "@/shared/ui/dropdown-menu";
import { InlineAlert } from "@/shared/ui/states";

import styles from "./login-popover.module.css";

/**
 * 로그인 진입점
 *
 * provider 버튼 2개를 헤더에 나란히 두지 않는다. "로그인" 단일 버튼 뒤에
 * popover로 provider를 감춘다 — 헤더 폭과 기대 행동을 라우트마다 다르게 만들지
 * 않기 위해서다.
 *
 * 세션을 조회하지 않는다. 완전 공개 라우트에서 세션 API를 부르면 익명 트래픽이
 * 발생하고(불변식 I-4), 세션 프로바이더가 번들에 딸려 들어온다. 로그인 후보
 * 항목은 fetch가 아니라 `<a href>`(`DropdownMenu`의 `href` 변형)여야 한다 —
 * OAuth는 브라우저 전체 이동이 필요하다.
 *
 * OAuth 콜백은 항상 공개 기본 URL로 돌아온다(서버 리다이렉트 대상은 여기서
 * 못 바꾼다) — 로그인 누르기 직전 경로(쿼리 포함)를 저장해 두면
 * `ReturnToRedirect`가 돌아온 뒤 그 경로로 한 번 이동시킨다(web-legacy
 * LoginLinks L-10과 같은 계약: 필터·검색 쿼리도 함께 보존).
 *
 * I-02: 카카오톡·인스타그램 등 인앱 브라우저는 구글이 OAuth 자체를
 * `disallowed_useragent`로 거부한다 — 실패하고 나서 알리면 늦으므로 시도 전에 경고한다.
 */
export function LoginPopover() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const query = searchParams.toString();
  const returnPath = query ? `${pathname}?${query}` : pathname;
  const inAppBrowser = useIsInAppBrowser();

  return (
    <div className={styles.wrap}>
      <DropdownMenu
        aria-label="로그인 방법 선택"
        trigger={(props) => (
          <Button {...props} variant="quiet">
            로그인
          </Button>
        )}
        items={[
          {
            label: "Google로 계속",
            href: "/oauth2/authorization/google",
            onClick: () => saveReturnTo(returnPath),
          },
          {
            label: "Naver로 계속",
            href: "/oauth2/authorization/naver",
            onClick: () => saveReturnTo(returnPath),
          },
        ]}
      />
      {inAppBrowser && (
        <div className={styles.warning}>
          <InlineAlert tone="warning">
            카카오톡·인스타그램 등 인앱 브라우저에서는 구글 로그인이 차단됩니다. 브라우저 메뉴에서
            &quot;다른 브라우저로 열기&quot;를 선택해 주세요.
          </InlineAlert>
        </div>
      )}
    </div>
  );
}
