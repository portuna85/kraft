import { ImageResponse } from "next/og";

import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function CompanionOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="함께 나온 번호"
      title="자주 같이 나온 번호 쌍"
      description="쌍당 평균 동반 출현과 비교해 상위 쌍을 보여줍니다"
    />,
    { ...size, fonts },
  );
}
