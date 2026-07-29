import { proxyBackend } from "@/lib/backend-proxy";

export async function POST(req: Request) {
  return proxyBackend("/api/v1/stats/analysis", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: await req.text(),
  });
}
