import { ImageResponse } from "next/og";

import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function RecommendOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="번호 추천"
      title="조합을 만들고 저장해 보세요"
      description="무작위 · 균형 조합 · 당첨금 분산 줄이기 — 모든 조합의 확률은 같습니다"
    />,
    { ...size, fonts },
  );
}
