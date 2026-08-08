"use client";

import { useEffect, useState } from "react";

import { bookmarkPost, getMyInteractions, likePost } from "@/entities/community-post/interactions";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { Button } from "@/shared/ui/button";

import styles from "./post-actions.module.css";

/**
 * 좋아요·북마크 — improvement_fe.md §23.10, §14
 *
 * 초기 좋아요 수는 SSR된 게시글에서 오지만 **내가 눌렀는지는 거기 없다.** 공개 ISR
 * HTML에 사용자 상태를 넣지 않기 때문이다(§5.4). 그래서 마운트 후 한 번 물어본다.
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

  const [liked, setLiked] = useState(false);
  const [bookmarked, setBookmarked] = useState(false);
  const [likeCount, setLikeCount] = useState(initialLikeCount);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!loggedIn) return;

    let cancelled = false;
    getMyInteractions([postId])
      .then((interactions) => {
        if (cancelled) return;
        setLiked(interactions.likedPostIds.includes(postId));
        setBookmarked(interactions.bookmarkedPostIds.includes(postId));
      })
      // 내 반응 상태를 못 읽은 것은 글 읽기를 막을 이유가 아니다. 버튼은 누르지 않은
      // 상태로 두고, 실제로 누를 때 서버가 판단한다.
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [postId, loggedIn]);

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
      <p className={styles.note}>
        좋아요 {likeCount}개 · 로그인하면 좋아요와 북마크를 남길 수 있습니다.
      </p>
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
