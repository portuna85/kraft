import type { Metadata } from "next";
import { CompanionFilterClient } from "./companion-filter-client";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { PageHeader } from "@/components/page-header";
import { getCompanionStats } from "@/lib/api";
import { getPageSeoContext } from "@/lib/seo-context";
import { withCoreDataLogging } from "@/lib/logger";

export const metadata: Metadata = {
  title: "동반 출현",
  description: "로또 6/45에서 함께 자주 나온 번호 조합을 분석해 동반 출현 통계를 제공합니다.",
  alternates: { canonical: "/companion" },
};

export default async function CompanionPage() {
  const { nonce, baseUrl } = await getPageSeoContext();

  // 이 페이지의 유일한 핵심 데이터 — 실패를 200 폴백으로 숨기지 않고 error.tsx(5xx)로 넘긴다.
  const stats = await withCoreDataLogging(
    "동반 출현 통계 조회 실패 — 핵심 데이터 실패로 페이지 오류 처리",
    () => getCompanionStats()
  );

  // 초기 payload는 상위 50개만 전달해 SSR/RSC 응답 크기를 줄인다. 번호 필터 선택 시
  // 클라이언트가 서버의 번호별 필터 API(ball 파라미터)를 호출해 상위 50개 밖의 번호도 정확히 매칭한다.
  const initialPairs = stats.topPairs.slice(0, 50);
  // 990개 번호 쌍(₄₅C₂)의 평균 동반 출현 = 회차 수 × 6개 번호에서 뽑는 조합 수(₆C₂=15) ÷ 쌍 개수.
  // 이 기준선이 없으면 상위 쌍의 출현 횟수가 "특별히 잘 나오는 쌍"으로 오해될 여지가 있다.
  const averageCooccurrence = (stats.totalRounds * 15) / stats.topPairs.length;

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "동반 출현", item: `${baseUrl}/companion` }]} />
      <PageHeader
        eyebrow="동반 출현"
        title="동반 출현 번호"
        description={`총 ${stats.totalRounds}회 기준 전체 ${stats.topPairs.length}개 조합 · 기본 상위 50개 표시 · 쌍당 평균 동반 출현 ${averageCooccurrence.toFixed(1)}회`}
      />
      <CompanionFilterClient pairs={initialPairs} totalRounds={stats.totalRounds} />
    </section>
  );
}
