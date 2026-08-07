import type { MetadataRoute } from "next";
import { getPublicBaseUrl } from "@/lib/api";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      disallow: ["/saved", "/status", "/admin", "/ops", "/actuator", "/api/"]
    },
    sitemap: `${getPublicBaseUrl()}/sitemap.xml`
  };
}
