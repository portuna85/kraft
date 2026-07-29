// 운영에서는 Caddy가 /api/v1/* 를 backend:8080으로 직결하므로 web/src/app/api/v1/**의
// route handler(이 함수를 호출하는 쪽)는 도달하지 않는다. 이 프록시는 `npm run dev`로
// Next.js만 단독 실행할 때(Caddy 없이) 백엔드를 호출하기 위한 로컬 개발용 폴백이다.
import { backendBaseUrl } from "@/lib/backend-url";

const passthroughHeaders = [
  "cache-control",
  "content-type",
  "etag",
  "last-modified",
  "retry-after",
  "x-ratelimit-limit",
  "x-ratelimit-remaining",
  "x-request-id",
] as const;

function copyHeaders(source: Headers): Headers {
  const headers = new Headers();
  for (const headerName of passthroughHeaders) {
    const headerValue = source.get(headerName);
    if (headerValue) {
      headers.set(headerName, headerValue);
    }
  }
  // F-P0-11: 세션 쿠키(JSESSIONID)·CSRF 쿠키(XSRF-TOKEN)를 쓰는 community 엔드포인트를
  // 이 프록시로 태우려면 백엔드가 내려주는 Set-Cookie를 그대로 브라우저에 돌려줘야 한다 —
  // 없으면 로그인·CSRF 발급이 전혀 반영되지 않는다. getSetCookie()는 여러 개를 배열로
  // 반환하므로(단일 값 헤더가 아니다) append로 하나씩 실어야 전부 유지된다.
  for (const cookie of source.getSetCookie()) {
    headers.append("set-cookie", cookie);
  }
  return headers;
}

// F-P0-11: community 엔드포인트는 세션 쿠키 + CSRF 더블서브밋(쿠키 XSRF-TOKEN +
// 헤더 X-XSRF-TOKEN)을 쓴다 — 둘 다 들어온 요청 그대로 백엔드에 실어 보내야 한다.
export function communityProxyHeaders(
  req: Request,
  extra?: Record<string, string>
): HeadersInit {
  const headers: Record<string, string> = { ...extra };
  const cookie = req.headers.get("cookie");
  if (cookie) headers.cookie = cookie;
  const xsrfToken = req.headers.get("x-xsrf-token");
  if (xsrfToken) headers["x-xsrf-token"] = xsrfToken;
  return headers;
}

/** Headers permitted for anonymous device-scoped API routes. */
export function deviceProxyHeaders(req: Request, json = false): Record<string, string> {
  const token = req.headers.get("X-Device-Token");
  return {
    ...(json ? { "Content-Type": "application/json" } : {}),
    ...(token ? { "X-Device-Token": token } : {}),
  };
}

export async function proxyBackend(
  path: string,
  init?: RequestInit
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const res = await fetch(`${backendBaseUrl}${path}`, {
      ...init,
      signal: controller.signal,
      cache: "no-store",
    });
    const body = res.status === 204 ? null : await res.arrayBuffer();
    return new Response(body, {
      status: res.status,
      headers: copyHeaders(res.headers),
    });
  } catch {
    return Response.json(
      { code: "BACKEND_UNAVAILABLE", message: "백엔드 요청을 처리하지 못했습니다." },
      { status: 502 }
    );
  } finally {
    clearTimeout(timer);
  }
}
