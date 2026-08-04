import { ImageResponse } from "next/og";
import { getOgFontConfig } from "@/lib/og-font-node";
import { OgFrame } from "@/lib/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const BALLS = [
  { n: "?", bg: "#00e5ff", fg: "#050816" },
  { n: "?", bg: "#7c4dff", fg: "#ffffff" },
  { n: "?", bg: "#40f0b5", fg: "#050816" },
  { n: "?", bg: "#2563eb", fg: "#ffffff" },
  { n: "?", bg: "#00b8cc", fg: "#050816" },
  { n: "?", bg: "#5f6f95", fg: "#ffffff" },
];

export default async function OgImage() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    (
      <OgFrame
        fontFamily={fontFamily}
        badgeSize={56}
        badgeRadius={14}
        topCircleSize={520}
        topCircleOffset={-140}
        bottomCircleSize={380}
        bottomCircleOffset={-100}
      >
        <div style={{ display: "flex", gap: 18, marginBottom: 44 }}>
          {BALLS.map((ball, index) => (
            <div
              // L-10: n이 전부 "?"라 ball.n을 key로 쓰면 React key가 중복된다 — BALLS는
              // 고정된 정적 배열(순서가 안 바뀜)이라 인덱스가 안전한 안정적 식별자다.
              key={index}
              style={{
                width: 92,
                height: 92,
                borderRadius: "50%",
                background: ball.bg,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: ball.fg,
                fontSize: 34,
                fontWeight: 700,
                boxShadow: "0 8px 24px rgba(0,0,0,0.16)",
              }}
            >
              {ball.n}
            </div>
          ))}
        </div>

        <div style={{ display: "flex", fontSize: 30, fontWeight: 700, color: "#f7f9ff", marginBottom: 14, letterSpacing: -0.5 }}>
          로또 6/45 당첨 번호 · 통계 · 번호 추천
        </div>
        <div style={{ display: "flex", fontSize: 22, color: "#9aa7c7" }}>kraft.io.kr · 모든 조합의 당첨 확률은 같습니다</div>
      </OgFrame>
    ),
    { ...size, fonts },
  );
}
