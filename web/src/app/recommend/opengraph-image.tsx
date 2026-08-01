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

export default async function Image() {
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
        <div style={{ position: "absolute", top: -120, right: -120, width: 480, height: 480, borderRadius: "50%", background: "rgba(0,229,255,0.12)", display: "flex" }} />
        <div style={{ position: "absolute", bottom: -80, left: -80, width: 340, height: 340, borderRadius: "50%", background: "rgba(124,77,255,0.14)", display: "flex" }} />

        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 28 }}>
          <div style={{ width: 38, height: 38, borderRadius: 10, background: "linear-gradient(135deg, #00e5ff, #7c4dff)", display: "flex", alignItems: "center", justifyContent: "center", color: "#050816", fontSize: 19, fontWeight: 700 }}>K</div>
          <span style={{ fontSize: 26, fontWeight: 700, color: "#f7f9ff", letterSpacing: -0.5 }}>KRAFT LOTTO</span>
        </div>

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
      </div>
    ),
    { ...size, fonts },
  );
}
