"use client";

import { usePathname, useSearchParams } from "next/navigation";

import { saveReturnTo } from "@/shared/lib/return-to";
import { InlineAlert } from "@/shared/ui/states";

const REASON_MESSAGES: Record<string, string> = {
  session_expired: "로그인 세션이 만료됐습니다. 다시 시도해 주세요.",
  access_denied: "로그인이 거부되었습니다.",
  disallowed_useragent:
    "카카오톡·인스타그램 등 인앱 브라우저에서는 구글 로그인이 차단됩니다. 브라우저 메뉴에서 " +
    '"다른 브라우저로 열기"를 선택한 뒤 다시 시도해 주세요.',
  unknown: "로그인에 실패했습니다. 다시 시도해 주세요.",
};

/**
 * I-02: `?login_error=1&reason=...`(CommunityLoginHandler.onAuthenticationFailure)을 읽어
 * 배너로 알린다. 이전에는 이 쿼리 파라미터를 읽는 코드가 프런트 어디에도 없어 — 실패한
 * OAuth 로그인이 평범한 홈 화면으로만 보였다(서버 로그 없이는 사용자도 운영자도 구분
 * 못 했다).
 */
export function LoginErrorBanner() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  if (searchParams.get("login_error") !== "1") {
    return null;
  }

  const reason = searchParams.get("reason") ?? "unknown";
  const message = REASON_MESSAGES[reason] ?? REASON_MESSAGES.unknown;

  return (
    <div className="shell">
      <InlineAlert tone="danger">
        {message}{" "}
        <a href="/oauth2/authorization/google" onClick={() => saveReturnTo(pathname)}>
          다시 로그인
        </a>
      </InlineAlert>
    </div>
  );
}
