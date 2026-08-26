import { headers } from "next/headers";

import { NONCE_HEADER } from "@/shared/config/csp";
import { JsonLd } from "@/shared/ui/json-ld";

import { buildFaqJsonLd } from "./faq";

/**
 * FE-PERF-05(docs/improvement.md): nonce는 9개 슬러그 중 `faq`에만 필요하다.
 * `headers()`를 여기 안으로 격리해 다른 8개 슬러그는 동적 API를 부르지 않게 한다
 * (PERF-SSR-01의 정적 프리렌더 전제 조건).
 */
export async function FaqJsonLd() {
  const nonce = (await headers()).get(NONCE_HEADER) ?? undefined;
  return <JsonLd data={buildFaqJsonLd()} nonce={nonce} />;
}
