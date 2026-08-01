import { ImageResponse } from "next/og";
import { getOgFontConfig } from "@/lib/og-font-node";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const BARS = [82, 74, 68, 61, 55, 47];

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

        <div style={{ display: "flex", fontSize: 48, fontWeight: 700, color: "#f7f9ff", letterSpacing: -1, marginBottom: 10 }}>번호 출현 통계</div>
        <div style={{ display: "flex", fontSize: 22, color: "#9aa7c7", marginBottom: 40 }}>과거 데이터의 상대적 분포를 탐색하는 도구</div>

        <div style={{ display: "flex", alignItems: "flex-end", gap: 18 }}>
          {BARS.map((pct, index) => (
            <div key={index} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              <div
                style={{
                  width: 52,
                  height: pct * 1.2,
                  background: `rgba(0,229,255,${0.3 + pct / 200})`,
                  borderRadius: "6px 6px 0 0",
                  display: "flex",
                }}
              />
              <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#7c4dff", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 20, fontWeight: 700, color: "#ffffff" }}>
                ·
              </div>
            </div>
          ))}
        </div>
      </div>
    ),
    { ...size, fonts },
  );
}
