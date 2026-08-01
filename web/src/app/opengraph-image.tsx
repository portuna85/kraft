import { ImageResponse } from "next/og";
import { getOgFontConfig } from "@/lib/og-font-node";

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
      <div
        style={{
          width: 1200,
          height: 630,
          background: "linear-gradient(145deg, #050816 0%, #111936 100%)",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          fontFamily,
          position: "relative",
        }}
      >
        <div
          style={{
            position: "absolute",
            top: -140,
            right: -140,
            width: 520,
            height: 520,
            borderRadius: "50%",
            background: "rgba(0, 229, 255, 0.12)",
            display: "flex",
          }}
        />
        <div
          style={{
            position: "absolute",
            bottom: -100,
            left: -100,
            width: 380,
            height: 380,
            borderRadius: "50%",
            background: "rgba(124, 77, 255, 0.14)",
            display: "flex",
          }}
        />

        <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 40 }}>
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: 14,
              background: "linear-gradient(135deg, #00e5ff, #7c4dff)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#050816",
              fontSize: 28,
              fontWeight: 700,
            }}
          >
            K
          </div>
          <span style={{ fontSize: 42, fontWeight: 700, color: "#f7f9ff", letterSpacing: -1 }}>
            KRAFT LOTTO
          </span>
        </div>

        <div style={{ display: "flex", gap: 18, marginBottom: 44 }}>
          {BALLS.map((ball) => (
            <div
              key={ball.n}
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
      </div>
    ),
    { ...size, fonts },
  );
}
