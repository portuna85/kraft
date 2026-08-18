import { expect, test } from "@playwright/test";

/**
 * KF-16(docs/improvement.md) — 세그먼트 404가 중첩 `main` 랜드마크와 중복 `id="main"`을
 * 만든다.
 *
 * 루트 `not-found.tsx`가 `<main id="main">`을 반환하는데, `(session)/layout.tsx`가
 * 이미 `<main id="main">`으로 children을 감싼다. `(session)/**` 하위에 세그먼트 레벨
 * `not-found.tsx`가 없어, **실제로 매치된 라우트 안에서 `notFound()`를 호출하는
 * 경우**(예: `community/posts/[id]/page.tsx:70,78`) 세션 레이아웃이 이미 마운트된
 * 채로 그 안에서 루트 not-found가 렌더된다 — main 랜드마크 중첩 + `id` 중복.
 *
 * (아예 매치되는 라우트가 없는 경로, 예: `/recommend/does-not-exist`는 Next.js가
 * `(session)/layout.tsx`를 거치지 않고 루트 레이아웃만으로 404를 렌더해 이 결함이
 * 나타나지 않는다 — 그래서 반드시 "매치된 동적 라우트 안에서 notFound() 호출"
 * 시나리오로 재현해야 한다.)
 *
 * **이 테스트는 지금 실패해야 정상이다(red).**
 */
test.describe("세션 라우트 하위 404가 main 랜드마크를 하나만 만든다", () => {
  test("존재하지 않는 게시글 상세에서 main과 #main이 각각 하나뿐이다", async ({ page }) => {
    // 픽스처(e2e/fixtures/domains/community.mjs)에는 id 1, 2 게시글만 있다 —
    // 999999는 확실히 매치되지 않아 getPost()가 404를 받고 notFound()를 던진다.
    await page.goto("/community/posts/999999");

    await expect(page.locator("main")).toHaveCount(1);
    await expect(page.locator("#main")).toHaveCount(1);
  });
});
