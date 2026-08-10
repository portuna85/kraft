import { ImageResponse } from "next/og";

export const size = { width: 180, height: 180 };
export const contentType = "image/png";

export default function AppleIcon() {
  return new ImageResponse(
    <div
      style={{
        width: 180,
        height: 180,
        borderRadius: 38,
        background: "#050816",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        style={{
          width: 120,
          height: 120,
          borderRadius: "50%",
          background: "linear-gradient(135deg, #00e5ff, #7c4dff)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: 64,
          fontWeight: 800,
          color: "#04101b",
        }}
      >
        K
      </div>
    </div>,
    { ...size },
  );
}
