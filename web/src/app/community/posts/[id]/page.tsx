import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { headers } from "next/headers";
import { getCommunityPost } from "@/lib/community-api";
import { BackendError, getPublicBaseUrl } from "@/lib/api";
import { PostOwnerActions } from "@/components/community/post-owner-actions";
import { CommentSection } from "@/features/community/comment-section";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { PageHeader } from "@/components/page-header";
import { formatDateTime } from "@/lib/format";
import { ReactionBar } from "@/features/community/reaction-bar";
import { ReportDialog } from "@/features/community/report-dialog";
import { BlockButton } from "@/features/community/block-button";
import { BlockedPostGate } from "@/features/community/blocked-post-gate";
import { RecommendationAttachmentView } from "@/features/community/recommendation-attachment-view";
import { CATEGORY_LABELS } from "@/features/community/types";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  try {
    const post = await getCommunityPost(Number(id));
    return { title: post.title, alternates: { canonical: `/community/posts/${id}` } };
  } catch {
    return { title: "게시글" };
  }
}

export default async function CommunityPostDetailPage({ params }: Props) {
  const { id } = await params;
  const postId = Number(id);
  if (!Number.isInteger(postId) || postId <= 0) {
    notFound();
  }

  let post;
  try {
    post = await getCommunityPost(postId);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();

  return (
    <article className="panel community-post-detail">
      <JsonLdBreadcrumb
        baseUrl={baseUrl}
        nonce={nonce}
        items={[{ name: "커뮤니티", item: `${baseUrl}/community` }, { name: post.title }]}
      />
      {/* C-1: 서버가 내려준 게시글은 공유 ISR 캐시라 사용자별 차단 여부를 모른다 — 클라이언트
          렌더링 시점에 게이트가 확인해 본문 대신 대체 상태를 보여준다. */}
      <BlockedPostGate ownerId={post.ownerId}>
        <PageHeader
          eyebrow={`${CATEGORY_LABELS[post.category]} · 커뮤니티`}
          title={post.title}
          meta={
            <p className="community-post-meta">
              <span>{post.authorNickname}</span>
              <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time>
            </p>
          }
        />
        <div className="community-post-content">
          {post.content.split("\n").map((line, index) => (
            // 콘텐츠는 XSS 방어를 위해 plain text로 렌더링하며 dangerouslySetInnerHTML을 사용하지 않는다.
            <p key={index}>{line}</p>
          ))}
        </div>

        {post.recommendationAttachment ? (
          <RecommendationAttachmentView attachment={post.recommendationAttachment} />
        ) : null}

        <ReactionBar postId={post.id} initialLikeCount={post.likeCount} />
        <ReportDialog targetType="POST" targetId={post.id} />
        <BlockButton userId={post.ownerId} />

        <PostOwnerActions postId={post.id} ownerId={post.ownerId} version={post.version} />
        <CommentSection postId={post.id} />
      </BlockedPostGate>
    </article>
  );
}
