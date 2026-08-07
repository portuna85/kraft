import { test, expect } from "@playwright/test";

// /status는 서버 컴포넌트가 백엔드를 직접 조회하므로(브라우저 fetch가 아님) 라우트
// 모킹이 닿지 않는다. 이 E2E 환경엔 백엔드가 없으므로 폴백 UI가 뜨는지만 확인한다.
test("서비스 상태 페이지는 백엔드 응답이 없으면 폴백 문구를 보여준다", async ({ page }) => {
  await page.goto("/status");

  await expect(page.getByRole("heading", { name: "서비스 상태" })).toBeVisible();
  await expect(page.getByText("상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.")).toBeVisible();
  await expect(page.getByText("이력을 불러오지 못했습니다.")).toBeVisible();
});
