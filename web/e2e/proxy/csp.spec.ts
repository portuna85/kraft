import { expect, test } from "@playwright/test";

/**
 * T-22 — CSP nonce가 전 경로 응답에 존재하고 하이드레이션이 성공한다.
 *
 * nonce가 한 경로라도 빠지면 그 페이지의 RSC 하이드레이션이 통째로 막힌다. 헤더 존재만
 * 확인하는 것으로는 부족해서, 실제로 콘솔에 CSP 차단 오류가 없는지까지 본다.
 */
const PATHS = ["/", "/not-found-probe"];

test.describe("CSP nonce", () => {
  for (const path of PATHS) {
    test(`${path} 응답의 CSP에 nonce가 실린다`, async ({ request }) => {
      const response = await request.get(path);
      const csp = response.headers()["content-security-policy"];

      expect(csp, `${path}에 CSP 헤더가 없다`).toBeTruthy();
      expect(csp).toMatch(/script-src [^;]*'nonce-[A-Za-z0-9+/=]+'/);
    });

    test(`${path} 응답마다 nonce가 새로 발급된다`, async ({ request }) => {
      const read = async () => {
        const csp = (await request.get(path)).headers()["content-security-policy"] ?? "";
        return /'nonce-([A-Za-z0-9+/=]+)'/.exec(csp)?.[1];
      };

      const [first, second] = [await read(), await read()];
      expect(first).toBeTruthy();
      expect(second).not.toBe(first);
    });
  }

  test("홈에서 CSP 차단 없이 하이드레이션이 끝난다", async ({ page }) => {
    // 하이드레이션 증거로 쓰는 탭바는 1024px 이상에서 CSS로 숨겨진다 — 데스크톱
    // 뷰포트로 두면 요소가 없어서 "하이드레이션 실패"와 구분되지 않는다.
    await page.setViewportSize({ width: 390, height: 844 });

    const violations: string[] = [];
    page.on("console", (message) => {
      const text = message.text();
      if (/Content Security Policy|Refused to (execute|load|apply)/i.test(text)) {
        violations.push(text);
      }
    });

    await page.goto("/");
    // 하이드레이션이 실제로 끝났는지 — 클라이언트 컴포넌트인 탭바가 현재 위치를
    // 표시하려면 JS가 붙어야 한다.
    await expect(
      page.getByRole("navigation", { name: "바로가기" }).getByRole("link", { name: "홈" }),
    ).toHaveAttribute("aria-current", "page");

    expect(violations, `CSP 위반 발생:\n${violations.join("\n")}`).toHaveLength(0);
  });

  test("FE-CSP-01: style-src가 unsafe-inline 없이 강제되고 위반을 관측한다", async ({
    request,
  }) => {
    const csp = (await request.get("/")).headers()["content-security-policy"];

    expect(csp).toBeTruthy();
    expect(csp).toContain("report-uri /api/csp-report");
    const styleSrc = csp?.split("; ").find((part) => part.startsWith("style-src "));
    expect(styleSrc).not.toContain("'unsafe-inline'");
  });
});
