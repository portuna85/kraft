import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { InsightsHubNav } from "@/features/insights-hub-nav/insights-hub-nav";
import { INFO_PAGE_SLUGS, ROUTES } from "@/shared/config/routes";
import { formatDrawDate } from "@/shared/lib/format";
import { Breadcrumb } from "@/shared/ui/navigation";

import { INFO_PAGE_CONTENT } from "./content";
import { FaqJsonLd } from "./faq-json-ld";
import { INFO_PAGE_META, isInfoPageSlug } from "./metadata";

import styles from "./info.module.css";

type Props = { params: Promise<{ slug: string }> };

/** 9개 슬러그를 빌드 시점에 만든다 — 내용이 정적이라 요청마다 만들 이유가 없다. */
export function generateStaticParams() {
  return INFO_PAGE_SLUGS.map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  if (!isInfoPageSlug(slug)) return {};

  const meta = INFO_PAGE_META[slug];
  return {
    title: meta.title,
    description: meta.description,
    // canonical을 슬러그별로 정확히 준다 — 전 페이지가 홈을 가리키면 색인이 뭉개진다(§29.8).
    alternates: { canonical: ROUTES.info(slug) },
  };
}

export default async function InfoPage({ params }: Props) {
  const { slug } = await params;
  if (!isInfoPageSlug(slug)) notFound();

  const meta = INFO_PAGE_META[slug];

  return (
    <div className="stack">
      <Breadcrumb items={[{ label: "홈", href: ROUTES.home }]} current={meta.title} />

      <header className="prose stack">
        <h1>{meta.title}</h1>
        <p>{meta.description}</p>
        <p className="note">
          최종 수정: <time dateTime={meta.lastModified}>{formatDrawDate(meta.lastModified)}</time>
        </p>
      </header>

      {/* kraft-redesign-plan.md §4 "Insights sub-navigation"의 다섯 번째 항목
          (Data Source & Methodology)이 이 공용 라우트의 한 슬러그다 — 나머지
          8개 슬러그(FAQ·약관 등)에는 인사이트 허브와 관계가 없으므로 이 탭을
          붙이지 않는다. */}
      {slug === "data-source" && <InsightsHubNav />}

      <article className={`prose ${styles.article}`}>{INFO_PAGE_CONTENT[slug]}</article>

      {slug === "faq" && <FaqJsonLd />}
    </div>
  );
}
