import type { Metadata } from "next";
import { PostForm } from "@/features/community/post-form";
import { PageHeader } from "@/components/page-header";

export const metadata: Metadata = {
  title: "글쓰기",
  robots: { index: false, follow: false },
  alternates: { canonical: "/community/write" },
};

export default function CommunityWritePage() {
  return (
    <section className="panel">
      <PageHeader eyebrow="커뮤니티" title="글쓰기" />
      <PostForm mode="create" />
    </section>
  );
}
