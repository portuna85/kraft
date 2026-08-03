"use client";

import Link from "next/link";
import { formatDateTime } from "@/lib/format";
import { useBlockedUserIds } from "@/features/community/blocked-users-context";
import { CATEGORY_LABELS } from "@/features/community/types";
import type { CommunityPost } from "@/lib/community-api";

/**
 * C-1: 서버(ISR 공유 캐시)가 내려준 목록은 사용자별 개인화가 섞이지 않은 그대로다
 * (community-api.ts:9-11 계약 유지). 차단 필터링은 여기, 클라이언트 렌더링 시점에만
 * 적용한다 — UI가 "게시글과 댓글이 보이지 않습니다"라고 약속한 부분을 지킨다.
 * 서버 쿼리를 바꾸지 않으므로, 한 페이지의 항목 전부가 차단 대상이면 그 페이지는
 * 빈 목록으로 보일 수 있다(알려진 트레이드오프 — 페이지네이션 정합성보다 캐시 계약을 우선).
 */
export function CommunityPostList({ items }: { items: CommunityPost[] }) {
  const { isBlocked } = useBlockedUserIds();
  const visibleItems = items.filter((post) => !isBlocked(post.ownerId));

  return (
    <ul className="community-post-list">
      {visibleItems.map((post) => (
        <li key={post.id} className="community-post-list-item">
          <span className="community-post-category">{CATEGORY_LABELS[post.category]}</span>
          <Link href={`/community/posts/${post.id}`}>{post.title}</Link>
          <span className="community-post-author">{post.authorNickname}</span>
          {/* FE-047: 홈 커뮤니티 섹션은 날짜를 보여주는데 목록에는 없어
              최신성 판단이 불가능했다. */}
          <time className="community-post-date" dateTime={post.createdAt}>
            {formatDateTime(post.createdAt)}
          </time>
          <span className="community-post-counts">
            좋아요 {post.likeCount} · 댓글 {post.commentCount} · 조회 {post.viewCount}
          </span>
        </li>
      ))}
    </ul>
  );
}
