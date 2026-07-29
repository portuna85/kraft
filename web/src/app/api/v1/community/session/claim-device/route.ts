import { NextRequest } from "next/server";
import { deviceProxyHeaders, proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function POST(req: NextRequest) {
  return proxyBackend("/api/v1/community/session/claim-device", {
    method: "POST",
    headers: communityProxyHeaders(req, {
      "Content-Type": "application/json",
      ...deviceProxyHeaders(req),
    }),
  });
}
