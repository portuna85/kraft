import { expect, test } from "@playwright/test";

/**
 * KF-16(docs/improvement.md) 잔여 사례 — `(session)` 세그먼트는
 * `session-not-found-landmark.spec.ts`에서 이미 고쳐졌지만, 같은 버그가
 * `(public)` 세그먼트에도 남아 있었다.
 *
 * 루트 `not-found.tsx`가 `<main id="main">`을 반환하는데, `(public)/layout.tsx`도
 * 이미 `<main id="main">`으로 children을 감싼다. `(public)/**` 하위에 세그먼트 레벨
 * `not-found.tsx`가 없어, **실제로 매치된 라우트 안에서 `notFound()`를 호출하는
 * 경우**(`info/[slug]/page.tsx:39`, 존재하지 않는 슬러그) 공개 레이아웃이 이미
 * 마운트된 채로 그 안에서 루트 not-found가 렌더된다 — main 랜드마크 중첩 + `id` 중복.
 *
 * (아예 매치되는 라우트가 없는 경로는 Next.js가 `(public)/layout.tsx`를 거치지 않고
 * 루트 레이아웃만으로 404를 렌더해 이 결함이 나타나지 않는다 — 대조군으로 함께 둔다.)
 *
 * `(public)/not-found.tsx`(새 main 없이 본문만 렌더)를 추가해 고친다.
 */
test.describe("공개 라우트 하위 404가 main 랜드마크를 하나만 만든다", () => {
  test("존재하지 않는 정보 페이지 슬러그에서 main과 #main이 각각 하나뿐이다", async ({
    page,
  }) => {
    // info/[slug]/metadata.ts의 INFO_PAGE_SLUGS에 없는 슬러그 — isInfoPageSlug()가
    // false를 반환해 notFound()가 던져진다.
    await page.goto("/info/no-such-info-page");

    await expect(page.locator("main")).toHaveCount(1);
    await expect(page.locator("#main")).toHaveCount(1);
  });

  test("완전히 매치되지 않는 공개 경로도 main과 #main이 각각 하나뿐이다", async ({
    page,
  }) => {
    await page.goto("/no-such-public-route");

    await expect(page.locator("main")).toHaveCount(1);
    await expect(page.locator("#main")).toHaveCount(1);
  });
});
