import { ImageResponse } from "next/og";

export const contentType = "image/png";

const SIZES: Record<string, number> = { "32": 32, "192": 192, "512": 512 };

export function generateImageMetadata() {
  return Object.keys(SIZES).map((id) => ({
    id,
    contentType: "image/png" as const,
    size: { width: SIZES[id], height: SIZES[id] },
  }));
}

export default function Icon({ params }: { params: { id: string } }) {
  const sz = SIZES[params.id] ?? 32;
  const fontSize = Math.round(sz * 0.53);
  return new ImageResponse(
    (
      <div
        style={{
          width: sz,
          height: sz,
          borderRadius: "50%",
          background: "linear-gradient(135deg, #00e5ff, #7c4dff)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize,
          fontWeight: 800,
          color: "#050816",
        }}
      >
        K
      </div>
    ),
    { width: sz, height: sz },
  );
}
