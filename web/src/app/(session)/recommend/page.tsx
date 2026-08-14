import type { Metadata } from "next";

import { RecommendStudio } from "@/features/recommend-studio/recommend-studio";

export const metadata: Metadata = {
  title: "번호 추천",
  description:
    "전략을 고르고 고정·제외 번호를 지정해 조합을 만들어 보세요. 과거 당첨 데이터를 참고한 통계 결과입니다.",
  alternates: { canonical: "/recommend" },
};

/**
 * /recommend
 *
 * RSC 셸 + 클라이언트 스튜디오. 셸이 서버에서 그려지므로 제목·설명이 SSR 본문에 남고,
 * 상호작용이 필요한 부분만 클라이언트로 내려간다(§19.2 P-8).
 *
 * 이 라우트가 (session) 셸에 있는 이유는 저장 대상(계정/기기)을 정하려면 세션을 알아야
 * 하기 때문이다. 세션 스코프 경로 목록과도 일치한다(§14.5 I-4).
 */
export default function RecommendPage() {
  return (
    <div className="stack">
      <header className="prose stack">
        <h1>번호 추천</h1>
        <p>
          전략을 고르고, 꼭 넣고 싶은 번호나 빼고 싶은 번호를 지정해 조합을 만듭니다. 만든 조합은
          보관함에 저장해 당첨 여부를 나중에 대조할 수 있습니다.
        </p>
      </header>

      <RecommendStudio />
    </div>
  );
}
