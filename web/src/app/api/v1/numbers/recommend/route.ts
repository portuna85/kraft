import { NextRequest } from "next/server";
import { deviceProxyHeaders, proxyBackend } from "@/lib/backend-proxy";

export async function POST(req: NextRequest) {
  const body = await req.text();
  return proxyBackend("/api/v1/numbers/recommend", {
    method: "POST",
    headers: deviceProxyHeaders(req, true),
    body,
  });
}
