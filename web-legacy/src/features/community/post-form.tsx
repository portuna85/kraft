"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createPost, fetchCommunityPost, loginUrl, updatePost } from "@/lib/community-client";
import { revalidateCommunityPost } from "@/lib/community-revalidate";
import { BrowserApiError } from "@/lib/browser-api";
import { useCommunitySession } from "@/lib/community-session-provider";
import { saveReturnTo } from "@/lib/return-to";
import { CATEGORY_LABELS, CATEGORY_OPTIONS } from "@/features/community/types";
import { RecommendationAttachmentPicker } from "@/features/community/recommendation-attachment-picker";
import type { PostCategory } from "@/lib/community-api";
import { Button } from "@/ui/primitives/button";
import { InlineAlert } from "@/ui/primitives/inline-alert";
import { LoginLinks } from "./login-links";
import { TextArea } from "@/ui/primitives/text-area";
import { TextField } from "@/ui/primitives/text-field";

type CreateMode = { mode: "create" };
type EditMode = { mode: "edit"; postId: number; ownerId: number; initialTitle: string; initialContent: string; initialVersion: number };

export function PostForm(props: CreateMode | EditMode) {
  const router = useRouter();
  const pathname = usePathname();
  const { session, loading, error: sessionError, retry } = useCommunitySession();
  const [title, setTitle] = useState(props.mode === "edit" ? props.initialTitle : "");
  const [content, setContent] = useState(props.mode === "edit" ? props.initialContent : "");
  const [category, setCategory] = useState<PostCategory>("GENERAL");
  const [recommendationSetId, setRecommendationSetId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // FE-066: 충돌 안내가 "새로고침한 뒤 다시 작성해 주세요"였는데, 새로고침하면 작성 중이던
  // 내용이 사라진다. 입력값은 그대로 두고 최신 version만 다시 받아 이어서 저장할 수 있게 한다.
  const [versionConflict, setVersionConflict] = useState(false);
  const [resolvingConflict, setResolvingConflict] = useState(false);
  const [latestRemoteTitle, setLatestRemoteTitle] = useState<string | null>(null);
  // 저장에 실을 버전. 충돌 후 최신 값으로 갱신되므로 props를 그대로 쓰지 않는다.
  const [version, setVersion] = useState(props.mode === "edit" ? props.initialVersion : 0);

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
        const post = await updatePost(props.postId, title.trim(), content.trim(), version);
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

  /**
   * 충돌 복구: 입력값은 건드리지 않고 최신 version만 받아 온다. 상대가 무엇으로 바꿨는지
   * 제목으로 알려 주어, 그대로 덮어쓸지 사용자가 판단할 수 있게 한다.
   */
  const resolveConflict = async () => {
    if (props.mode !== "edit" || resolvingConflict) return;
    setResolvingConflict(true);
    setError(null);
    try {
      const latest = await fetchCommunityPost(props.postId);
      setVersion(latest.version);
      setLatestRemoteTitle(latest.title);
      setVersionConflict(false);
    } catch {
      setError("최신 내용을 불러오지 못했습니다. 잠시 후 다시 시도하세요.");
    } finally {
      setResolvingConflict(false);
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
  // FE-005: 세션 조회 실패를 비로그인으로 표시하면 provider 목록이 비어 로그인 링크조차
  // 사라진다 — 사용자가 아무것도 할 수 없게 되므로 실패는 실패로 알리고 재시도를 준다.
  if (sessionError) {
    return (
      <div className="community-post-form-login-required">
        <InlineAlert
          tone="danger"
          title="로그인 상태를 확인하지 못했습니다."
          description="네트워크 상태를 확인한 뒤 다시 시도해 주세요."
        />
        <button type="button" className="button secondary" onClick={retry}>
          다시 시도
        </button>
      </div>
    );
  }
  if (!session?.loggedIn) {
    const providers = session?.activeProviders ?? [];
    return (
      <div className="community-post-form-login-required">
        <InlineAlert tone="neutral" title="이 기능을 사용하려면 로그인이 필요합니다." description="로그인 후 현재 화면으로 돌아와 계속 작성할 수 있습니다." />
        <LoginLinks providers={providers} />
      </div>
    );
  }
  if (props.mode === "edit" && session.userId !== props.ownerId) {
    return (
      <div className="community-post-form-forbidden">
        <InlineAlert tone="danger" title="본인이 작성한 글만 수정할 수 있습니다." />
        <Link href={`/community/posts/${props.postId}`} className="button secondary">게시글로 돌아가기</Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="community-post-form">
      {versionConflict ? (
        <div id="post-form-error">
          <InlineAlert
            tone="danger"
            title="다른 곳에서 먼저 수정되었습니다."
            description="작성 중인 내용은 그대로 있습니다. 최신 상태를 불러온 뒤 다시 저장하면 이 내용으로 덮어씁니다."
          />
          <button
            type="button"
            className="button secondary"
            onClick={resolveConflict}
            disabled={resolvingConflict}
          >
            {resolvingConflict ? "불러오는 중…" : "최신 상태 불러오기"}
          </button>
        </div>
      ) : null}
      {latestRemoteTitle !== null ? (
        <InlineAlert
          tone="neutral"
          title="최신 상태를 불러왔습니다."
          description={`현재 저장된 제목은 “${latestRemoteTitle}”입니다. 저장하면 작성 중인 내용으로 덮어씁니다.`}
        />
      ) : null}
      {error ? <div id="post-form-error"><InlineAlert tone="danger" title="저장에 실패했습니다." description={error} /></div> : null}

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

      <TextField
        id="post-title"
        label="제목"
        value={title}
        onChange={setTitle}
        maxLength={200}
        required
        invalid={Boolean(error || versionConflict)}
        descriptionId="post-title-count"
        errorMessageId={error || versionConflict ? "post-form-error" : undefined}
      />
      <p id="post-title-count" className="community-field-hint">{title.length}/200</p>

      <TextArea
        id="post-content"
        label="내용"
        value={content}
        onChange={setContent}
        maxLength={20000}
        rows={10}
        required
        invalid={Boolean(error || versionConflict)}
        descriptionId="post-content-count"
        errorMessageId={error || versionConflict ? "post-form-error" : undefined}
      />
      <p id="post-content-count" className="community-field-hint">{content.length}/20,000</p>

      <Button type="submit" variant="primary" loading={submitting} loadingLabel="저장 중…" disabled={!title.trim() || !content.trim()}>
        저장
      </Button>
    </form>
  );
}
