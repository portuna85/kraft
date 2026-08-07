import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "운영 콘솔",
  robots: { index: false, follow: false },
};

/**
 * Phase 5에서 구현한다(§23.14). 지금 이 페이지가 존재하는 이유는 호스트 게이트가
 * 실제로 404를 내는지 E2E로 검증하려면 게이트 뒤에 도달 가능한 라우트가 있어야 하기
 * 때문이다 — 게이트만 있고 라우트가 없으면 "차단됐는지" "원래 없는지" 구분할 수 없다.
 */
export default function OpsPage() {
  return <h1>운영 콘솔</h1>;
}
