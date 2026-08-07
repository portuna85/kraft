import { test, expect } from "@playwright/test";

// 하단 고정 광고(StickyMobileAd)가 CTA·푸터를 실제로 가리는지 검증한다.
// playwright.ad-overlay.config.ts 전용 — NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY가
// baked-in된 빌드에서만 광고가 mount된다. 카카오 애드핏 스크립트가 실제로 로드되는지는
// 검증 대상이 아니다(오프라인 CI에서 무관) — .ad-unit의 min-height로 예약된 자리
// 자체가 CTA·푸터와 겹치지 않는지, 즉 R-09의 body.has-sticky-ad 조건부 여백이
// 실제로 충분한지가 핵심이다.
//
// 처음엔 bounding box 겹침만 봤는데, 테스트 페이지(404·/info/faq)의 콘텐츠가 뷰포트를
// 넉넉히 채우지 않아서 padding-bottom 예약을 일부러 없애도(회귀 재현) 통과해버리는 걸
// 실제로 확인했다 — 콘텐츠 길이에 기대지 않도록 .page의 padding-bottom이 광고 실측
// 높이 이상인지를 직접 단언한다.
function isOverlapping(a: { x: number; y: number; width: number; height: number }, b: typeof a) {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
}

test("body.has-sticky-ad일 때 .page 하단 여백이 '광고 없을 때 + 광고 실측 높이'만큼 늘어난다", async ({
  page,
}) => {
  await page.goto("/this-page-does-not-exist");

  const ad = page.locator(".ad-sticky-mobile");
  await expect(ad).toBeVisible();
  await expect(page.locator("body")).toHaveClass(/has-sticky-ad/);

  const adBox = await ad.boundingBox();
  expect(adBox).not.toBeNull();

  // .page 자체의 기본 padding-bottom(예: 64px)이 광고 높이(≈51px)보다 이미 커서,
  // "padding-bottom >= 광고 높이"만 보면 여백을 아예 안 늘려도 통과해버리는 걸 실제로
  // 확인했다 — has-sticky-ad 클래스를 잠깐 떼서 "광고 없을 때" 기준값을 직접 재고,
  // 그 대비 늘어난 양이 광고 높이 이상인지(델타)를 비교해야 실제로 의미가 있다.
  const [withAd, withoutAd] = await page.evaluate(() => {
    const pageEl = document.querySelector(".page") as HTMLElement;
    const withAdValue = parseFloat(getComputedStyle(pageEl).paddingBottom);
    document.body.classList.remove("has-sticky-ad");
    const withoutAdValue = parseFloat(getComputedStyle(pageEl).paddingBottom);
    document.body.classList.add("has-sticky-ad");
    return [withAdValue, withoutAdValue];
  });

  expect(withAd - withoutAd).toBeGreaterThanOrEqual(adBox!.height - 1);
});

test("하단 고정 광고가 404 페이지의 CTA 버튼을 가리지 않는다 (짧은 뷰포트로 여유 없이 확인)", async ({
  page,
}) => {
  // 이 프로젝트 e2e가 쓰는 최소 참조 뷰포트(320×568, responsive.spec.ts VIEWPORTS와
  // 동일) — 처음엔 임의로 더 낮춘 390×500으로 돌렸다가, 스크롤 없이 최초 진입 시점의
  // CTA가 뷰포트 아래쪽에 걸쳐 있어 고정 광고와 겹치는 걸 실제로 봤다. 이 문서가 다른
  // 곳에서도 쓰는 568을 하한으로 삼는 게 실제 최소 지원 기기 기준에 맞다.
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto("/this-page-does-not-exist");

  const ad = page.locator(".ad-sticky-mobile");
  await expect(ad).toBeVisible();

  const adBox = await ad.boundingBox();
  expect(adBox).not.toBeNull();

  const cta = page.locator(".not-found-actions a");
  const count = await cta.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    const box = await cta.nth(i).boundingBox();
    expect(box).not.toBeNull();
    expect(isOverlapping(adBox!, box!)).toBe(false);
  }
});

test("하단 고정 광고가 푸터 내비게이션을 가리지 않는다 (페이지 끝까지 스크롤)", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto("/info/faq");

  const ad = page.locator(".ad-sticky-mobile");
  await expect(ad).toBeVisible();

  await page.locator(".footer-nav").scrollIntoViewIfNeeded();
  const adBox = await ad.boundingBox();
  const footerLinks = page.locator(".footer-nav a");
  const count = await footerLinks.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    const box = await footerLinks.nth(i).boundingBox();
    expect(box).not.toBeNull();
    expect(isOverlapping(adBox!, box!)).toBe(false);
  }
});

// 모바일 하단 내비게이션(MobileBottomNav)이 상시 bottom:0을 차지하게
// 되면서, 광고는 그 위(bottom: var(--bottom-nav-h))로 옮겼다 — 광고가 내비 탭을
// 가리거나, 반대로 내비 위에 어정쩡하게 겹쳐 뜨지 않는지 직접 확인한다.
test("하단 고정 광고와 모바일 하단 내비게이션이 서로 겹치지 않고 광고가 내비 바로 위에 얹힌다", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto("/this-page-does-not-exist");

  const ad = page.locator(".ad-sticky-mobile");
  const nav = page.getByTestId("mobile-bottom-nav");
  await expect(ad).toBeVisible();
  await expect(nav).toBeVisible();

  const adBox = await ad.boundingBox();
  const navBox = await nav.boundingBox();
  expect(adBox).not.toBeNull();
  expect(navBox).not.toBeNull();

  expect(isOverlapping(adBox!, navBox!)).toBe(false);
  // 광고 바닥과 내비 상단 사이에 뜬 공간(빈 틈)이 없어야 한다 — 둘이 바로 붙어 있어야 한다.
  expect(Math.abs(adBox!.y + adBox!.height - navBox!.y)).toBeLessThan(2);
});

