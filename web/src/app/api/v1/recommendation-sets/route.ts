import { NextRequest } from "next/server";
import { proxyBackend } from "@/lib/backend-proxy";

export async function GET(req: NextRequest) {
  return proxyBackend("/api/v1/recommendation-sets", {
    headers: { "X-Device-Token": req.headers.get("X-Device-Token") ?? "" },
  });
}
