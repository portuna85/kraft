import { ImageResponse } from "next/og";
import { getOgFontConfig } from "@/lib/og-font-node";
import { OgFrame } from "@/lib/og-frame";

export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const BARS = [82, 74, 68, 61, 55, 47];

export default async function Image() {
  const { fonts, fontFamily } = await getOgFontConfig();

  return new ImageResponse(
    (
      <OgFrame fontFamily={fontFamily}>
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
      </OgFrame>
    ),
    { ...size, fonts },
  );
}
