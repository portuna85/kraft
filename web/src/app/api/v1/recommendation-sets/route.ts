import { NextRequest } from "next/server";
import { deviceProxyHeaders, proxyBackend } from "@/lib/backend-proxy";

export async function GET(req: NextRequest) {
  return proxyBackend("/api/v1/recommendation-sets", {
    headers: deviceProxyHeaders(req),
  });
}
