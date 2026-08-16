import type { Metadata } from "next";

import { PostForm } from "@/features/community-composer/post-form";
import { ROUTES } from "@/shared/config/routes";
import { Breadcrumb } from "@/shared/ui/navigation";

export const metadata: Metadata = {
  title: "글쓰기",
  description: "KRAFT Lotto 커뮤니티에 글을 씁니다.",
  alternates: { canonical: "/community/write" },
  // I-33: 수정 화면(edit/page.tsx)은 이미 noindex인데 글쓰기 화면만 빠져 있었다 —
  // 둘 다 개인 작성 흐름이라 색인 대상이 아니다.
  robots: { index: false, follow: false },
};

export default function CommunityWritePage() {
  return (
    <div className="stack">
      <Breadcrumb
        items={[
          { label: "홈", href: ROUTES.home },
          { label: "커뮤니티", href: ROUTES.community },
        ]}
        current="글쓰기"
      />

      <header className="prose stack">
        <h1>글쓰기</h1>
        <p>
          번호 추천 결과나 회차 분석을 나눠 주세요. 상업적 홍보와 개인정보가 담긴 글은{" "}
          {/* 규칙을 쓰기 직전에 알려 준다 — 다 쓴 뒤 삭제당하는 것보다 낫다. */}
          커뮤니티 이용규칙에 따라 삭제될 수 있습니다.
        </p>
      </header>

      <PostForm />
    </div>
  );
}
