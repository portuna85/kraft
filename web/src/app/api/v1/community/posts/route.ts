import { NextRequest } from "next/server";
import { proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function POST(req: NextRequest) {
  const body = await req.text();
  return proxyBackend("/api/v1/community/posts", {
    method: "POST",
    headers: communityProxyHeaders(req, {
      "Content-Type": "application/json",
      "X-Device-Token": req.headers.get("X-Device-Token") ?? "",
    }),
    body,
  });
}
