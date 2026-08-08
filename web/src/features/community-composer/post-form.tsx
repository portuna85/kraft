"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import {
  CONTENT_MAX_LENGTH,
  createPost,
  fetchLatestPost,
  postFormSchema,
  TITLE_MAX_LENGTH,
  updatePost,
  type PostFormValues,
} from "@/entities/community-post/composer";
import {
  CATEGORY_LABELS,
  POST_CATEGORIES,
  type CommunityPost,
} from "@/entities/community-post/schema";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { ApiError } from "@/shared/api/error";
import { ROUTES } from "@/shared/config/routes";
import { useForm } from "@/shared/hooks/use-form";
import { Button } from "@/shared/ui/button";
import { Select, TextArea, TextField } from "@/shared/ui/field";
import { InlineAlert } from "@/shared/ui/states";

import styles from "./composer.module.css";

/**
 * 글 작성·수정 폼 — improvement_fe.md §16, §23.11
 *
 * 작성과 수정이 같은 컴포넌트인 이유는 필드가 같기 때문이다. 다른 것은 셋뿐이다:
 * 분류는 작성할 때만 고를 수 있고, 수정은 expectedVersion을 싣고, 409 충돌 처리가
 * 수정에만 있다.
 */
export function PostForm({ existing }: { existing?: CommunityPost }) {
  const router = useRouter();
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);

  /** 수정 중 다른 곳에서 글이 바뀌면 이 버전이 최신으로 갱신된다. */
  const [version, setVersion] = useState(existing?.version ?? 0);
  const [conflict, setConflict] = useState<CommunityPost | null>(null);

  const form = useForm<PostFormValues>({
    initialValues: {
      title: existing?.title ?? "",
      content: existing?.content ?? "",
      category: existing?.category ?? "GENERAL",
    },
    schema: postFormSchema,
    onSubmit: async (values) => {
      if (existing === undefined) {
        const created = await createPost(values, null);
        router.push(ROUTES.communityPost(created.id));
        router.refresh();
        return;
      }

      try {
        await updatePost(existing.id, values, version);
        router.push(ROUTES.communityPost(existing.id));
        router.refresh();
      } catch (cause) {
        if (cause instanceof ApiError && cause.isConflict) {
          /**
           * 409 — 입력을 **보존한 채** 최신을 가져와 나란히 보여준다(레거시 FE-066).
           *
           * 여기서 폼을 최신 내용으로 덮으면 사용자가 방금 쓴 글이 사라진다. 무엇을
           * 남길지는 사용자가 정할 일이다.
           */
          const latest = await fetchLatestPost(existing.id);
          setConflict(latest);
          setVersion(latest.version);
          throw new ApiError(
            "client",
            "다른 곳에서 이 글이 수정됐습니다. 아래 최신 내용을 확인한 뒤 다시 저장해 주세요.",
            { status: 409 },
          );
        }
        throw cause;
      }
    },
  });

  /** 쓰다 만 글을 실수로 날리지 않게 한다(§23.11 불변식). */
  useEffect(() => {
    if (!form.state.isDirty) return;

    function onBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
    }
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [form.state.isDirty]);

  if (!loggedIn) {
    return (
      <InlineAlert tone="neutral" title="로그인이 필요합니다">
        글을 쓰려면 먼저 로그인해 주세요. 로그인하면 이 화면으로 돌아옵니다.
      </InlineAlert>
    );
  }

  const titleField = form.field("title");
  const contentField = form.field("content");

  return (
    <form className="stack" onSubmit={form.handleSubmit} noValidate>
      <TextField
        label="제목"
        name="title"
        maxLength={TITLE_MAX_LENGTH}
        value={titleField.value}
        onChange={(event) => titleField.onChange(event.target.value)}
        onBlur={titleField.onBlur}
        error={titleField.error}
      />

      {existing === undefined && (
        <Select
          label="분류"
          name="category"
          value={form.state.values.category}
          onChange={(event) =>
            form.setValue("category", event.target.value as PostFormValues["category"])
          }
        >
          {POST_CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {CATEGORY_LABELS[category]}
            </option>
          ))}
        </Select>
      )}

      <TextArea
        label="내용"
        name="content"
        rows={12}
        maxLength={CONTENT_MAX_LENGTH}
        value={contentField.value}
        onChange={(event) => contentField.onChange(event.target.value)}
        onBlur={contentField.onBlur}
        error={contentField.error}
        hint={`${form.state.values.content.length} / ${CONTENT_MAX_LENGTH}자`}
      />

      {form.state.formError !== null && (
        <InlineAlert tone="danger">{form.state.formError}</InlineAlert>
      )}

      {conflict !== null && (
        <section className={styles.conflict} aria-labelledby="conflict">
          <h2 id="conflict">현재 저장된 내용</h2>
          <p className={styles.note}>
            위에 쓰신 내용은 그대로 있습니다. 아래는 다른 곳에서 저장된 최신 내용입니다.
          </p>
          <h3>{conflict.title}</h3>
          {conflict.content.split("\n").map((line, index) => (
            <p key={`${index}-${line.slice(0, 12)}`}>{line === "" ? " " : line}</p>
          ))}
        </section>
      )}

      <div className={styles.actions}>
        {form.state.isSubmitting ? (
          <Button type="submit" loading loadingLabel="저장 중">
            저장
          </Button>
        ) : (
          <Button type="submit">저장</Button>
        )}
        <Button type="button" variant="quiet" onClick={() => router.back()}>
          취소
        </Button>
      </div>
    </form>
  );
}
