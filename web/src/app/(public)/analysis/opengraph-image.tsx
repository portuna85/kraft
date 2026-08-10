import { ImageResponse } from "next/og";

import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function AnalysisOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="내 조합 진단"
      title="번호 6개, 어떤 조합인가요"
      description="홀짝 · 합계 · 연속 번호와 역대 1등 이력을 함께 확인합니다"
    />,
    { ...size, fonts },
  );
}
