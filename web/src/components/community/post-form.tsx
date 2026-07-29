"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { createPost, updatePost } from "@/lib/community-client";
import { BrowserApiError } from "@/lib/browser-api";
import { useCommunitySession } from "@/components/community/community-session-provider";
import { CATEGORY_LABELS, CATEGORY_OPTIONS } from "@/features/community/types";
import { RecommendationAttachmentPicker } from "@/features/community/recommendation-attachment-picker";
import type { PostCategory } from "@/lib/community-api";

type CreateMode = { mode: "create" };
type EditMode = { mode: "edit"; postId: number; ownerId: number; initialTitle: string; initialContent: string; initialVersion: number };

export function PostForm(props: CreateMode | EditMode) {
  const router = useRouter();
  const { session, loading } = useCommunitySession();
  const [title, setTitle] = useState(props.mode === "edit" ? props.initialTitle : "");
  const [content, setContent] = useState(props.mode === "edit" ? props.initialContent : "");
  const [category, setCategory] = useState<PostCategory>("GENERAL");
  const [recommendationSetId, setRecommendationSetId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);

  const allowed = Boolean(
    session?.loggedIn && (props.mode === "create" || session.userId === props.ownerId)
  );

  // F-04: router와 props는 매 렌더 새 참조라 deps에 넣으면 렌더될 때마다 이 인가
  // 판단이 재실행돼 router.replace/push가 반복 호출된다. 리다이렉트 여부는 오직
  // loading·session이 바뀔 때만 다시 판단하면 되므로 그 둘만 감시한다(회귀 테스트:
  // community-post-form.test.tsx "session이 바뀌지 않으면 입력값 변경으로 리렌더링돼도
  // 리다이렉트를 다시 판단하지 않는다").
  useEffect(() => {
    if (loading || !session) return;
    if (!session.loggedIn) {
      router.replace("/community");
      return;
    }
    if (props.mode === "edit" && session.userId !== props.ownerId) {
      router.replace(`/community/posts/${props.postId}`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, session]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    // 중복 제출 방지 — 진행 중이면 재클릭·중복 Enter를 무시한다.
    if (submitting || !title.trim() || !content.trim()) return;
    setSubmitting(true);
    setError(null);
    setVersionConflict(false);
    try {
      if (props.mode === "create") {
        const post = await createPost(title.trim(), content.trim(), category, recommendationSetId);
        router.push(`/community/posts/${post.id}`);
      } else {
        const post = await updatePost(props.postId, title.trim(), content.trim(), props.initialVersion);
        router.push(`/community/posts/${post.id}`);
      }
    } catch (err) {
      if (err instanceof BrowserApiError && err.code === "COMMUNITY_POST_VERSION_CONFLICT") {
        setVersionConflict(true);
      } else {
        setError("저장에 실패했습니다. 잠시 후 다시 시도하세요.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="community-post-form" aria-busy="true">
        <span className="skeleton-line skeleton-eyebrow" />
        <span className="skeleton-line skeleton-body" />
        <span className="skeleton-line skeleton-body" />
      </div>
    );
  }
  if (!allowed) {
    return null;
  }

  return (
    <form onSubmit={handleSubmit} className="community-post-form">
      {versionConflict && (
        <p role="alert" id="post-form-error" className="community-version-conflict">
          다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.
        </p>
      )}
      {error && (
        <p role="alert" id="post-form-error">
          {error}
        </p>
      )}

      {props.mode === "create" ? (
        <>
          <label htmlFor="post-category">카테고리</label>
          <select
            id="post-category"
            value={category}
            onChange={(event) => setCategory(event.target.value as PostCategory)}
          >
            {CATEGORY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {CATEGORY_LABELS[option]}
              </option>
            ))}
          </select>
          <RecommendationAttachmentPicker value={recommendationSetId} onChange={setRecommendationSetId} />
        </>
      ) : null}

      <label htmlFor="post-title">제목</label>
      <input
        id="post-title"
        value={title}
        maxLength={200}
        onChange={(event) => setTitle(event.target.value)}
        required
        aria-invalid={error || versionConflict ? "true" : undefined}
        aria-describedby={error || versionConflict ? "post-form-error" : undefined}
      />

      <label htmlFor="post-content">내용</label>
      <textarea
        id="post-content"
        value={content}
        maxLength={20000}
        onChange={(event) => setContent(event.target.value)}
        required
        aria-invalid={error || versionConflict ? "true" : undefined}
        aria-describedby={error || versionConflict ? "post-form-error" : undefined}
      />

      <button type="submit" disabled={submitting || !title.trim() || !content.trim()}>
        {submitting ? "저장 중…" : "저장"}
      </button>
    </form>
  );
}
