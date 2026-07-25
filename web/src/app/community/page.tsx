import type { Metadata } from "next";
import Link from "next/link";
import { headers } from "next/headers";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { getPublicBaseUrl } from "@/lib/api";
import { getCommunityPosts } from "@/lib/community-api";

export const metadata: Metadata = {
  title: "커뮤니티",
  description: "KRAFT Lotto 이용자들과 정보를 나누는 커뮤니티 게시판입니다.",
  alternates: { canonical: "/community" },
};

type Props = { searchParams: Promise<{ page?: string }> };

export default async function CommunityPage({ searchParams }: Props) {
  const { page: pageParam } = await searchParams;
  const page = Math.max(0, Number(pageParam ?? 0) || 0);
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();
  // 백엔드 오류를 "게시글 없음"으로 감추지 않는다(§P1-03) — 실패는 최상위 error.tsx
  // 경계로 넘겨 재시도 UI를 보여주고, 정상 응답이 실제로 비어 있을 때만 빈 상태 문구를 쓴다.
  const result = await getCommunityPosts(page);

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "커뮤니티", item: `${baseUrl}/community` }]} />
      <p className="eyebrow">커뮤니티</p>
      <h1 className="page-title">커뮤니티</h1>
      <Link href="/community/write" className="button">
        글쓰기
      </Link>

      {result.items.length === 0 ? (
        <p>등록된 게시글이 없습니다.</p>
      ) : (
        <>
          <ul className="community-post-list">
            {result.items.map((post) => (
              <li key={post.id} className="community-post-list-item">
                <Link href={`/community/posts/${post.id}`}>{post.title}</Link>
                <span className="community-post-author">{post.authorNickname}</span>
              </li>
            ))}
          </ul>
          <nav aria-label="게시글 목록 페이지" className="community-pagination">
            {page > 0 && <Link href={`/community?page=${page - 1}`}>이전</Link>}
            <span>
              {page + 1} / {Math.max(1, result.totalPages)}
            </span>
            {page + 1 < result.totalPages && <Link href={`/community?page=${page + 1}`}>다음</Link>}
          </nav>
        </>
      )}
    </section>
  );
}
