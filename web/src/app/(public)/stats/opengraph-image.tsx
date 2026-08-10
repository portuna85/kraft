import { ImageResponse } from "next/og";

import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function StatsOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="당첨 패턴 통계"
      title="홀짝 · 고저 · 합계 분포"
      description="당첨 번호가 실제로 어떤 형태로 나왔는지 구간별로 보여줍니다"
    />,
    { ...size, fonts },
  );
}
