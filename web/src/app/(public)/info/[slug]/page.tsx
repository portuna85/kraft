import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { INFO_PAGE_SLUGS, ROUTES } from "@/shared/config/routes";
import { formatDrawDate } from "@/shared/lib/format";
import { Breadcrumb } from "@/shared/ui/navigation";

import { INFO_PAGE_CONTENT } from "./content";
import { FaqJsonLd } from "./faq-json-ld";
import { INFO_PAGE_META, isInfoPageSlug } from "./metadata";

import styles from "./info.module.css";

type Props = { params: Promise<{ slug: string }> };

/** 슬러그를 빌드 시점에 만든다 — 내용이 정적이라 요청마다 만들 이유가 없다. */
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

      <article className={`prose ${styles.article}`}>{INFO_PAGE_CONTENT[slug]}</article>

      {slug === "faq" && <FaqJsonLd />}
    </div>
  );
}
