import type { Metadata } from "next";
import { headers } from "next/headers";
import { RecommendClient } from "@/features/recommendation/recommend-client";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { PageHeader } from "@/components/page-header";
import { getPublicBaseUrl } from "@/lib/api";

export const metadata: Metadata = {
  title: "번호 추천",
  description: "제외할 번호를 설정하고 1~45 범위에서 추천 조합을 생성하고 저장할 수 있습니다.",
  alternates: { canonical: "/recommend" },
};

export default async function RecommendPage() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "번호 추천", item: `${baseUrl}/recommend` }]} />
      <PageHeader eyebrow="번호 추천" title="번호 추천" />
      <RecommendClient />
    </section>
  );
}
