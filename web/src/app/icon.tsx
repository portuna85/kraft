import { ImageResponse } from "next/og";

export const contentType = "image/png";

const SIZES: Record<string, number> = { "32": 32, "192": 192, "512": 512 };

export function generateImageMetadata() {
  return Object.keys(SIZES).map((id) => ({
    id,
    contentType: "image/png" as const,
    size: { width: SIZES[id] ?? 32, height: SIZES[id] ?? 32 },
  }));
}

export default function Icon({ params }: { params: { id: string } }) {
  const value = SIZES[params.id] ?? 32;
  const fontSize = Math.round(value * 0.53);

  return new ImageResponse(
    <div
      style={{
        width: value,
        height: value,
        borderRadius: "50%",
        background: "linear-gradient(135deg, #00e5ff, #7c4dff)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize,
        fontWeight: 800,
        color: "#04101b",
      }}
    >
      K
    </div>,
    { width: value, height: value },
  );
}
