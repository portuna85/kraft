import { NextRequest } from "next/server";
import { proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const query = req.nextUrl.search;
  return proxyBackend(`/api/v1/community/posts/${id}/comments${query}`, {
    headers: communityProxyHeaders(req),
  });
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const body = await req.text();
  return proxyBackend(`/api/v1/community/posts/${id}/comments`, {
    method: "POST",
    headers: communityProxyHeaders(req, { "Content-Type": "application/json" }),
    body,
  });
}
