import { NextRequest } from "next/server";
import { proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function GET(req: NextRequest) {
  return proxyBackend("/api/v1/community/session", {
    headers: communityProxyHeaders(req),
  });
}
