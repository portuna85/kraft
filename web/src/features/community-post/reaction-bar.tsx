"use client";

import { useState } from "react";

import { bookmarkPost, likePost } from "@/entities/community-post/interactions";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { Button } from "@/shared/ui/button";

import { usePostInteractions } from "./use-post-interactions";
import styles from "./post-actions.module.css";

/**
 * 좋아요·북마크
 *
 * 초기 좋아요 수는 SSR된 게시글에서 오지만 **내가 눌렀는지는 거기 없다.** 공개 ISR
 * HTML에 사용자 상태를 넣지 않기 때문이다(§5.4). 그래서 마운트 후 한 번 물어본다 —
 * FE-DATA-01(docs/improvement.md): `BlockedPostGate`·`BlockButton`과 같은
 * `me:interactions:${postId}` 리소스를 공유해 이 페이지에서 실제 네트워크 요청이
 * 1회로 합쳐진다(`use-post-interactions.ts`).
 *
 * 토글은 낙관적으로 반영한다 — 왕복을 기다리면 누른 느낌이 나지 않는다. 실패하면
 * 되돌리고 이유를 말한다. 조용히 되돌리면 눌렀는데 안 눌린 것처럼 보인다.
 */
export function ReactionBar({
  postId,
  initialLikeCount,
}: {
  postId: number;
  initialLikeCount: number;
}) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const interactions = usePostInteractions(postId, loggedIn);

  const [liked, setLiked] = useState(false);
  const [bookmarked, setBookmarked] = useState(false);
  const [likeCount, setLikeCount] = useState(initialLikeCount);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 렌더 중 상태 조정(React 공식 패턴) — 이 postId의 서버 응답을 아직 반영하지
  // 않았다면(최초 로딩 완료 시점) 한 번만 좋아요·북마크 초기값을 심는다. 이후
  // 로그인 사용자가 직접 누른 낙관적 상태는 이 값이 다시 덮어쓰지 않는다. effect
  // 대신 렌더 중 setState를 쓰는 이유는 이후 낙관적 토글 렌더에서 이 조정이
  // 다시 실행되지 않아야 하기 때문이다(effect였다면 `interactions`가 안 바뀌므로
  // 결과는 같지만, 셋스테이트-인-이펙트 린트 규칙이 이 패턴 자체를 금지한다).
  const [seededPostId, setSeededPostId] = useState<number | null>(null);
  if (interactions !== null && seededPostId !== postId) {
    setSeededPostId(postId);
    setLiked(interactions.likedPostIds.includes(postId));
    setBookmarked(interactions.bookmarkedPostIds.includes(postId));
  }

  async function toggleLike() {
    const next = !liked;
    setLiked(next);
    setLikeCount((count) => count + (next ? 1 : -1));
    setPending(true);
    setError(null);

    try {
      await likePost(postId, next);
    } catch {
      setLiked(!next);
      setLikeCount((count) => count + (next ? -1 : 1));
      setError("좋아요를 반영하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  }

  async function toggleBookmark() {
    const next = !bookmarked;
    setBookmarked(next);
    setPending(true);
    setError(null);

    try {
      await bookmarkPost(postId, next);
    } catch {
      setBookmarked(!next);
      setError("북마크를 반영하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  }

  if (!loggedIn) {
    return (
      <p className="note">좋아요 {likeCount}개 · 로그인하면 좋아요와 북마크를 남길 수 있습니다.</p>
    );
  }

  return (
    <div className={styles.bar}>
      <Button
        variant={liked ? "primary" : "secondary"}
        onClick={toggleLike}
        disabled={pending}
        aria-pressed={liked}
      >
        좋아요 {likeCount}
      </Button>
      <Button
        variant={bookmarked ? "primary" : "secondary"}
        onClick={toggleBookmark}
        disabled={pending}
        aria-pressed={bookmarked}
      >
        {bookmarked ? "북마크됨" : "북마크"}
      </Button>
      {error !== null && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
