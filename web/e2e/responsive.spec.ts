import { test, expect, type Page } from "@playwright/test";
import { expectNoOverflow } from "./lib/expect-no-overflow";

// .site-header의 backdrop-filter가 자식 fixed 요소(.nav-backdrop/.nav-mobile-wrap)의
// containing block이 되어 bottom:0/inset:0이 뷰포트가 아니라 헤더 자신의 높이 기준으로
// 계산되던 버그가 있었다 — top==bottom이 되어 드로어가 찌그러졌다(npm run dev 육안 확인으로
// 발견). #nav-mobile의 "보임" 여부만으로는 이 레이아웃 붕괴를 잡지 못하므로, 배경·패널의
// 실제 bounding box가 뷰포트 전체를 덮는지 직접 검사한다.
async function expectDrawerCoversViewport(page: Page) {
  const viewport = page.viewportSize();
  if (!viewport) throw new Error("viewport size unavailable");

  const backdropBox = await page.locator(".nav-backdrop").boundingBox();
  const wrapBox = await page.locator(".nav-mobile-wrap").boundingBox();
  expect(backdropBox).not.toBeNull();
  expect(wrapBox).not.toBeNull();

  // 서브픽셀 반올림 오차 허용치
  const TOLERANCE = 2;
  expect(Math.abs(backdropBox!.y)).toBeLessThan(TOLERANCE);
  expect(Math.abs(backdropBox!.y + backdropBox!.height - viewport.height)).toBeLessThan(TOLERANCE);

  // 드로어 패널은 헤더 아래부터 시작해 뷰포트 하단까지 닿아야 한다
  expect(wrapBox!.y).toBeGreaterThan(0);
  expect(Math.abs(wrapBox!.y + wrapBox!.height - viewport.height)).toBeLessThan(TOLERANCE);
}

// ─────────────────────────────────────────────────────────────────────────────
// 가로 스크롤 없음 — 대표 너비 + 브레이크포인트 경계(639/1023 = 640/1024 바로 아래)
// 에서 확인 (이 설정엔 백엔드가 없어 "/"는 error.tsx 렌더 상태 기준 — 실콘텐츠 상태의
// 오버플로는 playwright.content.config.ts 참고)
// ─────────────────────────────────────────────────────────────────────────────
const VIEWPORTS = [
  { width: 320, height: 568, label: "320px" },
  { width: 639, height: 900, label: "639px (태블릿 경계 −1px)" },
  { width: 768, height: 1024, label: "768px" },
  { width: 1023, height: 900, label: "1023px (데스크톱 경계 −1px)" },
  { width: 1280, height: 800, label: "1280px" },
] as const;

for (const vp of VIEWPORTS) {
  test(`${vp.label} 뷰포트에서 가로 스크롤 없음`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
    await page.goto("/");
    await expectNoOverflow(page);
  });
}

// 가로 모드(landscape) 1케이스 — 드로어 자체의 잘림과 별개로, 셸 레벨에서도
// 짧은 높이(844×390)에서 오버플로가 없는지 확인한다.
test("844×390(가로 모드)에서 가로 스크롤 없음", async ({ page }) => {
  await page.setViewportSize({ width: 844, height: 390 });
  await page.goto("/");
  await expectNoOverflow(page);
});

