import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getCommunityPost } from "@/lib/community-api";
import { BackendError } from "@/lib/api";
import { PostForm } from "@/features/community/post-form";
import { PageHeader } from "@/components/page-header";

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
    post = await getCommunityPost(postId);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  return (
    <section className="panel">
      <PageHeader eyebrow="커뮤니티" title="게시글 수정" />
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
