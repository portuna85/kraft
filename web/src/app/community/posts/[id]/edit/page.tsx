import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getCommunityPostFresh } from "@/lib/community-api";
import { BackendError } from "@/lib/api";
import { PostForm } from "@/features/community/post-form";

type Props = { params: Promise<{ id: string }> };

export const metadata: Metadata = {
  title: "게시글 수정",
  robots: { index: false, follow: false },
};

export default async function CommunityPostEditPage({ params }: Props) {
  const { id } = await params;
  const postId = Number(id);
  if (!Number.isInteger(postId) || postId <= 0) {
    notFound();
  }

  let post;
  try {
    post = await getCommunityPostFresh(postId);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  return (
    <section className="panel">
      <p className="eyebrow">커뮤니티</p>
      <h1 className="page-title">게시글 수정</h1>
      <PostForm
        mode="edit"
        postId={post.id}
        ownerId={post.ownerId}
        initialTitle={post.title}
        initialContent={post.content}
        initialVersion={post.version}
      />
    </section>
  );
}