// ─────────────────────────────────────────────────────────────────────────────
// 클라이언트 컴포넌트라 page.route 목으로 실제 콘텐츠를 렌더할 수 있는 라우트.
// 서버 컴포넌트 데이터 페이지(/, /frequency, /stats, /companion)의 실콘텐츠 오버플로
// 검사는 e2e/content/overflow.spec.ts(픽스처 백엔드)가 담당한다.
// ─────────────────────────────────────────────────────────────────────────────
test.describe("실제 콘텐츠가 채워진 라우트의 오버플로", () => {
  for (const width of [320, 768]) {
    test(`/saved — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.route("**/api/v1/saved", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            { id: 1, numbers: [1, 2, 3, 4, 5, 6], label: "테스트 번호", source: "MANUAL", createdAt: "2026-01-01T00:00:00Z" },
          ]),
        }),
      );
      await page.route("**/api/v1/saved/matches**", (route) =>
        route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
      );
      await page.goto("/saved");
      await expect(page.locator(".saved-item")).toHaveCount(1);
      await expectNoOverflow(page);
    });

    test(`/saved — 빈 상태 — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.route("**/api/v1/saved", (route) =>
        route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
      );
      await page.route("**/api/v1/saved/matches**", (route) =>
        route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
      );
      await page.goto("/saved");
      await expect(page.locator(".saved-empty-state")).toBeVisible();
      await expectNoOverflow(page);
    });

    test(`/saved — 당첨 배지 — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.route("**/api/v1/saved", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            { id: 1, numbers: [1, 2, 3, 4, 5, 6], label: "테스트 번호", source: "MANUAL", createdAt: "2026-01-01T00:00:00Z" },
          ]),
        }),
      );
      await page.route("**/api/v1/saved/matches**", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              savedNumber: { id: 1, numbers: [1, 2, 3, 4, 5, 6], label: "테스트 번호", source: "MANUAL", createdAt: "2026-01-01T00:00:00Z" },
              round: 1200,
              drawDate: "2026-07-18",
              drawNumbers: [1, 2, 3, 4, 5, 6],
              bonusNumber: 7,
              matchedCount: 6,
              bonusMatch: false,
              prizeTier: "1등",
            },
          ]),
        }),
      );
      await page.goto("/saved");
      await expect(page.locator(".saved-prize-badge.prize-win")).toBeVisible();
      await expectNoOverflow(page);
    });

    test(`/recommend — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.route("**/api/v1/numbers/recommend", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            recommendations: [
              [1, 2, 3, 4, 5, 6],
              [7, 8, 9, 10, 11, 12],
            ],
          }),
        }),
      );
      await page.goto("/recommend");
      await page.getByRole("button", { name: "추천받기" }).click();
      await expect(page.locator(".recommend-card").first()).toBeVisible();
      await expectNoOverflow(page);
    });

    test(`/recommend — 10개 생성 — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      const combos = Array.from({ length: 10 }, (_, i) => [i + 1, i + 2, i + 3, i + 4, i + 5, i + 6]);
      await page.route("**/api/v1/numbers/recommend", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ recommendations: combos }),
        }),
      );
      await page.goto("/recommend");
      await page.getByLabel("조합 수").fill("10");
      await page.getByRole("button", { name: "추천받기" }).click();
      await expect(page.locator(".recommend-card")).toHaveCount(10);
      await expectNoOverflow(page);
    });

    test(`/recommend — 검증 오류 메시지 — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.route("**/api/v1/numbers/recommend", (route) =>
        route.fulfill({
          status: 400,
          contentType: "application/json",
          body: JSON.stringify({ message: "제외 번호가 너무 많아 조합을 생성할 수 없습니다." }),
        }),
      );
      await page.goto("/recommend");
      await page.getByRole("button", { name: "추천받기" }).click();
      await expect(page.locator(".status-text")).toBeVisible();
      await expectNoOverflow(page);
    });

    // /analysis는 순수 클라이언트 계산(analyzeNumbers, 백엔드 미의존)이라
    // page.route 없이도 실제 결과 콘텐츠를 렌더할 수 있다.
    test(`/analysis — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.goto("/analysis");
      await page.getByPlaceholder("예: 3, 11, 19, 28, 34, 42")
        .pressSequentially("1, 2, 3, 4, 5, 6");
      await page.getByRole("button", { name: "분석하기" }).click();
      await expect(page.getByRole("heading", { name: "분석 결과" })).toBeVisible();
      await expectNoOverflow(page);
    });
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// 백엔드 미의존 정적 라우트(에러 경계 포함)의 오버플로. not-found·error는 액션
// 버튼이 뷰포트 안에 온전히 들어오는지까지 확인한다 — "고정 오버레이가 CTA를 안
// 가리는지" 검증의 전제 조건이다. 광고 오버레이 자체는 이 트랙에 광고 env가 없어
// e2e/ad-overlay/*(전용 빌드)에서만 검증 가능하다.
// ─────────────────────────────────────────────────────────────────────────────
test.describe("정적 라우트·에러 경계 오버플로", () => {
  for (const width of [320, 768]) {
    test(`/info/faq — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.goto("/info/faq");
      await expect(page.getByRole("heading", { name: "자주 묻는 질문" })).toBeVisible();
      await expectNoOverflow(page);
    });

    test(`404 — ${width}px, 액션 버튼이 뷰포트 안에 있다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.goto("/this-page-does-not-exist");
      const cta = page.getByRole("link", { name: "홈으로 이동" });
      await expect(cta).toBeVisible();
      await expect(cta).toBeInViewport();
      await expectNoOverflow(page);
    });

    test(`에러 경계(error.tsx) — ${width}px, 액션 버튼이 뷰포트 안에 있다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      // 이 설정엔 백엔드가 없어 "/"는 항상 error.tsx를 렌더한다(파일 상단 주석 참고).
      await page.goto("/");
      const cta = page.getByRole("button", { name: "다시 불러오기" });
      await expect(cta).toBeVisible();
      await expect(cta).toBeInViewport();
      await expectNoOverflow(page);
    });
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// 모바일 네비게이션 드로어
// ─────────────────────────────────────────────────────────────────────────────
test.describe("모바일 내비게이션 드로어", () => {
  // Pixel 5 너비 = 393px — CSS 기준 640px 미만 → 햄버거 메뉴
  test.use({ viewport: { width: 393, height: 851 } });

  test("햄버거 버튼이 보이고 데스크톱 내비게이션은 숨겨진다", async ({ page }) => {
    await page.goto("/");

    const toggle = page.getByRole("button", { name: "메뉴 열기" });
    await expect(toggle).toBeVisible();
    await expect(page.locator(".nav-desktop")).toBeHidden();
  });

  test("햄버거 클릭 → 드로어 열림", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 닫기" })).toBeVisible();
  });

  test("드로어 배경·패널이 뷰포트 전체를 덮는다 (backdrop-filter containing block 회귀 방지)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();

    await expectDrawerCoversViewport(page);
  });

  test("탈출 키로 드로어 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(page.locator("#nav-mobile")).not.toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });

  test("뒷배경 클릭으로 드로어 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();

    await page.locator(".nav-mobile-wrap").evaluate((element) => {
      (element as HTMLElement).click();
    });
    await expect(page.locator("#nav-mobile")).not.toBeVisible();
  });

  test("데스크톱 너비로 리사이즈 시 드로어 자동 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();

    // matchMedia change 이벤트를 트리거하기 위해 1024px 이상으로 리사이즈
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page.locator("#nav-mobile")).not.toBeVisible();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 태블릿 네비게이션 드로어
// ─────────────────────────────────────────────────────────────────────────────
test.describe("태블릿 내비게이션 드로어", () => {
  // 640px ≤ width < 1024px → 햄버거 메뉴 (전폭 드로어)
  test.use({ viewport: { width: 768, height: 1024 } });

  test("햄버거 버튼이 보이고 데스크톱 내비게이션은 숨겨진다", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
    await expect(page.locator(".nav-desktop")).toBeHidden();
  });

  test("햄버거 클릭 → 전폭 드로어 열림", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();
  });

  test("드로어 배경·패널이 뷰포트 전체를 덮는다 (backdrop-filter containing block 회귀 방지)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();

    await expectDrawerCoversViewport(page);
  });

  test("탈출 키로 드로어 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.locator("#nav-mobile")).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(page.locator("#nav-mobile")).not.toBeVisible();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 데스크톱: 데스크톱 nav 보임, 햄버거 숨겨짐
// ─────────────────────────────────────────────────────────────────────────────
test.describe("데스크톱 내비게이션", () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test("데스크톱 내비게이션이 보이고 햄버거 버튼은 숨겨진다", async ({ page }) => {
    await page.goto("/");

    await expect(page.locator(".nav-desktop")).toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeHidden();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// R-08: 터치 기기(pointer: coarse)에서 상시 노출 컨트롤의 히트 영역이 --target-min
// (44px) 이상인지 확인. Mobile Chrome/Tablet 프로젝트에서만 의미가 있다 — 데스크톱
// (pointer: fine)에서는 44px을 강제하지 않는 게 의도이므로 그 프로젝트에서는 skip.
// ─────────────────────────────────────────────────────────────────────────────
test.describe("터치 타깃 최소 크기", () => {
  test("nav-toggle·theme-toggle이 44px 이상이다", async ({ page }) => {
    await page.goto("/");

    const isCoarse = await page.evaluate(() => window.matchMedia("(pointer: coarse)").matches);
    test.skip(!isCoarse, "pointer: fine 프로젝트에는 해당 없음");

    for (const selector of [".nav-toggle", ".theme-toggle"]) {
      const box = await page.locator(selector).boundingBox();
      expect(box).not.toBeNull();
      expect(box!.height).toBeGreaterThanOrEqual(44);
    }
  });
});
