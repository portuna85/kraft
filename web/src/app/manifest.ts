import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "KRAFT Lotto",
    short_name: "KRAFT",
    description: "로또 당첨 결과 조회, 번호 추천, 보관함 관리",
    start_url: "/",
    display: "standalone",
    background_color: "#050816",
    theme_color: "#00e5ff",
    icons: [
      { src: "/icon/32", sizes: "32x32", type: "image/png" },
      { src: "/icon/192", sizes: "192x192", type: "image/png" },
      { src: "/icon/512", sizes: "512x512", type: "image/png", purpose: "maskable" },
      { src: "/apple-icon", sizes: "180x180", type: "image/png" },
    ],
  };
}
