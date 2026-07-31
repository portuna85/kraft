import type { Metadata } from "next";
import { headers } from "next/headers";
import { AnalysisClient } from "./analysis-client";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { PageHeader } from "@/components/page-header";
import { getPublicBaseUrl } from "@/lib/api";

export const metadata: Metadata = {
  title: "번호 조합 분석",
  description: "번호 6개를 입력하면 홀짝, 합계, 구간 분포와 역대 1등 당첨 내역을 분석합니다.",
  alternates: { canonical: "/analysis" },
};

export default async function AnalysisPage() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "번호 분석", item: `${baseUrl}/analysis` }]} />
      <PageHeader eyebrow="번호 분석" title="번호 조합 분석" />
      <AnalysisClient />
    </section>
  );
}
