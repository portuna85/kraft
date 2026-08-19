import { expect, test } from "@playwright/test";

import { assertMinHitArea } from "../lib/responsive-assertions";

/**
 * 부가 항목(docs/improvement.md §2.2②, §6 KF-24 인접) — `assertMinHitArea` 헬퍼
 * 자체의 알려진 한계 증명. **이것은 제품 UI 결함이 아니라 테스트 헬퍼의 오탐을
 * 기록하는 테스트다.**
 *
 * `assertMinHitArea`(e2e/lib/responsive-assertions.ts)는 `width===0 && height===0`인
 * 요소만 숨김으로 간주해 건너뛴다. `/data`의 `.cardLink`(data.module.css)는
 * stretched-link 패턴이다 — 보이는 링크 텍스트("보기")는 작지만 `::after{inset:0}`이
 * 부모 카드 전체를 실제 히트 영역으로 넓힌다. 헬퍼는 `::after`를 보지 않고 링크
 * 요소 자신의 `getBoundingClientRect()`만 재므로, 실제로는 44px를 훨씬 넘는 히트
 * 영역을 가진 링크를 "미달"로 오탐 보고한다.
 *
 * 현재 `touch-target.spec.ts`는 이 문제를 손으로 고른 셀렉터로 우회한다. 이 테스트는
 * 그 회피가 필요한 이유(헬퍼의 실제 한계)를 문서화한다.
 *
 * **이 테스트는 지금 실패해야 정상이다(red)** — `assertMinHitArea(page, ".cardLink")`를
 * 직접 호출하면 오탐이 나온다는 것 자체가 증명 대상이므로, 통과(오탐 없음)를
 * 기대하는 이 단언은 오늘 깨진다.
 */
test.use({ viewport: { width: 390, height: 844 } });

test.describe("assertMinHitArea가 stretched-link를 오탐하지 않는다 (헬퍼 한계 문서화)", () => {
  test("/data의 .cardLink가 assertMinHitArea에서 44px 미만으로 오탐되지 않는다", async ({
    page,
  }) => {
    test.fail(
      true,
      "assertMinHitArea가 stretched-link를 고려하지 않음 — 헬퍼 수정 전까지 알려진 실패",
    );
    await page.goto("/data", { waitUntil: "networkidle" });
    // 링크 자신의 시각 크기는 텍스트("보기") 크기라 44px 미만이지만, `::after`로
    // 확장된 실제 히트 영역은 카드 전체다 — 헬퍼가 이 차이를 모르는 것이 결함이다.
    // CSS Modules가 클래스명을 해시하므로 부분 일치 셀렉터로 찾는다. assertMinHitArea는
    // 비동기라 Jest류 `expect(fn).toThrow()`는 동기 예외만 잡으므로 직접 try/catch한다.
    let falsePositive: unknown = null;
    try {
      await assertMinHitArea(page, 'a[class*="cardLink"]');
    } catch (error) {
      falsePositive = error;
    }
    expect(
      falsePositive,
      "assertMinHitArea가 stretched-link(.cardLink)를 44px 미만으로 오탐 보고했다",
    ).toBeNull();
  });
});
