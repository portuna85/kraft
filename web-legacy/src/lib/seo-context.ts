import { headers } from "next/headers";
import { getPublicBaseUrl } from "@/lib/api";

// L-1: JSON-LD(nonce)와 baseUrl을 함께 쓰는 페이지 8곳이 이 두 줄을 그대로
// 반복하고 있었다 — 하나로 묶는다. next/headers를 쓰므로 서버 컴포넌트 전용
// 파일로 분리한다(lib/api.ts는 클라이언트 컴포넌트에서도 import되므로 여기 두면
// 클라이언트 번들이 next/headers를 끌고 들어가 빌드가 깨진다).
export async function getPageSeoContext(): Promise<{ nonce: string | undefined; baseUrl: string }> {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return { nonce, baseUrl: getPublicBaseUrl() };
}
