"use client";

import { useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { createPost, loginUrl, updatePost } from "@/lib/community-client";
import { revalidateCommunityPost } from "@/lib/community-revalidate";
import { BrowserApiError } from "@/lib/browser-api";
import { useCommunitySession } from "@/lib/community-session-provider";
import { saveReturnTo } from "@/lib/return-to";
import { CATEGORY_LABELS, CATEGORY_OPTIONS } from "@/features/community/types";
import { RecommendationAttachmentPicker } from "@/features/community/recommendation-attachment-picker";
import type { PostCategory } from "@/lib/community-api";

const PROVIDER_LABELS: Record<"google" | "naver", string> = {
  google: "Google 로그인",
  naver: "Naver 로그인",
};

type CreateMode = { mode: "create" };
type EditMode = { mode: "edit"; postId: number; ownerId: number; initialTitle: string; initialContent: string; initialVersion: number };

export function PostForm(props: CreateMode | EditMode) {
  const router = useRouter();
  const pathname = usePathname();
  const { session, loading } = useCommunitySession();
  const [title, setTitle] = useState(props.mode === "edit" ? props.initialTitle : "");
  const [content, setContent] = useState(props.mode === "edit" ? props.initialContent : "");
  const [category, setCategory] = useState<PostCategory>("GENERAL");
  const [recommendationSetId, setRecommendationSetId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versionConflict, setVersionConflict] = useState(false);

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
        await revalidateCommunityPost(post.id);
        router.push(`/community/posts/${post.id}`);
        router.refresh();
      } else {
        const post = await updatePost(props.postId, title.trim(), content.trim(), props.initialVersion);
        await revalidateCommunityPost(post.id);
        router.push(`/community/posts/${post.id}`);
        router.refresh();
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
  if (!session?.loggedIn) {
    const providers = session?.activeProviders ?? [];
    return (
      <div className="community-post-form-login-required">
        <p>이 기능을 사용하려면 로그인이 필요합니다.</p>
        {providers.map((provider) => (
          <a
            key={provider}
            href={loginUrl(provider)}
            className="account-login-link"
            onClick={() => saveReturnTo(pathname)}
          >
            {PROVIDER_LABELS[provider]}
          </a>
        ))}
      </div>
    );
  }
  if (props.mode === "edit" && session.userId !== props.ownerId) {
    return (
      <div className="community-post-form-forbidden">
        <p role="alert">본인이 작성한 글만 수정할 수 있습니다.</p>
        <a href={`/community/posts/${props.postId}`}>게시글로 돌아가기</a>
      </div>
    );
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
