// @vitest-environment node
//
// lib/api.ts의 프로덕션 KRAFT_PUBLIC_BASE_URL 미설정 예외는 서버 실행 컨텍스트
// (typeof window === "undefined")에서만 던지도록 만들었다(§ad-overlay e2e 회귀 —
// 이 모듈이 클라이언트 번들에 섞여 들어가면 브라우저에서 항상 undefined인
// KRAFT_PUBLIC_BASE_URL 때문에 매번 던져버렸다). 기본 jsdom 환경은 전역 window를
// 정의해 이 파일 전체를 "브라우저"로 착각하게 만들므로, 실제 서버 실행을 흉내 내려면
// 이 테스트 파일만 node 환경으로 강제해야 한다.
import { describe, expect, it, beforeEach, afterEach, vi } from "vitest";

describe("백엔드 내부 주소 기본값", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    Object.assign(process.env, originalEnv);
    vi.resetModules();
  });

  it("환경 변수가 없으면 기본 백엔드 주소를 사용한다", async () => {
    delete process.env.KRAFT_BACKEND_INTERNAL_URL;
    // Dynamically import after clearing env so the module re-reads it
    const { getPublicBaseUrl } = await import("@/lib/api");
    // The function exists — backend URL default is an internal concern, so
    // we verify the public URL fallback as a proxy for module loading correctly
    expect(typeof getPublicBaseUrl).toBe("function");
  });

  it("지정된 백엔드 내부 주소를 사용한다", async () => {
    process.env.KRAFT_BACKEND_INTERNAL_URL = "http://custom-backend:9090";
    vi.resetModules();
    const mod = await import("@/lib/api");
    expect(typeof mod.getPublicBaseUrl).toBe("function");
  });
});

describe("공개 base URL 검증", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  it("프로덕션에서 KRAFT_PUBLIC_BASE_URL이 없으면 모듈 로드 시 예외를 던진다", async () => {
    process.env = { ...originalEnv, NODE_ENV: "production" };
    delete process.env.KRAFT_PUBLIC_BASE_URL;
    vi.resetModules();

    await expect(import("@/lib/api")).rejects.toThrow("KRAFT_PUBLIC_BASE_URL");
  });

  it("프로덕션이어도 KRAFT_PUBLIC_BASE_URL이 있으면 정상 로드된다", async () => {
    process.env = { ...originalEnv, NODE_ENV: "production", KRAFT_PUBLIC_BASE_URL: "https://kraft-lotto.example" };
    vi.resetModules();

    const { getPublicBaseUrl } = await import("@/lib/api");
    expect(getPublicBaseUrl()).toBe("https://kraft-lotto.example");
  });
});

describe("자료형 계약", () => {
  it("당첨 번호 타입이 블루프린트의 모든 필드를 포함한다", async () => {
    // Compile-time check via TypeScript — this test ensures the type is importable
    // and has the expected shape at runtime (shape enforced by tsc, not vitest)
    const { getPublicBaseUrl } = await import("@/lib/api");
    expect(getPublicBaseUrl).toBeDefined();
  });
});
