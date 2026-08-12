import Link from "next/link";

import { ROUTES } from "@/shared/config/routes";
import { Badge } from "@/shared/ui/badge";
import { Card } from "@/shared/ui/surface";

import { CATEGORY_LABELS, type CommunityPost } from "../schema";

import styles from "./post-summary-card.module.css";

/**
 * 목록 항목 — improvement_fe.md §9.4
 *
 * 카드 전체가 링크가 아니라 제목만 링크다. 카드를 통째로 감싸면 안에 있는 배지·작성자
 * 같은 텍스트가 전부 링크 이름에 섞여 낭독이 길어진다.
 *
 * 작성 일시를 노출하는 이유는 목록에서 최신성을 판단할 수 있어야 하기 때문이다
 * (레거시 FE-047).
 */
export function PostSummaryCard({ post }: { post: CommunityPost }) {
  return (
    <Card as="li" level={1}>
      <div className={styles.meta}>
        <Badge tone="neutral" size="sm" label={CATEGORY_LABELS[post.category]} />
        <span>{post.authorNickname}</span>
        <time dateTime={post.createdAt}>{post.createdAt.slice(0, 10)}</time>
      </div>

      <h3 className={styles.title}>
        <Link href={ROUTES.communityPost(post.id)}>{post.title}</Link>
      </h3>

      <p className={styles.counts}>
        <span>좋아요 {post.likeCount}</span>
        <span>댓글 {post.commentCount}</span>
        <span>조회 {post.viewCount}</span>
      </p>
    </Card>
  );
}
