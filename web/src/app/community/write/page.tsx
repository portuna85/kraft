import type { Metadata } from "next";
import { PostForm } from "@/features/community/post-form";

export const metadata: Metadata = {
  title: "글쓰기",
  robots: { index: false, follow: false },
  alternates: { canonical: "/community/write" },
};

export default function CommunityWritePage() {
  return (
    <section className="panel">
      <p className="eyebrow">커뮤니티</p>
      <h1 className="page-title">글쓰기</h1>
      <PostForm mode="create" />
    </section>
  );
}
