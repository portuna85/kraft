import type { Metadata } from "next";
import Link from "next/link";
import { headers } from "next/headers";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { getPublicBaseUrl } from "@/lib/api";
import { getCommunityPosts, type PostCategory, type PostSort } from "@/lib/community-api";
import { CATEGORY_LABELS, CATEGORY_OPTIONS } from "@/features/community/types";

export const metadata: Metadata = {
  title: "커뮤니티",
  description: "KRAFT Lotto 이용자들과 정보를 나누는 커뮤니티 게시판입니다.",
  alternates: { canonical: "/community" },
};

type Props = {
  searchParams: Promise<{ page?: string; category?: string; sort?: string; query?: string }>;
};

const SORT_LABELS: Record<PostSort, string> = { latest: "최신", weekly_popular: "이번 주 인기" };

function isPostCategory(value: string | undefined): value is PostCategory {
  return (CATEGORY_OPTIONS as readonly string[]).includes(value ?? "");
}

function buildHref(params: {
  page?: number;
  category?: string;
  sort?: string;
  query?: string;
}): string {
  const search = new URLSearchParams();
  if (params.category) search.set("category", params.category);
  if (params.sort) search.set("sort", params.sort);
  if (params.query) search.set("query", params.query);
  if (params.page) search.set("page", String(params.page));
  const qs = search.toString();
  return qs ? `/community?${qs}` : "/community";
}

export default async function CommunityPage({ searchParams }: Props) {
  const { page: pageParam, category: categoryParam, sort: sortParam, query } = await searchParams;
  const page = Math.max(0, Number(pageParam ?? 0) || 0);
  const category = isPostCategory(categoryParam) ? categoryParam : undefined;
  const sort: PostSort = sortParam === "weekly_popular" ? "weekly_popular" : "latest";
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();
  // 백엔드 오류를 "게시글 없음"으로 감추지 않는다(§P1-03) — 실패는 최상위 error.tsx
  // 경계로 넘겨 재시도 UI를 보여주고, 정상 응답이 실제로 비어 있을 때만 빈 상태 문구를 쓴다.
  const result = await getCommunityPosts(page, 20, { category, sort, query });

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "커뮤니티", item: `${baseUrl}/community` }]} />
      <p className="eyebrow">커뮤니티</p>
      <h1 className="page-title">커뮤니티</h1>
      <Link href="/community/write" className="button">
        글쓰기
      </Link>

      <form method="get" className="community-search-form">
        {category && <input type="hidden" name="category" value={category} />}
        {sort !== "latest" && <input type="hidden" name="sort" value={sort} />}
        <label htmlFor="community-search">검색</label>
        <input id="community-search" type="search" name="query" defaultValue={query ?? ""} minLength={2} maxLength={50} />
        <button type="submit">검색</button>
      </form>

      <nav aria-label="카테고리" className="community-category-nav">
        <Link href={buildHref({ sort, query })} aria-current={!category ? "page" : undefined}>
          전체
        </Link>
        {CATEGORY_OPTIONS.map((option) => (
          <Link
            key={option}
            href={buildHref({ category: option, sort, query })}
            aria-current={category === option ? "page" : undefined}
          >
            {CATEGORY_LABELS[option]}
          </Link>
        ))}
      </nav>

      <nav aria-label="정렬" className="community-sort-nav">
        {(Object.keys(SORT_LABELS) as PostSort[]).map((option) => (
          <Link
            key={option}
            href={buildHref({ category, sort: option, query })}
            aria-current={sort === option ? "page" : undefined}
          >
            {SORT_LABELS[option]}
          </Link>
        ))}
      </nav>

      {result.items.length === 0 ? (
        <p>등록된 게시글이 없습니다.</p>
      ) : (
        <>
          <ul className="community-post-list">
            {result.items.map((post) => (
              <li key={post.id} className="community-post-list-item">
                <span className="community-post-category">{CATEGORY_LABELS[post.category]}</span>
                <Link href={`/community/posts/${post.id}`}>{post.title}</Link>
                <span className="community-post-author">{post.authorNickname}</span>
                <span className="community-post-counts">
                  좋아요 {post.likeCount} · 댓글 {post.commentCount} · 조회 {post.viewCount}
                </span>
              </li>
            ))}
          </ul>
          <nav aria-label="게시글 목록 페이지" className="community-pagination">
            {page > 0 && <Link href={buildHref({ page: page - 1, category, sort, query })}>이전</Link>}
            <span>
              {page + 1} / {Math.max(1, result.totalPages)}
            </span>
            {page + 1 < result.totalPages && (
              <Link href={buildHref({ page: page + 1, category, sort, query })}>다음</Link>
            )}
          </nav>
        </>
      )}
    </section>
  );
}
