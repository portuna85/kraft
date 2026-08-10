import { ImageResponse } from "next/og";

import { getLatestRound } from "@/entities/round/api";
import { getOgFontConfig } from "@/shared/lib/og-font";
import { OgFrame } from "@/shared/ui/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const BALL_COLORS = [
  { bg: "#fbc400", fg: "#2f2608" },
  { bg: "#69c8f2", fg: "#04101b" },
  { bg: "#ff7272", fg: "#2f0808" },
  { bg: "#aaaaaa", fg: "#1a1a1a" },
  { bg: "#b0d840", fg: "#1a2408" },
  { bg: "#168b68", fg: "#ffffff" },
];

export default async function HomeOgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  // 핵심 데이터라도 OG 이미지 생성 실패가 페이지 자체를 막지 않는다 — 최신 회차를
  // 못 가져오면 일반 소개 이미지로 대체한다.
  const latest = await getLatestRound().catch(() => null);

  return new ImageResponse(
    <OgFrame
      fontFamily={fontFamily}
      eyebrow="KRAFT LOTTO"
      title={latest === null ? "로또 6/45 당첨 결과와 번호 추천" : `${latest.round}회 당첨 번호`}
      description="당첨 결과 조회 · 통계 분석 · 번호 추천 · 모든 조합의 당첨 확률은 같습니다"
    >
      {latest !== null && (
        <div style={{ display: "flex", gap: 16 }}>
          {latest.numbers.map((n, index) => (
            <div
              key={index}
              style={{
                width: 84,
                height: 84,
                borderRadius: "50%",
                background: BALL_COLORS[index]?.bg,
                color: BALL_COLORS[index]?.fg,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 32,
                fontWeight: 700,
              }}
            >
              {n}
            </div>
          ))}
          <div
            style={{
              width: 84,
              height: 84,
              borderRadius: "50%",
              background: "#168b68",
              color: "#ffffff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 32,
              fontWeight: 700,
              border: "3px solid #f7f9ff",
            }}
          >
            {latest.bonusNumber}
          </div>
        </div>
      )}
    </OgFrame>,
    { ...size, fonts },
  );
}
