import type { Metadata } from "next";
import Link from "next/link";

import { getPostPage } from "@/entities/community-post/api";
import { buildListHref, hasActiveFilter, parseListParams } from "@/entities/community-post/query";
import {
  CATEGORY_LABELS,
  POST_CATEGORIES,
  POST_SORTS,
  SORT_LABELS,
} from "@/entities/community-post/schema";
import { PostSummaryCard } from "@/entities/community-post/ui/post-summary-card";
import { ROUTES } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/states";
import { Pagination } from "@/shared/ui/surface";

import styles from "./community.module.css";

export const metadata: Metadata = {
  title: "커뮤니티",
  description: "번호 조합과 회차 분석을 나누는 공간입니다.",
  alternates: { canonical: "/community" },
};

/**
 * 커뮤니티 목록 — improvement_fe.md §23.9, §5.6
 *
 * **URL이 상태의 단일 진실 공급원이다.** 검색은 method="get" 폼, 필터·정렬·페이지는
 * 링크다. 공유되고, 뒤로가기가 동작하고, JS 없이도 넘어가며, RSC가 그대로 읽는다.
 */
export default async function CommunityPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string; category?: string; sort?: string; query?: string }>;
}) {
  const params = parseListParams(await searchParams);
  const page = await getPostPage(params);

  return (
    <div className="stack">
      <header className="stack">
        <h1>커뮤니티</h1>
        <Link className={styles.writeLink} href={ROUTES.communityWrite}>
          글쓰기
        </Link>
      </header>

      {/* GET 폼이라 검색 결과가 URL에 남는다. 기존 필터는 hidden으로 유지한다. */}
      <form className={styles.search} method="get" action={ROUTES.community} role="search">
        <label htmlFor="query">검색어</label>
        <input
          id="query"
          name="query"
          type="search"
          defaultValue={params.query ?? ""}
          minLength={2}
          maxLength={50}
          placeholder="2자 이상 입력하세요"
        />
        {params.category !== undefined && (
          <input type="hidden" name="category" value={params.category} />
        )}
        {params.sort !== "latest" && <input type="hidden" name="sort" value={params.sort} />}
        <button type="submit">검색</button>
      </form>

      <nav className={styles.filters} aria-label="분류">
        <Link
          className={styles.filter}
          href={buildListHref(params, { category: undefined, page: 0 })}
          aria-current={params.category === undefined ? "page" : undefined}
        >
          전체
        </Link>
        {POST_CATEGORIES.map((category) => (
          <Link
            key={category}
            className={styles.filter}
            href={buildListHref(params, { category, page: 0 })}
            aria-current={params.category === category ? "page" : undefined}
          >
            {CATEGORY_LABELS[category]}
          </Link>
        ))}
      </nav>

      <nav className={styles.filters} aria-label="정렬">
        {POST_SORTS.map((sort) => (
          <Link
            key={sort}
            className={styles.filter}
            href={buildListHref(params, { sort, page: 0 })}
            aria-current={params.sort === sort ? "page" : undefined}
          >
            {SORT_LABELS[sort]}
          </Link>
        ))}
      </nav>

      {page.items.length === 0 ? (
        // 빈 결과의 원인을 구분한다(FE-048) — 필터 때문인지 원래 없는지 모르면
        // 사용자는 필터를 지워야 한다는 것을 알 수 없다.
        hasActiveFilter(params) ? (
          <EmptyState
            reason="filtered"
            title="조건에 맞는 글이 없습니다"
            description="검색어나 분류를 바꿔 보세요."
            action={<Link href={ROUTES.community}>조건 모두 지우기</Link>}
          />
        ) : (
          <EmptyState
            reason="no-data"
            title="아직 글이 없습니다"
            description="첫 글을 남겨 보세요."
            action={<Link href={ROUTES.communityWrite}>글쓰기</Link>}
          />
        )
      ) : (
        <ol className="stack">
          {page.items.map((post) => (
            <PostSummaryCard key={post.id} post={post} />
          ))}
        </ol>
      )}

      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        buildHref={(next) => buildListHref(params, { page: next })}
      />
    </div>
  );
}
