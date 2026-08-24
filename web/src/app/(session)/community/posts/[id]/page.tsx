import type { Metadata } from "next";
import { headers } from "next/headers";
import { notFound } from "next/navigation";

import { getCommentPage } from "@/entities/community-comment/api";
import { getPost } from "@/entities/community-post/api";
import { CATEGORY_LABELS, isTombstone } from "@/entities/community-post/schema";
import { ReportDialog } from "@/entities/community-report/ui/report-dialog";
import { CommentSection } from "@/features/community-comments/comment-section";
import {
  BlockButton,
  BlockedPostGate,
  ReportGate,
} from "@/features/community-post/blocked-post-gate";
import { PostOwnerActions } from "@/features/community-post/post-owner-actions";
import { ReactionBar } from "@/features/community-post/reaction-bar";
import { RecommendationAttachmentView } from "@/features/community-post/recommendation-attachment-view";
import { ApiError } from "@/shared/api/error";
import { NONCE_HEADER } from "@/shared/config/csp";
import { publicEnv } from "@/shared/config/env";
import { ROUTES } from "@/shared/config/routes";
import { formatDateTime } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { JsonLd } from "@/shared/ui/json-ld";
import { Breadcrumb } from "@/shared/ui/navigation";
import { InlineAlert } from "@/shared/ui/states";

import styles from "../../community.module.css";

/**
 * 게시글 상세
 *
 * **이 라우트는 캐시되지 않는다.** GET이 조회수를 올리는 부수효과를 갖기 때문이며,
 * 그 설정은 entities/community-post/api.ts가 소유한다(M-4). 성능을 이유로 캐시를
 * 켜면 조회수 지표가 조용히 멈춘다 — 화면상으로는 멀쩡해 보여서 늦게 발견된다.
 *
 * 제목·작성자·작성일은 **SSR 본문에 포함**된다(§25.6, §29.8). 클라이언트에서 채우면
 * 크롤러가 빈 문서를 본다. 댓글 첫 페이지도 같은 이유로 서버에서 가져온다.
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
      alternates: { canonical: ROUTES.communityPost(id) },
    };
  } catch {
    // 메타데이터 생성 실패가 페이지 자체를 죽이지 않게 한다.
    return { title: "게시글" };
  }
}

export default async function PostDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const id = parseId((await params).id);
  if (id === null) notFound();

  // KF-26(FE-OPT-23, docs/improvement.md): headers()는 post·comments 어느
  // 쪽에도 의존하지 않는데 맨 뒤에 순차로 걸려 있었다 — getPost와 함께
  // 병렬로 시작한다((public)/status/page.tsx와 같은 패턴). getCommentPage는
  // tombstone 여부를 post로 판정한 뒤에만 불러도 되므로(가려진 글엔 댓글을
  // 아예 안 보여줌) 그대로 순차 유지한다 — 투기적으로 병렬화하면 tombstone
  // 글에도 댓글 요청이 나가는 낭비가 생긴다.
  let post;
  let nonce;
  try {
    [post, nonce] = await Promise.all([
      getPost(id),
      headers().then((h) => h.get(NONCE_HEADER) ?? undefined),
    ]);
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

  /**
   * 댓글 실패가 글 읽기를 막지 않는다 — 부수 데이터라 인라인으로 흡수한다(§7.6).
   * 본문은 이미 있으므로 여기서 5xx를 내면 읽을 수 있었던 글을 못 읽게 만든다.
   */
  const comments = await getCommentPage(id).catch(() => null);

  return (
    <div className="stack">
      <Breadcrumb
        items={[
          { label: "홈", href: ROUTES.home },
          { label: "커뮤니티", href: ROUTES.community },
        ]}
        current={post.title}
      />

      <BlockedPostGate postId={post.id} ownerId={post.ownerId}>
        <article className={styles.postBody}>
          <header className="stack">
            <Badge tone="neutral" size="sm" label={CATEGORY_LABELS[post.category]} />
            <h1>{post.title}</h1>
            <p className={styles.postMeta}>
              <span>{post.authorNickname}</span>
              <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time>
              <span>조회 {post.viewCount}</span>
            </p>
          </header>

          {/*
           * 본문은 dangerouslySetInnerHTML 없이 줄 단위 <p>로 렌더한다(§20.1).
           * 사용자가 쓴 문자열을 HTML로 해석하는 순간 XSS 경로가 열린다 — 서식이 필요해도
           * 이 방식을 먼저 유지하고, 정말 필요하면 허용 목록 기반 변환을 별도로 검토한다.
           */}
          {post.content.split("\n").map((line, index) => (
            <p key={`${index}-${line.slice(0, 12)}`}>{line === "" ? " " : line}</p>
          ))}
        </article>
      </BlockedPostGate>

      {post.recommendationAttachment !== null && (
        <RecommendationAttachmentView attachment={post.recommendationAttachment} />
      )}

      <ReactionBar postId={post.id} initialLikeCount={post.likeCount} />

      <div className={styles.postActions}>
        <PostOwnerActions postId={post.id} ownerId={post.ownerId} version={post.version} />
        <ReportGate ownerId={post.ownerId}>
          <ReportDialog targetType="POST" targetId={post.id} label="이 글 신고" />
        </ReportGate>
        <BlockButton postId={post.id} ownerId={post.ownerId} />
      </div>

      {comments === null ? (
        // KF-23(docs/improvement.md): 평문 <p>라 댓글 로드 실패가 스크린리더에
        // 무성이었다 — role="alert"를 주는 InlineAlert로 바꾼다.
        <InlineAlert tone="danger">지금은 댓글을 불러올 수 없습니다.</InlineAlert>
      ) : (
        <CommentSection postId={post.id} initialPage={comments} />
      )}

      <JsonLd
        nonce={nonce}
        data={{
          "@context": "https://schema.org",
          "@type": "DiscussionForumPosting",
          headline: post.title,
          articleBody: post.content,
          datePublished: post.createdAt,
          dateModified: post.updatedAt,
          author: { "@type": "Person", name: post.authorNickname },
          url: `${publicEnv.baseUrl}${ROUTES.communityPost(post.id)}`,
          interactionStatistic: [
            {
              "@type": "InteractionCounter",
              interactionType: "https://schema.org/LikeAction",
              userInteractionCount: post.likeCount,
            },
            {
              "@type": "InteractionCounter",
              interactionType: "https://schema.org/CommentAction",
              userInteractionCount: post.commentCount,
            },
          ],
        }}
      />
    </div>
  );
}
