import type { MetadataRoute } from "next";
import { getLatestWinningNumber, getPublicBaseUrl } from "@/lib/api";
import { INFO_PAGE_METADATA, INFO_PAGE_SLUGS } from "@/lib/info-page-metadata";

// 레이아웃 밖 라우트 핸들러라 페이지의 revalidate와 달리 실제로 Full Route Cache에 적용된다.
// Next.js 세그먼트 설정 export는 리터럴이어야 정적 분석이 되므로 lib/revalidate.ts의
// REVALIDATE_SITEMAP(=3600)과 값을 수동으로 맞춘다(import 시 빌드 실패).
export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const baseUrl = getPublicBaseUrl();
  let lastMod: string | undefined;
  try {
    const latest = await getLatestWinningNumber();
    lastMod = `${latest.drawDate}T00:00:00+09:00`;
  } catch {
    // backend unavailable (e.g. during build); omit lastModified
  }

  // 실존 URL만 리다이렉트 없는 최종 형태로 등재
  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${baseUrl}/`,          lastModified: lastMod, changeFrequency: "weekly",  priority: 1.0 },
    { url: `${baseUrl}/frequency`, lastModified: lastMod, changeFrequency: "weekly",  priority: 0.8 },
    { url: `${baseUrl}/recommend`, lastModified: lastMod, changeFrequency: "weekly",  priority: 0.8 },
    { url: `${baseUrl}/stats`,     lastModified: lastMod, changeFrequency: "weekly",  priority: 0.7 },
    { url: `${baseUrl}/analysis`,  lastModified: lastMod, changeFrequency: "weekly",  priority: 0.7 },
    { url: `${baseUrl}/companion`, lastModified: lastMod, changeFrequency: "weekly",  priority: 0.7 },
    { url: `${baseUrl}/community`, lastModified: lastMod, changeFrequency: "daily",   priority: 0.6 },

    ...INFO_PAGE_SLUGS.map((slug) => {
      const info = INFO_PAGE_METADATA[slug];
      return {
        url: `${baseUrl}/info/${slug}`,
        lastModified: info.lastModified,
        changeFrequency: info.changeFrequency,
        priority: info.priority,
      };
    }),
  ];

  return staticRoutes;
}