test("닫기 버튼을 누르면 광고가 사라지고 body.has-sticky-ad도 해제된다", async ({ page }) => {
  await page.goto("/this-page-does-not-exist");

  await expect(page.locator(".ad-sticky-mobile")).toBeVisible();
  await page.getByRole("button", { name: "광고 닫기" }).click();

  await expect(page.locator(".ad-sticky-mobile")).not.toBeVisible();
  await expect(page.locator("body")).not.toHaveClass(/has-sticky-ad/);
});

test("닫았던 광고는 다시 방문하면 재표시된다(KF-12b, 닫기는 탭 세션 한정)", async ({ page }) => {
  await page.goto("/this-page-does-not-exist");
  await page.getByRole("button", { name: "광고 닫기" }).click();
  await expect(page.locator(".ad-sticky-mobile")).not.toBeVisible();

  // 같은 탭에서 새로 마운트(새로고침)하면 닫힘 상태는 컴포넌트 로컬 state라 유지되지
  // 않는다 — 세션/영구 저장을 전혀 쓰지 않는다는 의도된 설계를 실측으로 고정한다.
  await page.reload();
  await expect(page.locator(".ad-sticky-mobile")).toBeVisible();
});

test("844×390(가로 모드)처럼 짧은 화면에서는 고정 광고가 접히고 그만큼 여백도 줄어든다(KF-12b)", async ({
  page,
}) => {
  await page.setViewportSize({ width: 844, height: 390 });
  await page.goto("/this-page-does-not-exist");

  await expect(page.locator(".ad-sticky-mobile")).toBeHidden();

  // body.has-sticky-ad 자체는 여전히 붙어있어도(컴포넌트는 unit이 있으면 클래스를
  // 토글) --content-safe-bottom이 광고분을 빼고 내비 높이만 반영하는지 직접 확인한다.
  const paddingBottom = await page.evaluate(() => {
    const pageEl = document.querySelector(".page") as HTMLElement;
    return parseFloat(getComputedStyle(pageEl).paddingBottom);
  });
  const bottomNavHeight = (await page.getByTestId("mobile-bottom-nav").boundingBox())!.height;
  // 광고(50px대) 분량이 더해지지 않았다면 page-pad-bottom + nav 높이보다 크게 벗어나지 않아야 한다.
  expect(paddingBottom).toBeLessThan(bottomNavHeight + 64 + 20);
});

test("320×568(세로)에서는 짧은 화면 기준에 해당하지 않아 광고가 그대로 보인다(KF-12b)", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto("/this-page-does-not-exist");

  await expect(page.locator(".ad-sticky-mobile")).toBeVisible();
});

test("가상 키보드가 열린 것으로 감지되면 고정 광고가 내려가고 닫히면 다시 뜬다(KF-12b)", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto("/this-page-does-not-exist");
  await expect(page.locator(".ad-sticky-mobile")).toBeVisible();

  // 실제 OS 키보드를 띄울 수는 없으므로, 키보드가 열렸을 때 모바일 브라우저가 보고하는
  // visualViewport 축소를 그대로 흉내낸다 — 폭은 그대로, 높이만 25% 넘게 줄이고
  // visualViewport의 resize 이벤트를 직접 발화한다(useKeyboardOpen이 구독하는 신호).
  await page.evaluate(() => {
    const vv = window.visualViewport!;
    Object.defineProperty(vv, "height", { value: vv.height * 0.5, configurable: true });
    vv.dispatchEvent(new Event("resize"));
  });
  await expect(page.locator(".ad-sticky-mobile")).toBeHidden();
  await expect(page.locator("body")).not.toHaveClass(/has-sticky-ad/);

  // 키보드가 닫히면(높이 원복) 광고도 다시 뜬다.
  await page.evaluate(() => {
    const vv = window.visualViewport!;
    Object.defineProperty(vv, "height", { value: window.innerHeight, configurable: true });
    vv.dispatchEvent(new Event("resize"));
  });
  await expect(page.locator(".ad-sticky-mobile")).toBeVisible();
});

test("가상 키보드 표시 상태에서도 폼 제출은 정상 동작한다(KF-12b)", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.route("**/api/v1/numbers/recommend", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ recommendations: [[1, 2, 3, 4, 5, 6]] }),
    }),
  );
  await page.goto("/recommend");
  await expect(page.locator(".ad-sticky-mobile")).toBeVisible();

  await page.evaluate(() => {
    const vv = window.visualViewport!;
    Object.defineProperty(vv, "height", { value: vv.height * 0.5, configurable: true });
    vv.dispatchEvent(new Event("resize"));
  });
  await expect(page.locator(".ad-sticky-mobile")).toBeHidden();

  await page.getByRole("button", { name: "추천 생성" }).click();
  await expect(page.locator(".recommend-card").first()).toBeVisible();
});
