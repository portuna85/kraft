import { NextRequest } from "next/server";
import { proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const body = await req.text();
  return proxyBackend(`/api/v1/community/posts/${id}`, {
    method: "PUT",
    headers: communityProxyHeaders(req, { "Content-Type": "application/json" }),
    body,
  });
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const query = req.nextUrl.search;
  return proxyBackend(`/api/v1/community/posts/${id}${query}`, {
    method: "DELETE",
    headers: communityProxyHeaders(req),
  });
}
