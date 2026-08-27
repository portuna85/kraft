import type { Metadata } from "next";
import Link from "next/link";

import { RecommendHistoryList } from "@/features/recommend-history/recommend-history-list";
import { ROUTES } from "@/shared/config/routes";

export const metadata: Metadata = {
  title: "추천 이력",
  description: "과거에 생성한 번호 추천 조합을 다시 확인합니다.",
  // 개인화·기기 데이터라 색인하지 않는다(§23.8).
  robots: { index: false, follow: false },
};

/**
 * 추천 이력
 *
 * 레거시는 본문이 제목 한 줄뿐이었다(§23.8 신규 요구). 이력의 의미와 보관 범위(기기 vs
 * 계정)를 설명해 그 문제를 해소한다.
 */
export default function RecommendHistoryPage() {
  return (
    <div className="stack">
      <header className="prose stack">
        <h1>추천 이력</h1>
        <p>
          지금까지 생성한 번호 추천 조합을 최신 순으로 모았습니다. 로그인하지 않고 만든 조합은 이
          브라우저에만 연결되고, 로그인하면 계정에 연결된 이력만 보입니다.
        </p>
        <p className="note">
          저장한 번호는 <Link href={ROUTES.saved}>보관함</Link>에서 따로 볼 수 있습니다.
        </p>
      </header>

      <RecommendHistoryList />
    </div>
  );
}
