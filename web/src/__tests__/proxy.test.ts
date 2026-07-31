import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";

describe("CSP nonce 미들웨어", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  describe("generateNonce", () => {
    it("매번 다른 base64 문자열을 생성한다", async () => {
      const { generateNonce } = await import("@/proxy");
      const a = generateNonce();
      const b = generateNonce();

      expect(a).not.toBe(b);
      expect(a.length).toBeGreaterThan(0);
    });
  });

  describe("buildCsp", () => {
    it("nonce를 script-src에 포함한 CSP 문자열을 생성한다", async () => {
      process.env = { ...process.env, NODE_ENV: "production" };
      vi.resetModules();
      const { buildCsp } = await import("@/proxy");

      const csp = buildCsp("abc123");

      expect(csp).toContain("script-src 'self' 'nonce-abc123'");
      expect(csp).toContain("frame-ancestors 'none'");
    });

    it("프로덕션에서는 'unsafe-eval'을 포함하지 않는다", async () => {
      process.env = { ...process.env, NODE_ENV: "production" };
      vi.resetModules();
      const { buildCsp } = await import("@/proxy");

      expect(buildCsp("nonce")).not.toContain("unsafe-eval");
    });

    it("개발 환경에서는 'unsafe-eval'을 포함한다", async () => {
      process.env = { ...process.env, NODE_ENV: "development" };
      vi.resetModules();
      const { buildCsp } = await import("@/proxy");

      expect(buildCsp("nonce")).toContain("unsafe-eval");
    });

    it("NEXT_PUBLIC_AD_NETWORK 미설정 시 AdFit 출처만 포함한다(기본값)", async () => {
      delete process.env.NEXT_PUBLIC_AD_NETWORK;
      vi.resetModules();
      const { buildCsp } = await import("@/proxy");

      const csp = buildCsp("nonce");

      expect(csp).toContain("t1.kakaocdn.net");
      expect(csp).not.toContain("googlesyndication.com");
      expect(csp).not.toContain("doubleclick.net");
    });

    it("NEXT_PUBLIC_AD_NETWORK=adsense면 AdSense 출처만 포함한다", async () => {
      process.env = { ...process.env, NEXT_PUBLIC_AD_NETWORK: "adsense" };
      vi.resetModules();
      const { buildCsp } = await import("@/proxy");

      const csp = buildCsp("nonce");

      expect(csp).toContain("googlesyndication.com");
      expect(csp).toContain("doubleclick.net");
      expect(csp).not.toContain("kakao");
    });
  });

  describe("buildCspReportOnly", () => {
    it("style-src에서 'unsafe-inline'을 빼고 report-uri를 포함한다", async () => {
      process.env = { ...process.env, NODE_ENV: "production" };
      vi.resetModules();
      const { buildCspReportOnly } = await import("@/proxy");

      const csp = buildCspReportOnly("abc123");

      expect(csp).toContain("style-src 'self' 'nonce-abc123'");
      expect(csp).not.toContain("unsafe-inline");
      expect(csp).toContain("report-uri /api/csp-report");
    });

    it("enforcing 정책(buildCsp)과 달리 style-src만 좁아지고 나머지 지시문은 동일하다", async () => {
      process.env = { ...process.env, NODE_ENV: "production" };
      vi.resetModules();
      const { buildCsp, buildCspReportOnly } = await import("@/proxy");

      const enforcing = buildCsp("abc123");
      const reportOnly = buildCspReportOnly("abc123");

      expect(enforcing).toContain("frame-ancestors 'none'");
      expect(reportOnly).toContain("frame-ancestors 'none'");
      expect(enforcing).toContain("style-src 'self' 'unsafe-inline'");
    });
  });

  describe("proxy", () => {
    it("응답에 CSP 헤더를 설정하고 요청에 x-nonce 헤더를 주입한다", async () => {
      const { proxy } = await import("@/proxy");
      const req = new NextRequest("http://localhost/");

      const response = proxy(req);

      const csp = response.headers.get("Content-Security-Policy");
      expect(csp).toContain("nonce-");
      expect(response.headers.get("x-middleware-request-x-nonce")).toBeTruthy();
    });

    it("Report-Only CSP 헤더도 함께 설정한다", async () => {
      const { proxy } = await import("@/proxy");
      const req = new NextRequest("http://localhost/");

      const response = proxy(req);

      const reportOnlyCsp = response.headers.get("Content-Security-Policy-Report-Only");
      expect(reportOnlyCsp).toContain("report-uri /api/csp-report");
      expect(reportOnlyCsp).not.toContain("unsafe-inline");
    });

    it("KRAFT_OPS_ALLOWED_HOST와 다른 호스트로 /ops에 접근하면 404로 rewrite한다", async () => {
      process.env.KRAFT_OPS_ALLOWED_HOST = "ops.kraft-lotto.example";
      vi.resetModules();
      const { proxy } = await import("@/proxy");
      const req = new NextRequest("http://public.kraft-lotto.example/ops", {
        headers: { host: "public.kraft-lotto.example" },
      });

      const response = proxy(req);

      expect(response.status).toBe(404);
    });

    it("KRAFT_OPS_ALLOWED_HOST와 같은 호스트면 /ops를 통과시킨다", async () => {
      process.env.KRAFT_OPS_ALLOWED_HOST = "ops.kraft-lotto.example";
      vi.resetModules();
      const { proxy } = await import("@/proxy");
      const req = new NextRequest("http://ops.kraft-lotto.example/ops", {
        headers: { host: "ops.kraft-lotto.example" },
      });

      const response = proxy(req);

      expect(response.status).not.toBe(404);
    });

    it("F-P0-12: KRAFT_OPS_ALLOWED_HOST가 없으면 fail-closed로 /ops를 404 처리한다", async () => {
      delete process.env.KRAFT_OPS_ALLOWED_HOST;
      vi.resetModules();
      const { proxy } = await import("@/proxy");
      const req = new NextRequest("http://anyhost.example/ops", {
        headers: { host: "anyhost.example" },
      });

      const response = proxy(req);

      expect(response.status).toBe(404);
    });
  });
});
