import { NextRequest } from "next/server";
import { deviceProxyHeaders, proxyBackend } from "@/lib/backend-proxy";

export async function GET(req: NextRequest) {
  return proxyBackend("/api/v1/saved", {
    headers: deviceProxyHeaders(req),
  });
}

export async function POST(req: NextRequest) {
  const body = await req.text();
  return proxyBackend("/api/v1/saved", {
    method: "POST",
    headers: deviceProxyHeaders(req, true),
    body,
  });
}
