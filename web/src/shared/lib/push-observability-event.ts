import { serverEnv } from "@/shared/config/env";

const TIMEOUT_MS = 2_000;

/**
 * OBS-WEB-01(docs/improvement.md): web -> backend 관측 이벤트(vitals/CSP/client-error)
 * push. 실패해도(백엔드 다운·타임아웃·시크릿 미설정 등) 호출부의 응답에 영향을 주면
 * 안 된다 — 항상 흡수한다. `RevalidateWebhookListener`(backend → web 방향)의
 * "부가 기능은 주 경로를 막지 않는다" 원칙과 같다.
 *
 * 시크릿 미설정은 로컬 개발처럼 관측 스택 자체가 없는 환경을 위한 것이다 — 그때는
 * 조용히 아무것도 하지 않는다(fail-closed와 다르다: 이건 이 기능 자체가 꺼진 상태다).
 */
export function pushObservabilityEvent(path: string, body: unknown): void {
  const secret = serverEnv.webObservabilitySecret;
  if (secret === undefined || secret === "") return;

  void fetch(`${serverEnv.backendInternalUrl}/api/v1/observability/${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", "X-Web-Observability-Secret": secret },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  }).catch(() => undefined);
}
