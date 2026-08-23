import { describe, expect, it } from "vitest";

import { buildCsp, buildCspReportOnly, generateNonce, type CspInput } from "./csp";

function input(overrides: Partial<CspInput> = {}): CspInput {
  return {
    nonce: "test-nonce",
    adNetwork: "adfit",
    allowEval: false,
    surface: "public",
    ...overrides,
  };
}

function directive(policy: string, name: string): string {
  const found = policy.split("; ").find((part) => part.startsWith(`${name} `));
  return found ?? "";
}

describe("CSP 정책", () => {
  it("script-src에 nonce를 포함한다 — 없으면 전 페이지 하이드레이션이 막힌다", () => {
    expect(directive(buildCsp(input()), "script-src")).toContain("'nonce-test-nonce'");
  });

  it("프로덕션에서는 unsafe-eval을 허용하지 않는다", () => {
    expect(buildCsp(input({ allowEval: false }))).not.toContain("'unsafe-eval'");
    expect(buildCsp(input({ allowEval: true }))).toContain("'unsafe-eval'");
  });

  it("실제로 쓰는 광고 네트워크의 출처만 연다", () => {
    const adfit = buildCsp(input({ adNetwork: "adfit" }));
    expect(adfit).toContain("https://t1.kakaocdn.net");
    expect(adfit).not.toContain("googlesyndication");

    const adsense = buildCsp(input({ adNetwork: "adsense" }));
    expect(adsense).toContain("https://pagead2.googlesyndication.com");
    expect(adsense).not.toContain("kakaocdn");
  });

  it("클릭재킹·플러그인·base 태그 주입을 막는다", () => {
    const policy = buildCsp(input());
    expect(policy).toContain("frame-ancestors 'none'");
    expect(policy).toContain("object-src 'none'");
    expect(policy).toContain("base-uri 'self'");
    expect(policy).toContain("form-action 'self'");
  });

  it("폰트는 자체 호스팅만 허용한다", () => {
    expect(directive(buildCsp(input()), "font-src")).toBe("font-src 'self'");
  });
});

describe("FE-SEC-01: ops surface는 광고 네트워크 origin을 전혀 열지 않는다", () => {
  it("adfit·adsense 어느 쪽을 골라도 ops CSP에 해당 origin이 없다", () => {
    const opsAdfit = buildCsp(input({ surface: "ops", adNetwork: "adfit" }));
    expect(opsAdfit).not.toContain("kakaocdn");

    const opsAdsense = buildCsp(input({ surface: "ops", adNetwork: "adsense" }));
    expect(opsAdsense).not.toContain("googlesyndication");
    expect(opsAdsense).not.toContain("doubleclick");
  });

  it("script-src·img-src·connect-src에서 광고 origin이 빠지고 self만 남는다", () => {
    const ops = buildCsp(input({ surface: "ops" }));
    expect(directive(ops, "script-src")).toBe(`script-src 'self' 'nonce-test-nonce'`);
    expect(directive(ops, "img-src")).toBe("img-src 'self' data:");
    expect(directive(ops, "connect-src")).toBe("connect-src 'self'");
  });

  it("frame-src는 'none'이다 — 열어둘 광고 iframe이 없다", () => {
    expect(directive(buildCsp(input({ surface: "ops" })), "frame-src")).toBe("frame-src 'none'");
  });

  it("public surface는 기존과 동일하게 광고 origin을 연다(회귀 없음)", () => {
    const pub = buildCsp(input({ surface: "public", adNetwork: "adfit" }));
    expect(pub).toContain("https://t1.kakaocdn.net");
  });
});

describe("CSP Report-Only 후보 정책", () => {
  it("style-src에서 unsafe-inline을 빼 인라인 style 사용을 관측한다", () => {
    const styleSrc = directive(buildCspReportOnly(input()), "style-src");
    expect(styleSrc).not.toContain("'unsafe-inline'");
    expect(styleSrc).toContain("'nonce-test-nonce'");
  });

  it("위반을 수집할 report-uri를 갖는다", () => {
    expect(buildCspReportOnly(input())).toContain("report-uri /api/csp-report");
  });

  it("강제 정책은 아직 style-src에 unsafe-inline을 유지한다", () => {
    // 후보 정책이 위반 0으로 안정되기 전에 강제 정책을 좁히면 화면이 깨진다.
    expect(directive(buildCsp(input()), "style-src")).toContain("'unsafe-inline'");
  });
});

describe("nonce 생성기", () => {
  it("요청마다 다른 값을 만든다", () => {
    expect(generateNonce()).not.toBe(generateNonce());
  });

  it("CSP에 넣을 수 있는 base64 문자열이다", () => {
    expect(generateNonce()).toMatch(/^[A-Za-z0-9+/]+={0,2}$/);
  });
});
