import type { ReactNode } from "react";

/**
 * OG 이미지 공통 프레임 — improvement_fe.md §4.2
 *
 * 레거시는 이 배경·배지·문구 마크업을 라우트마다 복제했다(L-1). satori(next/og
 * 렌더러)는 CSS 클래스를 못 쓰고 인라인 style 트리만 받아, 공통 요소를 컴포넌트로
 * 뽑는 게 유일한 중복 제거 수단이다.
 *
 * 색은 shared/styles/tokens.css의 다크 팔레트 값을 그대로 썼다 — satori가 CSS
 * 커스텀 프로퍼티를 못 읽으므로 리터럴로 박아 둔다.
 */
export function OgFrame({
  fontFamily,
  eyebrow,
  title,
  description,
  children,
}: {
  fontFamily: string;
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div
      style={{
        width: 1200,
        height: 630,
        display: "flex",
        flexDirection: "column",
        padding: 72,
        background: "linear-gradient(145deg, #050816 0%, #111936 100%)",
        fontFamily,
        position: "relative",
      }}
    >
      <div
        style={{
          position: "absolute",
          top: -140,
          right: -140,
          width: 480,
          height: 480,
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
          width: 340,
          height: 340,
          borderRadius: "50%",
          background: "rgba(124, 77, 255, 0.14)",
          display: "flex",
        }}
      />

      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <div
          style={{
            width: 40,
            height: 40,
            borderRadius: 10,
            background: "linear-gradient(135deg, #00e5ff, #7c4dff)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 20,
            fontWeight: 700,
            color: "#04101b",
          }}
        >
          K
        </div>
        <span style={{ display: "flex", fontSize: 22, fontWeight: 700, color: "#9aa7c7" }}>
          {eyebrow}
        </span>
      </div>

      <div style={{ display: "flex", flexDirection: "column", flex: 1, justifyContent: "center" }}>
        <div
          style={{
            display: "flex",
            fontSize: 56,
            fontWeight: 700,
            color: "#f7f9ff",
            marginBottom: 20,
            letterSpacing: -0.5,
            lineHeight: 1.25,
          }}
        >
          {title}
        </div>

        {children}

        <div
          style={{ display: "flex", fontSize: 24, color: "#9aa7c7", marginTop: children ? 32 : 0 }}
        >
          {description}
        </div>
      </div>

      <div style={{ display: "flex", fontSize: 20, color: "#5b667a" }}>kraft.io.kr</div>
    </div>
  );
}
