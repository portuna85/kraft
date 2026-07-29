import { NextRequest } from "next/server";
import { deviceProxyHeaders, proxyBackend } from "@/lib/backend-proxy";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyBackend(`/api/v1/recommendation-sets/${id}`, {
    headers: deviceProxyHeaders(req),
  });
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyBackend(`/api/v1/recommendation-sets/${id}`, {
    method: "DELETE",
    headers: deviceProxyHeaders(req),
  });
}
