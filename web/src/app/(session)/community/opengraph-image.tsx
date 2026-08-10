import { ImageResponse } from "next/og";

import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function CommunityOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="커뮤니티"
      title="조합 공유와 회차 분석을 나눠요"
      description="추천 조합, 당첨 후기, 질문을 자유롭게 올리는 공간입니다"
    />,
    { ...size, fonts },
  );
}
