import { expect, test } from "@playwright/test";

/**
 * FE-SEC-01(docs/improvement.md) — `/ops` 응답 CSP에 광고 네트워크 origin이 없어야 한다.
 *
 * `e2e/proxy/ops-gate.spec.ts`는 `KRAFT_OPS_ALLOWED_HOST`를 의도적으로 비운 채 돌아
 * `/ops`가 항상 404이므로 이 검증을 할 수 없다. 이 a11y 트랙은
 * `KRAFT_OPS_ALLOWED_HOST=127.0.0.1`로 이미 `/ops`를 허용해 두었으므로(그래야 axe가
 * 운영 콘솔도 스캔한다) 실제 200 응답의 CSP를 여기서 확인한다.
 */
test.describe("/ops CSP surface", () => {
  test("응답 CSP에 광고 네트워크 origin이 없다", async ({ request }) => {
    const response = await request.get("/ops");
    expect(response.status()).toBe(200);

    const csp = response.headers()["content-security-policy"];
    expect(csp, "/ops 응답에 CSP 헤더가 없다").toBeTruthy();
    expect(csp).not.toContain("kakaocdn");
    expect(csp).not.toContain("googlesyndication");
    expect(csp).not.toContain("doubleclick");
    expect(csp).toMatch(/frame-src 'none'/);
  });

  test("공개 라우트(`/`)의 CSP는 광고 origin을 계속 연다(회귀 없음)", async ({ request }) => {
    const response = await request.get("/");
    const csp = response.headers()["content-security-policy"];

    expect(csp).toBeTruthy();
    // 기본 네트워크는 adfit(NEXT_PUBLIC_AD_NETWORK 기본값) — csp.ts AD_ORIGINS 참고.
    expect(csp).toContain("kakaocdn");
  });
});
