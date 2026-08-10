import type { MetadataRoute } from "next";

import { getLatestRound } from "@/entities/round/api";
import { INFO_PAGE_META } from "@/app/(public)/info/[slug]/metadata";
import { publicEnv } from "@/shared/config/env";
import { INFO_PAGE_SLUGS, ROUTES } from "@/shared/config/routes";

/**
 * 사이트맵 — improvement_fe.md §24.2(6), §29.8
 *
 * 라우트 핸들러라 페이지의 revalidate와 달리 Full Route Cache에 실제로 적용된다.
 * 리터럴이어야 정적 분석이 되므로 entities/round/api.ts의 재검증 주기와 값을 수동으로
 * 맞춘다(1시간 — import로 끌어오면 빌드 실패).
 */
export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const baseUrl = publicEnv.baseUrl;

  // 최신 회차 발표일을 lastModified로 재사용하지 않는다 — 커뮤니티 콘텐츠 변경과
  // 무관한 값이라 검색엔진에 잘못된 신선도 신호를 준다(레거시 L-10). 회차 관련
  // 라우트에만 실제 갱신 시점으로 쓴다.
  const latest = await getLatestRound().catch(() => null);
  const lastMod = latest === null ? undefined : `${latest.drawDate}T00:00:00+09:00`;

  const staticRoutes: MetadataRoute.Sitemap = [
    {
      url: `${baseUrl}${ROUTES.home}`,
      lastModified: lastMod,
      changeFrequency: "weekly",
      priority: 1.0,
    },
    {
      url: `${baseUrl}${ROUTES.frequency}`,
      lastModified: lastMod,
      changeFrequency: "weekly",
      priority: 0.8,
    },
    {
      url: `${baseUrl}${ROUTES.recommend}`,
      lastModified: lastMod,
      changeFrequency: "weekly",
      priority: 0.8,
    },
    {
      url: `${baseUrl}${ROUTES.stats}`,
      lastModified: lastMod,
      changeFrequency: "weekly",
      priority: 0.7,
    },
    {
      url: `${baseUrl}${ROUTES.analysis}`,
      lastModified: lastMod,
      changeFrequency: "weekly",
      priority: 0.7,
    },
    {
      url: `${baseUrl}${ROUTES.companion}`,
      lastModified: lastMod,
      changeFrequency: "weekly",
      priority: 0.7,
    },
    { url: `${baseUrl}${ROUTES.community}`, changeFrequency: "daily", priority: 0.6 },

    ...INFO_PAGE_SLUGS.map((slug) => {
      const meta = INFO_PAGE_META[slug];
      return {
        url: `${baseUrl}${ROUTES.info(slug)}`,
        lastModified: meta.lastModified,
        changeFrequency: meta.changeFrequency,
        priority: meta.priority,
      };
    }),
  ];

  return staticRoutes;
}
