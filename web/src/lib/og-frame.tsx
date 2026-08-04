// L-1: 3개 OG 이미지 라우트(/, /frequency, /recommend)가 배경 그라디언트·원형
// 장식·로고 배지 마크업을 그대로 복제하고 있었다 — satori(next/og)는 CSS 클래스를
// 못 쓰는 인라인 style 트리라 흔한 추출 없이는 복붙이 유일한 선택지였다. 크기가
// 다른 두 변형(홈은 배지 56px·큰 원, 나머지는 배지 38px·작은 원)을 props로 흡수한다.
type OgFrameProps = {
  fontFamily: string;
  badgeSize?: number;
  badgeRadius?: number;
  topCircleSize?: number;
  topCircleOffset?: number;
  bottomCircleSize?: number;
  bottomCircleOffset?: number;
  children: React.ReactNode;
};

export function OgFrame({
  fontFamily,
  badgeSize = 38,
  badgeRadius = 10,
  topCircleSize = 480,
  topCircleOffset = -120,
  bottomCircleSize = 340,
  bottomCircleOffset = -80,
  children,
}: OgFrameProps) {
  const badgeFontSize = Math.round(badgeSize * 0.5);
  const compact = badgeSize < 50;

  return (
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
          top: topCircleOffset,
          right: topCircleOffset,
          width: topCircleSize,
          height: topCircleSize,
          borderRadius: "50%",
          background: "rgba(0, 229, 255, 0.12)",
          display: "flex",
        }}
      />
      <div
        style={{
          position: "absolute",
          bottom: bottomCircleOffset,
          left: bottomCircleOffset,
          width: bottomCircleSize,
          height: bottomCircleSize,
          borderRadius: "50%",
          background: "rgba(124, 77, 255, 0.14)",
          display: "flex",
        }}
      />

      <div style={{ display: "flex", alignItems: "center", gap: compact ? 10 : 14, marginBottom: compact ? 28 : 40 }}>
        <div
          style={{
            width: badgeSize,
            height: badgeSize,
            borderRadius: badgeRadius,
            background: "linear-gradient(135deg, #00e5ff, #7c4dff)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#050816",
            fontSize: badgeFontSize,
            fontWeight: 700,
          }}
        >
          K
        </div>
        <span style={{ fontSize: compact ? 26 : 42, fontWeight: 700, color: "#f7f9ff", letterSpacing: compact ? -0.5 : -1 }}>
          KRAFT LOTTO
        </span>
      </div>

      {children}
    </div>
  );
}
