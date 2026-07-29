import { NextRequest } from "next/server";
import { proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function GET(req: NextRequest) {
  const query = req.nextUrl.search;
  return proxyBackend(`/api/v1/community/me/interactions${query}`, {
    headers: communityProxyHeaders(req),
  });
}
