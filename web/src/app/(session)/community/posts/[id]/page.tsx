import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { getPost } from "@/entities/community-post/api";
import { CATEGORY_LABELS, isTombstone } from "@/entities/community-post/schema";
import { ApiError } from "@/shared/api/error";
import { Badge } from "@/shared/ui/badge";

import styles from "../../community.module.css";

/**
 * 게시글 상세 — improvement_fe.md §23.10
 *
 * **이 라우트는 캐시되지 않는다.** GET이 조회수를 올리는 부수효과를 갖기 때문이며,
 * 그 설정은 entities/community-post/api.ts가 소유한다(M-4). 성능을 이유로 캐시를
 * 켜면 조회수 지표가 조용히 멈춘다 — 화면상으로는 멀쩡해 보여서 늦게 발견된다.
 *
 * 제목·작성자·작성일은 **SSR 본문에 포함**된다(§25.6, §29.8). 클라이언트에서 채우면
 * 크롤러가 빈 문서를 본다.
 */
export const dynamic = "force-dynamic";

function parseId(raw: string): number | null {
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const id = parseId((await params).id);
  if (id === null) return { title: "게시글" };

  try {
    const post = await getPost(id);
    return {
      title: post.title,
      // 본문을 그대로 쓰지 않고 잘라 쓴다 — 설명이 너무 길면 검색 결과에서 잘린다.
      description: post.content.slice(0, 120),
      alternates: { canonical: `/community/posts/${id}` },
    };
  } catch {
    // 메타데이터 생성 실패가 페이지 자체를 죽이지 않게 한다.
    return { title: "게시글" };
  }
}

export default async function PostDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const id = parseId((await params).id);
  if (id === null) notFound();

  let post;
  try {
    post = await getPost(id);
  } catch (cause) {
    // 404는 "없는 글"이라 오류 화면이 아니라 not-found로 보낸다. 그 외 실패는
    // 경계까지 던져 5xx가 되게 한다(§7.6).
    if (cause instanceof ApiError && cause.status === 404) notFound();
    throw cause;
  }

  if (isTombstone(post)) {
    return (
      <article className={styles.postBody}>
        <h1>표시할 수 없는 글</h1>
        <p>삭제되었거나 운영 정책에 따라 가려진 글입니다.</p>
      </article>
    );
  }

  return (
    <article className={styles.postBody}>
      <header className="stack">
        <Badge tone="neutral" size="sm" label={CATEGORY_LABELS[post.category]} />
        <h1>{post.title}</h1>
        <p className={styles.postMeta}>
          <span>{post.authorNickname}</span>
          <time dateTime={post.createdAt}>{post.createdAt.slice(0, 10)}</time>
          <span>조회 {post.viewCount}</span>
          <span>좋아요 {post.likeCount}</span>
        </p>
      </header>

      {/*
       * 본문은 dangerouslySetInnerHTML 없이 줄 단위 <p>로 렌더한다(§20.1).
       * 사용자가 쓴 문자열을 HTML로 해석하는 순간 XSS 경로가 열린다 — 서식이 필요해도
       * 이 방식을 먼저 유지하고, 정말 필요하면 허용 목록 기반 변환을 별도로 검토한다.
       */}
      {post.content.split("\n").map((line, index) => (
        <p key={`${index}-${line.slice(0, 12)}`}>{line === "" ? " " : line}</p>
      ))}
    </article>
  );
}
