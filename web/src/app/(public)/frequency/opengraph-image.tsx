import { ImageResponse } from "next/og";

import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function FrequencyOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="번호별 출현 통계"
      title="1~45번, 얼마나 나왔을까"
      description="역대 회차 출현 횟수를 무작위 기대값과 비교합니다"
    />,
    { ...size, fonts },
  );
}
