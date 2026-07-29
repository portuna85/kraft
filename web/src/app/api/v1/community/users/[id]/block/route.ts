import { NextRequest } from "next/server";
import { proxyBackend, communityProxyHeaders } from "@/lib/backend-proxy";

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyBackend(`/api/v1/community/users/${id}/block`, {
    method: "PUT",
    headers: communityProxyHeaders(req),
  });
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyBackend(`/api/v1/community/users/${id}/block`, {
    method: "DELETE",
    headers: communityProxyHeaders(req),
  });
}
