"use client";

import { useEffect, useState } from "react";
import { useCommunitySession } from "@/lib/community-session-provider";
import {
  bookmarkPost,
  getMyInteractions,
  likePost,
  unbookmarkPost,
  unlikePost,
} from "@/lib/community-client";

export function ReactionBar({ postId, initialLikeCount }: { postId: number; initialLikeCount: number }) {
  const { session } = useCommunitySession();
  const [likeCount, setLikeCount] = useState(initialLikeCount);
  const [liked, setLiked] = useState(false);
  const [bookmarked, setBookmarked] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!session?.loggedIn) return;
    let cancelled = false;
    getMyInteractions([postId])
      .then((result) => {
        if (cancelled) return;
        setLiked(result.likedPostIds.includes(postId));
        setBookmarked(result.bookmarkedPostIds.includes(postId));
      })
      .catch(() => {
        // 개인 반응 상태 조회 실패는 조용히 무시 — 공개 집계(likeCount)는 이미 표시돼 있다.
      });
    return () => {
      cancelled = true;
    };
  }, [session?.loggedIn, postId]);

  async function toggleLike() {
    if (!session?.loggedIn) {
      setMessage("로그인 후 이용할 수 있습니다.");
      return;
    }
    const wasLiked = liked;
    setLiked(!wasLiked);
    setLikeCount((count) => count + (wasLiked ? -1 : 1));
    try {
      await (wasLiked ? unlikePost(postId) : likePost(postId));
    } catch {
      setLiked(wasLiked);
      setLikeCount((count) => count + (wasLiked ? 1 : -1));
      setMessage("처리하지 못했습니다. 잠시 후 다시 시도하세요.");
    }
  }

  async function toggleBookmark() {
    if (!session?.loggedIn) {
      setMessage("로그인 후 이용할 수 있습니다.");
      return;
    }
    const wasBookmarked = bookmarked;
    setBookmarked(!wasBookmarked);
    try {
      await (wasBookmarked ? unbookmarkPost(postId) : bookmarkPost(postId));
    } catch {
      setBookmarked(wasBookmarked);
      setMessage("처리하지 못했습니다. 잠시 후 다시 시도하세요.");
    }
  }

  return (
    <div className="community-reaction-bar">
      <button type="button" onClick={toggleLike} aria-pressed={liked}>
        {liked ? "좋아요 취소" : "좋아요"} {likeCount}
      </button>
      <button type="button" onClick={toggleBookmark} aria-pressed={bookmarked}>
        {bookmarked ? "북마크 해제" : "북마크"}
      </button>
      {message ? (
        <span role="status" aria-live="polite">
          {message}
        </span>
      ) : null}
    </div>
  );
}
