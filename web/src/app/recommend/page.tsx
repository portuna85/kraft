import type { Metadata } from "next";
import { headers } from "next/headers";
import { RecommendClient } from "@/features/recommendation/recommend-client";
import { JsonLdBreadcrumb } from "@/components/json-ld";
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
      <p className="eyebrow">번호 추천</p>
      <h1 className="page-title">번호 추천</h1>
      <RecommendClient />
    </section>
  );
}
