import { test, expect } from "@playwright/test";

const SUMMARY = {
  service: "kraft-lotto",
  timezone: "Asia/Seoul",
  status: "정상",
  latestRound: 1230,
  latestDrawDate: "2026-01-03",
  checkedAt: "2026-01-03T12:00:00Z",
  fresh: true,
};

const COLLECTED = {
  round: 1230,
  drawDate: "2026-01-03",
  numbers: [1, 2, 3, 4, 5, 6],
  bonusNumber: 7,
  firstPrizeAmount: 2_100_000_000,
};

test("토큰이 없으면 모든 운영 액션 버튼이 비활성 상태다", async ({ page }) => {
  await page.goto("/ops");

  await expect(page.getByRole("heading", { name: "회차 운영 대시보드" })).toBeVisible();
  await expect(page.getByRole("button", { name: "운영 상태 확인" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "최신 회차 반영" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "지정 회차 반영" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "수동 등록" })).toBeDisabled();
});

test("토큰을 입력해 운영 상태를 조회하면 결과 패널을 보여준다", async ({ page }) => {
  await page.route("**/ops-api/summary", (route) => {
    expect(route.request().headers()["x-ops-token"]).toBe("secret-token");
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(SUMMARY) });
  });

  await page.goto("/ops");
  await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("secret-token");
  await page.getByRole("button", { name: "운영 상태 확인" }).click();

  await expect(page.getByText("운영 상태를 불러왔습니다.")).toBeVisible();
  await expect(page.getByText("1230회")).toBeVisible();
});

test("토큰이 틀리면 백엔드 오류 메시지를 그대로 보여준다", async ({ page }) => {
  await page.route("**/ops-api/summary", (route) => {
    route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ message: "운영 토큰이 올바르지 않습니다." }),
    });
  });

  await page.goto("/ops");
  await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("wrong-token");
  await page.getByRole("button", { name: "운영 상태 확인" }).click();

  await expect(page.getByText("운영 토큰이 올바르지 않습니다.")).toBeVisible();
});

test("지정 회차 반영에 성공하면 반영 결과 패널을 보여준다", async ({ page }) => {
  await page.route("**/ops-api/collect/500", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ ...COLLECTED, round: 500 }),
    });
  });
  await page.route("**/ops-api/summary", (route) => {
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(SUMMARY) });
  });

  await page.goto("/ops");
  await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("secret-token");
  await page.getByLabel("특정 회차").fill("500");
  await page.getByRole("button", { name: "지정 회차 반영" }).click();

  await expect(page.getByText("500회차 데이터를 반영했습니다.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "500회차" })).toBeVisible();
});

test("지정 회차 반영에 1 미만 값을 넣으면 요청 없이 검증 메시지만 보여준다", async ({ page }) => {
  let collectCalled = false;
  await page.route("**/ops-api/collect/**", (route) => {
    collectCalled = true;
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(COLLECTED) });
  });

  await page.goto("/ops");
  await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("secret-token");
  await page.getByLabel("특정 회차").fill("0");
  await page.getByRole("button", { name: "지정 회차 반영" }).click();

  await expect(page.getByText("수집할 회차는 1 이상의 정수여야 합니다.")).toBeVisible();
  expect(collectCalled).toBe(false);
});

test("운영 토큰 입력란은 비밀번호 타입이며 자동완성이 꺼져 있다", async ({ page }) => {
  await page.goto("/ops");

  const tokenInput = page.getByPlaceholder("X-Ops-Token 값을 입력하세요");
  await expect(tokenInput).toHaveAttribute("type", "password");
  await expect(tokenInput).toHaveAttribute("autocomplete", "off");
});
