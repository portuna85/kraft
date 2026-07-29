import { NextRequest } from "next/server";
import { deviceProxyHeaders, proxyBackend } from "@/lib/backend-proxy";

export async function GET(req: NextRequest) {
  const round = req.nextUrl.searchParams.get("round") ?? "latest";
  return proxyBackend(`/api/v1/saved/matches?round=${encodeURIComponent(round)}`, {
    headers: deviceProxyHeaders(req),
  });
}
