import { test, expect, type Page } from "@playwright/test";
import { expectNoOverflow } from "./lib/expect-no-overflow";

// .site-header의 backdrop-filter가 자식 fixed 요소(1단계 Drawer 프리미티브의 backdrop/panel)의
// containing block이 되어 bottom:0/inset:0이 뷰포트가 아니라 헤더 자신의 높이 기준으로
// 계산되던 버그가 있었다 — top==bottom이 되어 드로어가 찌그러졌다(npm run dev 육안 확인으로
// 발견했고 Drawer 프리미티브가 실제 화면에 적용되며 재현을 확인했다). role=dialog의
// "보임" 여부만으로는 이 레이아웃 붕괴를 잡지 못하므로, 배경·패널의 실제 bounding box가
// 뷰포트 전체 높이를 덮는지 직접 검사한다(패널은 side="right"라 폭은 뷰포트 전체가 아니다).
async function expectDrawerCoversViewport(page: Page) {
  const viewport = page.viewportSize();
  if (!viewport) throw new Error("viewport size unavailable");

  const backdropBox = await page.locator("[data-drawer-backdrop]").boundingBox();
  const panelBox = await page.locator("[data-drawer-panel]").boundingBox();
  expect(backdropBox).not.toBeNull();
  expect(panelBox).not.toBeNull();

  // 서브픽셀 반올림 오차 허용치
  const TOLERANCE = 2;
  expect(Math.abs(backdropBox!.y)).toBeLessThan(TOLERANCE);
  expect(Math.abs(backdropBox!.y + backdropBox!.height - viewport.height)).toBeLessThan(TOLERANCE);

  // 패널(오른쪽 사이드)은 뷰포트 맨 위부터 바닥까지 전체 높이를 차지해야 한다
  expect(Math.abs(panelBox!.y)).toBeLessThan(TOLERANCE);
  expect(Math.abs(panelBox!.y + panelBox!.height - viewport.height)).toBeLessThan(TOLERANCE);
}

