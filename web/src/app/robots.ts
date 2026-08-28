import type { MetadataRoute } from "next";

import { publicEnv } from "@/shared/config/env";

/**
 * robots.txt
 *
 * `/saved`·`/ops`·`/status`에 **동일한 정책**을 적용한다. 여기서는 셋 다 disallow하지
 * 않는다 — 색인 여부는 각 페이지의 `robots` 메타데이터(noindex)가 결정한다. disallow는
 * 크롤 자체를 막아 noindex 메타를 크롤러가 아예 못 읽게 만들 수 있어, 진짜로 콘텐츠가
 * 아닌 것(Route Handler)에만 쓴다.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", disallow: ["/api/"] },
    sitemap: `${publicEnv.baseUrl}/sitemap.xml`,
  };
}
