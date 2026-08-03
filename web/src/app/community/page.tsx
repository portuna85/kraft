import type { Metadata } from "next";
import Link from "next/link";
import { headers } from "next/headers";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { PageHeader } from "@/components/page-header";
import { getPublicBaseUrl } from "@/lib/api";
import { EmptyState } from "@/ui/primitives/empty-state";
import { getCommunityPosts, type PostCategory, type PostSort } from "@/lib/community-api";
import { CATEGORY_LABELS, CATEGORY_OPTIONS } from "@/features/community/types";
import { CommunityPostList } from "@/features/community/community-post-list";

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
  // 정렬은 기본값이 있어 "좁힌 조건"으로 치지 않는다 — 검색어와 카테고리만 해제 대상이다.
  const hasActiveFilter = Boolean(query) || Boolean(category);

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "커뮤니티", item: `${baseUrl}/community` }]} />
      <PageHeader
        eyebrow="커뮤니티"
        title="커뮤니티"
        description="번호와 당첨 정보, 분석 경험을 다른 이용자와 나눠 보세요."
        actions={<Link href="/community/write" className="button">글쓰기</Link>}
      />

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
        // FE-048: 이전에는 "검색 결과 0건"과 "게시판이 비어 있음"이 같은 문구였다.
        // 필터를 좁혀서 안 나온 것인지 원래 글이 없는 것인지 구분하고, 해제 경로를 준다.
        hasActiveFilter ? (
          <div className="community-empty">
            {/* FE-009: 표현은 다른 화면과 같은 EmptyState로 맞추되, 원인 구분(FE-048)은 유지한다.
                해제 경로는 서버 컴포넌트라 onClick 대신 Link로 둔다. */}
            <EmptyState
              title={query ? `“${query}” 검색 결과가 없습니다.` : "이 조건에 맞는 게시글이 없습니다."}
              description="검색어나 카테고리를 바꾸거나 필터를 해제해 보세요."
            />
            <Link href="/community" className="button secondary">필터 해제하고 전체 보기</Link>
          </div>
        ) : (
          <EmptyState title="아직 등록된 게시글이 없습니다." description="첫 글을 작성해 보세요." />
        )
      ) : (
        <>
          {hasActiveFilter && (
            <p className="muted" role="status">
              {query ? `“${query}” 검색 결과 ` : "조건에 맞는 게시글 "}
              {result.totalElements}건
              {" · "}
              <Link href="/community">필터 해제</Link>
            </p>
          )}
          <CommunityPostList items={result.items} />
          {/* FE-052: totalPages를 알고 있으면서 이전/다음만 제공해, 뒤쪽 페이지에서
              1페이지로 돌아가려면 뒤로가기밖에 없었다. */}
          <nav aria-label="게시글 목록 페이지" className="community-pagination">
            {page > 0 && (
              <>
                <Link href={buildHref({ category, sort, query })} aria-label="첫 페이지">처음</Link>
                <Link href={buildHref({ page: page - 1, category, sort, query })}>이전</Link>
              </>
            )}
            <span aria-current="page">
              {page + 1} / {Math.max(1, result.totalPages)}
            </span>
            {page + 1 < result.totalPages && (
              <>
                <Link href={buildHref({ page: page + 1, category, sort, query })}>다음</Link>
                <Link
                  href={buildHref({ page: result.totalPages - 1, category, sort, query })}
                  aria-label="마지막 페이지"
                >
                  마지막
                </Link>
              </>
            )}
          </nav>
        </>
      )}
    </section>
  );
}