// ─────────────────────────────────────────────────────────────────────────────
// 가로 스크롤 없음 — 대표 너비 + 브레이크포인트 경계(639/1023 = 640/1024 바로 아래)
// 에서 확인 (이 설정엔 백엔드가 없어 "/"는 error.tsx 렌더 상태 기준 — 실콘텐츠 상태의
// 오버플로는 playwright.content.config.ts 참고)
// ─────────────────────────────────────────────────────────────────────────────
const VIEWPORTS = [
  { width: 320, height: 568, label: "320px" },
  { width: 639, height: 900, label: "639px (태블릿 경계 −1px)" },
  { width: 640, height: 900, label: "640px (태블릿 경계)" },
  { width: 768, height: 1024, label: "768px" },
  { width: 1023, height: 900, label: "1023px (데스크톱 경계 −1px)" },
  { width: 1024, height: 900, label: "1024px (데스크톱 경계)" },
  { width: 1280, height: 800, label: "1280px" },
  { width: 1440, height: 900, label: "1440px" },
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
      await page.getByRole("button", { name: "추천 생성" }).click();
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
      await page.getByRole("button", { name: "추천 생성" }).click();
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
      await page.getByRole("button", { name: "추천 생성" }).click();
      await expect(page.locator(".status-text")).toBeVisible();
      await expectNoOverflow(page);
    });

    if (width === 768) {
      test("/recommend — 좁은 컨테이너에서는 4열로 전환되지 않는다(KF-12a)", async ({ page }) => {
        await page.setViewportSize({ width: 375, height: 800 });
        await page.goto("/recommend");
        const columns = await page
          .locator(".recommend-form")
          .evaluate((el) => getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/).length);
        expect(columns).toBeLessThan(4);
      });
    }

    test(`/analysis — ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.route("**/api/v1/stats/analysis", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            numbers: [1, 2, 3, 4, 5, 6],
            oddCount: 3,
            evenCount: 3,
            lowCount: 6,
            highCount: 0,
            sumOfNumbers: 21,
            sumBucket: "21-65",
            consecutivePairCount: 5,
            rangeDistribution: [
              { range: "1-9", count: 6 },
              { range: "10-19", count: 0 },
              { range: "20-29", count: 0 },
              { range: "30-39", count: 0 },
              { range: "40-45", count: 0 },
            ],
            wonFirstPrize: true,
            firstPrizeHistory: [
              { round: 1, drawDate: "2002-12-07", firstPrizeAmount: 0 },
            ],
          }),
        }),
      );
      await page.goto("/analysis");
      await page.getByPlaceholder("예: 3, 11, 19, 28, 34, 42")
        .pressSequentially("1, 2, 3, 4, 5, 6");
      await page.getByRole("button", { name: "분석하기" }).click();
      await expect(page.getByRole("heading", { name: "분석 결과" })).toBeVisible();
      await expectNoOverflow(page);
    });
  }

  test("/recommend — 넓은 화면에서 조건과 결과 패널이 나란히 배치되고 폼은 읽기 순서를 유지한다", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/recommend");
    const studio = page.locator(".recommend-layout");
    const form = page.locator(".recommend-form");
    const result = page.getByRole("region", { name: "추천 결과" });
    const studioTracks = await studio.evaluate((el) =>
      getComputedStyle(el)
        .gridTemplateColumns.trim()
        .split(/\s+/)
        .map((v) => parseFloat(v)),
    );
    expect(studioTracks).toHaveLength(2);
    expect(studioTracks[0]).toBeGreaterThanOrEqual(450);
    expect(studioTracks[1]).toBeGreaterThanOrEqual(450);

    const formBox = (await form.boundingBox())!;
    const resultBox = (await result.boundingBox())!;
    expect(Math.abs(formBox.y - resultBox.y)).toBeLessThanOrEqual(1);
    expect(formBox.x + formBox.width).toBeLessThan(resultBox.x);

    const formTracks = await form.evaluate((el) => getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/));
    expect(formTracks).toHaveLength(1);
    await expect(form.getByRole("group", { name: "1부터 45까지 번호 선택판" })).toBeVisible();
    await expectNoOverflow(page);
  });
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

  // RW-P1-03: 넓은 화면(셸 폭 1180px)에서 장문 본문이 --prose-width(70ch) 상한을
  // 넘지 않는지 확인한다. 320/768px는 뷰포트 자체가 70ch보다 좁아 상한이 관측되지
  // 않으므로 별도의 넓은 뷰포트가 필요하다.
  test("/info/faq — 1280px, 본문 폭이 가독성 상한(--prose-width)을 넘지 않는다", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/info/faq");
    await expect(page.getByRole("heading", { name: "자주 묻는 질문" })).toBeVisible();

    const proseWidthPx = await page.evaluate(() => {
      const probe = document.createElement("div");
      probe.style.width = "70ch";
      probe.style.position = "absolute";
      probe.style.visibility = "hidden";
      document.body.appendChild(probe);
      const px = probe.getBoundingClientRect().width;
      probe.remove();
      return px;
    });
    const articleBox = await page.locator("article").first().boundingBox();
    expect(articleBox).not.toBeNull();
    expect(articleBox!.width).toBeLessThanOrEqual(proseWidthPx + 1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 모바일 하단 내비게이션 + 보조 메뉴 드로어
// ─────────────────────────────────────────────────────────────────────────────
test.describe("모바일 하단 내비게이션과 보조 메뉴", () => {
  // Pixel 5 너비 = 393px — CSS 기준 1024px 미만 → 하단 내비 + 보조 메뉴 햄버거
  test.use({ viewport: { width: 393, height: 851 } });

  test("하단 내비게이션과 보조 메뉴 버튼이 보이고, 데스크톱 내비게이션은 숨겨진다", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByTestId("mobile-bottom-nav")).toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
    await expect(page.getByTestId("desktop-nav")).toBeHidden();
  });

  test("하단 내비게이션 탭 4개(홈/추천/커뮤니티/보관함)에 이동 링크가 있고, 현재 경로에 aria-current가 붙는다", async ({
    page,
  }) => {
    await page.goto("/recommend");

    const nav = page.getByTestId("mobile-bottom-nav");
    for (const label of ["홈", "추천", "커뮤니티", "보관함"]) {
      await expect(nav.getByRole("link", { name: new RegExp(label) })).toBeVisible();
    }
    await expect(nav.getByRole("link", { name: /추천/ })).toHaveAttribute("aria-current", "page");
  });

  test("햄버거 클릭 → 보조 메뉴 드로어 열림", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 닫기" })).toBeVisible();
  });

  test("드로어 배경·패널이 뷰포트 전체 높이를 덮는다 (backdrop-filter containing block 회귀 방지)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    await expectDrawerCoversViewport(page);
  });

  test("탈출 키로 드로어 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).not.toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });

  test("뒷배경 클릭으로 드로어 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    await page.locator("[data-drawer-backdrop]").evaluate((element) => {
      (element as HTMLElement).click();
    });
    await expect(page.getByRole("dialog")).not.toBeVisible();
  });

  test("데스크톱 너비로 리사이즈 시 드로어 자동 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    // matchMedia change 이벤트를 트리거하기 위해 1024px 이상으로 리사이즈
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page.getByRole("dialog")).not.toBeVisible();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 태블릿: 모바일과 동일하게 하단 내비 + 보조 메뉴 (1024px 미만)
// ─────────────────────────────────────────────────────────────────────────────
test.describe("태블릿 하단 내비게이션과 보조 메뉴", () => {
  // 640px ≤ width < 1024px → 하단 내비 + 보조 메뉴 햄버거(모바일과 동일 구조)
  test.use({ viewport: { width: 768, height: 1024 } });

  test("하단 내비게이션과 보조 메뉴 버튼이 보이고, 데스크톱 내비게이션은 숨겨진다", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByTestId("mobile-bottom-nav")).toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
    await expect(page.getByTestId("desktop-nav")).toBeHidden();
  });

  test("햄버거 클릭 → 드로어 열림", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
  });

  test("드로어 배경·패널이 뷰포트 전체 높이를 덮는다 (backdrop-filter containing block 회귀 방지)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    await expectDrawerCoversViewport(page);
  });

  test("탈출 키로 드로어 닫힘", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).not.toBeVisible();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 데스크톱: 데스크톱 nav + 계정/테마 그룹 보임, 하단 내비·햄버거 숨겨짐
// ─────────────────────────────────────────────────────────────────────────────
test.describe("데스크톱 내비게이션", () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test("데스크톱 내비게이션이 보이고 하단 내비게이션·햄버거 버튼은 숨겨진다", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByTestId("desktop-nav")).toBeVisible();
    await expect(page.getByTestId("mobile-bottom-nav")).toBeHidden();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeHidden();
  });

  test("데이터 드롭다운에 4개 하위 링크가 있다", async ({ page }) => {
    await page.goto("/");

    await page.getByRole("button", { name: "데이터" }).click();
    for (const label of ["출현 통계", "패턴 통계", "동반 출현", "번호 분석"]) {
      await expect(page.getByRole("link", { name: label })).toBeVisible();
    }
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// RW-P1-05: ≥1024px에서도 coarse pointer(터치스크린 노트북·2-in-1)인 기기가 있다 —
// hasTouch:true는 Chromium에서 pointer:coarse 미디어 특성을 실제로 트리거한다
// (일반 44px 타깃 테스트는 Mobile Chrome/Tablet 프로젝트에서만 의미가 있어 데스크톱
// 내비가 안 보이는 좁은 뷰포트로 한정돼 있었다 — 이 조합은 그 사각지대를 메운다).
// ─────────────────────────────────────────────────────────────────────────────
test.describe("데스크톱 내비게이션 — coarse pointer 기기", () => {
  test.use({ viewport: { width: 1280, height: 800 }, hasTouch: true });

  test("coarse pointer에서도 데스크톱 내비 링크·토글이 44px 이상이다", async ({ page }) => {
    await page.goto("/");

    const isCoarse = await page.evaluate(() => window.matchMedia("(pointer: coarse)").matches);
    test.skip(!isCoarse, "이 브라우저 엔진에서는 hasTouch가 pointer:coarse를 트리거하지 않음");

    const nav = page.getByTestId("desktop-nav");
    await expect(nav).toBeVisible();

    const links = nav.getByRole("link");
    const linkCount = await links.count();
    expect(linkCount).toBeGreaterThan(0);
    for (let i = 0; i < linkCount; i++) {
      const box = await links.nth(i).boundingBox();
      expect(box).not.toBeNull();
      expect(box!.height).toBeGreaterThanOrEqual(44);
    }

    const dataToggle = nav.getByRole("button", { name: "데이터" });
    const toggleBox = await dataToggle.boundingBox();
    expect(toggleBox).not.toBeNull();
    expect(toggleBox!.height).toBeGreaterThanOrEqual(44);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 모바일 하단 내비게이션이 상시 노출되므로, 페이지 끝까지 스크롤해도
// 푸터 링크를 가리지 않는지 확인한다(광고 유무와 무관 — 광고 자체 겹침은
// e2e/ad-overlay/sticky-ad.spec.ts가 별도로 검증).
// ─────────────────────────────────────────────────────────────────────────────
test.describe("모바일 하단 내비게이션과 푸터의 공존", () => {
  test.use({ viewport: { width: 320, height: 568 } });

  test("페이지 끝까지 스크롤해도 하단 내비가 푸터 링크를 가리지 않는다", async ({ page }) => {
    await page.goto("/info/faq");

    await page.locator(".footer-nav").scrollIntoViewIfNeeded();
    const navBox = await page.getByTestId("mobile-bottom-nav").boundingBox();
    expect(navBox).not.toBeNull();

    const footerLinks = page.locator(".footer-nav a");
    const count = await footerLinks.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const box = await footerLinks.nth(i).boundingBox();
      expect(box).not.toBeNull();
      const overlaps =
        navBox!.x < box!.x + box!.width &&
        navBox!.x + navBox!.width > box!.x &&
        navBox!.y < box!.y + box!.height &&
        navBox!.y + navBox!.height > box!.y;
      expect(overlaps).toBe(false);
    }
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// R-08: 터치 기기(pointer: coarse)에서 상시 노출 컨트롤의 히트 영역이 --target-min
// (44px) 이상인지 확인. Mobile Chrome/Tablet 프로젝트에서만 의미가 있다 — 데스크톱
// (pointer: fine)에서는 44px을 강제하지 않는 게 의도이므로 그 프로젝트에서는 skip.
// ─────────────────────────────────────────────────────────────────────────────
test.describe("터치 타깃 최소 크기", () => {
  test("보조 메뉴 버튼·테마 토글·하단 내비 탭이 44px 이상이다", async ({ page }) => {
    await page.goto("/");

    const isCoarse = await page.evaluate(() => window.matchMedia("(pointer: coarse)").matches);
    test.skip(!isCoarse, "pointer: fine 프로젝트에는 해당 없음");

    const hamburgerBox = await page.locator('button[aria-label="메뉴 열기"]').boundingBox();
    expect(hamburgerBox).not.toBeNull();
    expect(hamburgerBox!.height).toBeGreaterThanOrEqual(44);
    expect(hamburgerBox!.width).toBeGreaterThanOrEqual(44);

    // 데스크톱용 ThemeToggle(AccountThemeGroup 안, CSS로만 숨김)도 DOM에는 늘 있으므로
    // 드로어 안의 것으로 범위를 좁힌다. 모바일에서는 이게 실제로 보이는 유일한 ThemeToggle이다.
    await page.getByRole("button", { name: "메뉴 열기" }).click();
    const themeToggleBox = await page.getByRole("dialog").locator(".theme-toggle").boundingBox();
    expect(themeToggleBox).not.toBeNull();
    expect(themeToggleBox!.height).toBeGreaterThanOrEqual(44);
    expect(themeToggleBox!.width).toBeGreaterThanOrEqual(44);
    await page.keyboard.press("Escape");

    const bottomNavLinks = page.getByTestId("mobile-bottom-nav").getByRole("link");
    const count = await bottomNavLinks.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const box = await bottomNavLinks.nth(i).boundingBox();
      expect(box).not.toBeNull();
      expect(box!.height).toBeGreaterThanOrEqual(44);
      expect(box!.width).toBeGreaterThanOrEqual(44);
    }
  });
});
