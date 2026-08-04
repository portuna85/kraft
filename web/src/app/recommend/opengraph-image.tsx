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

export default async function Image() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    (
      <OgFrame fontFamily={fontFamily}>
        <div style={{ display: "flex", fontSize: 48, fontWeight: 700, color: "#f7f9ff", letterSpacing: -1, marginBottom: 10 }}>번호 추천</div>
        <div style={{ display: "flex", fontSize: 22, color: "#9aa7c7", marginBottom: 40 }}>모든 유효한 조합의 당첨 확률은 같습니다</div>

        <div style={{ display: "flex", gap: 16 }}>
          {BALLS.map((ball, i) => (
            <div
              key={i}
              style={{
                width: 92,
                height: 92,
                borderRadius: "50%",
                background: ball.bg,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: ball.fg,
                fontSize: 38,
                fontWeight: 700,
                boxShadow: "0 6px 20px rgba(0,0,0,0.14)",
                opacity: 0.85 + i * 0.025,
              }}
            >
              {ball.n}
            </div>
          ))}
        </div>
      </OgFrame>
    ),
    { ...size, fonts },
  );
}
