import { NextRequest } from "next/server";
import { proxyBackend } from "@/lib/backend-proxy";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyBackend(`/api/v1/recommendation-sets/${id}`, {
    headers: { "X-Device-Token": req.headers.get("X-Device-Token") ?? "" },
  });
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyBackend(`/api/v1/recommendation-sets/${id}`, {
    method: "DELETE",
    headers: { "X-Device-Token": req.headers.get("X-Device-Token") ?? "" },
  });
}
