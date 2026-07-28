import type { Metadata } from "next";
import { RecommendationHistoryClient } from "@/components/recommendation-history-client";

export const metadata: Metadata = {
  title: "추천 이력",
  description: "기기에 저장된 추천 세트 이력을 확인하고 삭제할 수 있습니다.",
  alternates: { canonical: "/recommend/history" },
};

export default function RecommendationHistoryPage() {
  return (
    <section className="panel">
      <p className="eyebrow">번호 추천</p>
      <h1 className="page-title">추천 이력</h1>
      <RecommendationHistoryClient />
    </section>
  );
}
