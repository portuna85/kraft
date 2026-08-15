import { expect, test, type APIRequestContext } from "@playwright/test";

const FIXTURE_URL = "http://127.0.0.1:4111";
const REVALIDATE_SECRET = "e2e-revalidate-secret";

async function revalidateLatest(request: APIRequestContext) {
  const response = await request.post("/api/revalidate", {
    headers: { "X-Revalidate-Secret": REVALIDATE_SECRET },
    data: { paths: ["/"], tags: ["rounds:latest"] },
  });
  expect(response.status()).toBe(200);
}

async function setLatestRoundAndRevalidate(
  request: APIRequestContext,
  round: number,
  drawDate: string,
) {
  const response = await request.put(
    `${FIXTURE_URL}/__test__/latest?round=${round}&drawDate=${drawDate}`,
  );
  expect(response.status()).toBe(204);
  await revalidateLatest(request);
}

test.describe("최신 회차 ISR 재검증", () => {
  test.afterEach(async ({ request }) => {
    const response = await request.post(`${FIXTURE_URL}/__test__/reset`);
    expect(response.status()).toBe(204);
    // 다음 spec이 변경된 fetch 캐시를 이어받지 않도록 기준값도 즉시 반영한다.
    await revalidateLatest(request);
  });

  test("웹훅 뒤 첫 홈 요청부터 갱신된 회차를 표시한다", async ({ page, request }) => {
    // 이전 실행의 영속 fetch 캐시가 남아 있어도 기준 회차부터 새로 시작한다.
    await setLatestRoundAndRevalidate(request, 1150, "2026-08-01");
    await page.goto("/");
    await expect(page.getByTestId("latest-round")).toContainText("1150회");

    await setLatestRoundAndRevalidate(request, 1151, "2026-08-08");

    const response = await page.goto("/");
    expect(response?.status()).toBe(200);
    await expect(page.getByTestId("latest-round")).toContainText("1151회");
    await expect(page.getByTestId("latest-round")).toContainText("2026년 8월 8일 추첨");
  });
});
