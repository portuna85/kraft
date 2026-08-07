import { buildWebsiteJsonLd } from "@/lib/csp-inline-scripts";
import { serializeJsonLd } from "@/lib/json-ld-serialize";

type BreadcrumbItem = { name: string; item?: string };

type JsonLdBreadcrumbProps = {
  baseUrl: string;
  nonce?: string;
  items: BreadcrumbItem[];
};

export function JsonLdBreadcrumb({ baseUrl, nonce, items }: JsonLdBreadcrumbProps) {
  const schema = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      { "@type": "ListItem", position: 1, name: "홈", item: baseUrl },
      ...items.map((crumb, index) => ({
        "@type": "ListItem",
        position: index + 2,
        name: crumb.name,
        ...(crumb.item ? { item: crumb.item } : {}),
      })),
    ],
  };
  return (
    <script
      type="application/ld+json"
      nonce={nonce}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: serializeJsonLd(schema) }}
    />
  );
}

type JsonLdDiscussionForumPostingProps = {
  baseUrl: string;
  nonce?: string;
  url: string;
  headline: string;
  text: string;
  authorName: string;
  datePublished: string;
  dateModified: string;
  commentCount: number;
};

export function JsonLdDiscussionForumPosting({
  baseUrl,
  nonce,
  url,
  headline,
  text,
  authorName,
  datePublished,
  dateModified,
  commentCount,
}: JsonLdDiscussionForumPostingProps) {
  const schema = {
    "@context": "https://schema.org",
    "@type": "DiscussionForumPosting",
    headline,
    text,
    url,
    mainEntityOfPage: url,
    datePublished,
    dateModified,
    commentCount,
    author: { "@type": "Person", name: authorName },
    isPartOf: { "@type": "WebSite", name: "KRAFT Lotto", url: baseUrl },
  };
  return (
    <script
      type="application/ld+json"
      nonce={nonce}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: serializeJsonLd(schema) }}
    />
  );
}

type JsonLdWebSiteProps = {
  baseUrl: string;
  nonce?: string;
};

export function JsonLdWebSite({ baseUrl, nonce }: JsonLdWebSiteProps) {
  return (
    <script
      type="application/ld+json"
      nonce={nonce}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: serializeJsonLd(buildWebsiteJsonLd(baseUrl)) }}
    />
  );
}
