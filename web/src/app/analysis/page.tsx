import type { Metadata } from "next";
import { headers } from "next/headers";
import { AnalysisClient } from "@/components/analysis-client";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { getPublicBaseUrl } from "@/lib/api";

export const metadata: Metadata = {
  title: "번호 조합 분석",
  description: "번호 6개를 입력하면 홀짝, 합계, 구간 분포 등 기본 통계를 분석합니다.",
  alternates: { canonical: "/analysis" },
};

export default async function AnalysisPage() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "번호 분석", item: `${baseUrl}/analysis` }]} />
      <p className="eyebrow">번호 분석</p>
      <h1 className="page-title">번호 조합 분석</h1>
      <AnalysisClient />
    </section>
  );
}
