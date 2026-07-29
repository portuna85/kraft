import { defineConfig, devices } from "@playwright/test";

// KF-06: web/src/app/api/v1/**의 Next 라우트 핸들러(backend-proxy.ts)를 삭제한 뒤,
// 그게 하던 일 — 쿠키·CSRF·기기토큰 전달, 응답 헤더 패스스루, 타임아웃, /api/revalidate
// 차단 — 을 이제 실제로 대신하는 caddy/Caddyfile.local 앞단이 맞게 동작하는지 검증한다.
// 다른 e2e 트랙과 달리 standalone Next 서버를 직접 때리지 않고, 실 Caddy 컨테이너를
// 그 앞에 세운 뒤 baseURL을 Caddy 쪽으로 잡는다 — 그래야 Next가 아니라 Caddy가
// 프록시 계약을 지키는지 검증하는 게 된다. 브라우저/네트워크 계약 검증이 목적이라
// 크로스브라우저·모바일 뷰포트 매트릭스는 불필요해 chromium 1개만 돈다.
const FIXTURE_BACKEND_PORT = 4103;
const NEXT_PORT = 3104;
const CADDY_PORT = process.env.PROXY_CADDY_PORT ?? "8180";
const CADDY_IMAGE =
  "caddy:2-alpine@sha256:77c07d5ebfa5be9fd6c820d2094ae662c9e7eeb9bf98346b7f639900263ee2a2";

export default defineConfig({
  testDir: "./e2e/proxy",
  fullyParallel: true,
  workers: process.env.CI ? 2 : undefined,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: `http://127.0.0.1:${CADDY_PORT}`,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: "node e2e/fixtures/backend.mjs",
      url: `http://127.0.0.1:${FIXTURE_BACKEND_PORT}`,
      reuseExistingServer: !process.env.CI,
      timeout: 10_000,
      env: { E2E_FIXTURE_BACKEND_PORT: String(FIXTURE_BACKEND_PORT) },
    },
    {
      // KF-06 이후 SSR fetch는 여전히 백엔드를 직접 호출한다(backendBaseUrl) — 이 트랙에서는
      // 그 직접 호출도, 브라우저가 Caddy를 거쳐 보내는 호출도 같은 픽스처 백엔드를 본다.
      command: "npm run e2e:serve",
      url: `http://127.0.0.1:${NEXT_PORT}`,
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      env: {
        NEXT_DIST_DIR: ".next-proxy",
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${FIXTURE_BACKEND_PORT}`,
        KRAFT_PUBLIC_BASE_URL: `http://127.0.0.1:${CADDY_PORT}`,
        PORT: String(NEXT_PORT),
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
    {
      // host.docker.internal은 Docker Desktop(Win/Mac)에서 기본 제공되고, Linux CI
      // 러너에서는 --add-host=host.docker.internal:host-gateway로 명시해야 해석된다.
      command:
        `docker run --rm -p ${CADDY_PORT}:80 ` +
        "--add-host=host.docker.internal:host-gateway " +
        `-e KRAFT_LOCAL_BACKEND_UPSTREAM=host.docker.internal:${FIXTURE_BACKEND_PORT} ` +
        `-e KRAFT_LOCAL_WEB_UPSTREAM=host.docker.internal:${NEXT_PORT} ` +
        "-v \"" +
        process.cwd().replace(/\\/g, "/") +
        "/../caddy:/etc/caddy\" " +
        `${CADDY_IMAGE} caddy run --config /etc/caddy/Caddyfile.local --adapter caddyfile`,
      url: `http://127.0.0.1:${CADDY_PORT}`,
      reuseExistingServer: !process.env.CI,
      timeout: 20_000,
    },
  ],
});
