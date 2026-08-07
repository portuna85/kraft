import { test, expect } from "@playwright/test";

// KF-06: 삭제된 web/src/lib/backend-proxy.ts를 Caddy(caddy/Caddyfile.local)가 대신하는지
// 검증한다. 픽스처 백엔드(e2e/fixtures/backend.mjs)의 /api/v1/_proxy-probe/* 가 받은 요청을
// 그대로 반사하므로, 응답 바디를 보면 Caddy가 실제로 무엇을 백엔드까지 실어 보냈는지 알 수
// 있다 — Caddy 설정에서 헤더 전달을 인위로 빼면(예: reverse_proxy에서 header_up 제거)
// 이 스펙들이 반드시 실패해야 진짜 회귀 방지 그물이다.

test("커뮤니티 쓰기 흐름 — 쿠키와 X-XSRF-TOKEN이 백엔드까지 전달된다", async ({
  page,
  context,
}) => {
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "e2e-test-session",
      domain: "127.0.0.1",
      path: "/",
    },
  ]);
  await page.goto("/");

  const body = await page.evaluate(async () => {
    const res = await fetch("/api/v1/_proxy-probe/echo", {
      method: "POST",
      credentials: "include",
      headers: { "X-XSRF-TOKEN": "e2e-test-csrf" },
    });
    return res.json();
  });

  expect(body.cookie).toContain("JSESSIONID=e2e-test-session");
  expect(body.xsrfToken).toBe("e2e-test-csrf");
});

test("기기 스코프 흐름 — X-Device-Token이 백엔드까지 전달된다", async ({ page }) => {
  await page.goto("/");

  const body = await page.evaluate(async () => {
    const res = await fetch("/api/v1/_proxy-probe/echo", {
      method: "POST",
      headers: { "X-Device-Token": "e2e-test-device" },
    });
    return res.json();
  });

  expect(body.deviceToken).toBe("e2e-test-device");
});

test("백엔드의 Set-Cookie가 브라우저에 그대로 저장된다", async ({ page, context }) => {
  await page.goto("/");

  await page.evaluate(async () => {
    await fetch("/api/v1/_proxy-probe/echo", { method: "POST", credentials: "include" });
  });

  const cookies = await context.cookies();
  const proxyProbeCookie = cookies.find((c) => c.name === "proxy-probe-session");
  expect(proxyProbeCookie?.value).toBe("echoed");
});

test("응답 헤더(Retry-After·X-RateLimit-*·ETag)가 있는 그대로 통과한다", async ({ request }) => {
  const res = await request.get("/api/v1/_proxy-probe/headers");
  expect(res.status()).toBe(200);
  expect(res.headers()["retry-after"]).toBe("30");
  expect(res.headers()["x-ratelimit-limit"]).toBe("100");
  expect(res.headers()["x-ratelimit-remaining"]).toBe("5");
  expect(res.headers()["etag"]).toBe('"proxy-probe-etag"');
});

test("백엔드 무응답 시 Caddy의 5초 응답 타임아웃이 발동한다", async ({ request }) => {
  const startedAt = Date.now();
  const res = await request.get("/api/v1/_proxy-probe/slow", { timeout: 15_000 });
  const elapsedMs = Date.now() - startedAt;

  expect(res.status()).toBeGreaterThanOrEqual(500);
  // 픽스처는 6초 뒤 응답한다 — Caddy가 5초에서 끊지 않으면 이 값이 6초 근처로 나온다.
  expect(elapsedMs).toBeLessThan(6_000);
});

test("/api/revalidate는 외부(브라우저 컨텍스트)에서 403으로 차단된다", async ({ request }) => {
  const res = await request.post("/api/revalidate", { data: { path: "/" } });
  expect(res.status()).toBe(403);
});
