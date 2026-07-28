import { NextRequest } from "next/server";
import { proxyBackend } from "@/lib/backend-proxy";

export async function POST(req: NextRequest) {
  const body = await req.text();
  const deviceToken = req.headers.get("X-Device-Token");
  return proxyBackend("/api/v1/numbers/recommend", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(deviceToken ? { "X-Device-Token": deviceToken } : {}),
    },
    body,
  });
}
