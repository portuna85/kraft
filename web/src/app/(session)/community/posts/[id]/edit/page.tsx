import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { getPost } from "@/entities/community-post/api";
import { isTombstone } from "@/entities/community-post/schema";
import { PostForm } from "@/features/community-composer/post-form";
import { ApiError } from "@/shared/api/error";
import { ROUTES } from "@/shared/config/routes";
import { Breadcrumb } from "@/shared/ui/navigation";

export const metadata: Metadata = {
  title: "글 수정",
  robots: { index: false, follow: false },
};

/**
 * 수정 진입 시 **최신을 다시 읽는다** 불변식
 *
 * 캐시된 글로 폼을 채우면 그때 담긴 version도 옛 것이라, 저장할 때마다 409가 나거나
 * 더 나쁘게는 다른 사람이 고친 내용을 모르고 덮어쓴다. getPost 자체가 no-store다.
 */
export const dynamic = "force-dynamic";

function parseId(raw: string): number | null {
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

export default async function EditPostPage({ params }: { params: Promise<{ id: string }> }) {
  const id = parseId((await params).id);
  if (id === null) notFound();

  let post;
  try {
    post = await getPost(id);
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) notFound();
    throw cause;
  }

  // 지워진 글에는 수정할 내용이 없다.
  if (isTombstone(post)) notFound();

  return (
    <div className="stack">
      <Breadcrumb
        items={[
          { label: "홈", href: ROUTES.home },
          { label: "커뮤니티", href: ROUTES.community },
          { label: post.title, href: ROUTES.communityPost(post.id) },
        ]}
        current="수정"
      />

      <header className="prose stack">
        <h1>글 수정</h1>
      </header>

      <PostForm existing={post} />
    </div>
  );
}
