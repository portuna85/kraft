import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// KX-01 회귀 스모크 — GPT 라이브 감사(2026-07-30)가 발견한 "3억 초과 당첨금 세후 계산이
// 초과분을 분리하지 않고 전액에 높은 세율을 적용" 결함을 재발 방지한다. 표시된 세후
// 금액을 앱 코드(lib/tax.ts)를 거치지 않고 세법 공식으로 직접 재계산해 독립적으로
// 검증한다 — 계산 로직 자체가 틀리면 앱과 테스트가 같은 실수를 공유해 못 잡아내므로,
// 여기서는 lib/tax.ts를 import하지 않는다.
//
// 픽스처 백엔드(e2e/fixtures/backend.mjs ROUNDS_LATEST)의 1등 금액(2,145,678,900원)은
// 3억 초과 구간을 지나가도록 의도적으로 고른 값이다 — 3억 이하 금액만으로는 "초과분
// 미분리" 버그(단순 분기 vs 구간 분리)가 같은 결과를 내 재발을 잡아내지 못한다.
function expectedAfterTax(amount: number): number {
  const HIGH_PRIZE_THRESHOLD = 300_000_000;
  const LOW_PRIZE_THRESHOLD = 2_000_000;
  if (amount > HIGH_PRIZE_THRESHOLD) {
    return Math.floor(HIGH_PRIZE_THRESHOLD * 0.78 + (amount - HIGH_PRIZE_THRESHOLD) * 0.67);
  }
  if (amount > LOW_PRIZE_THRESHOLD) {
    return Math.floor(amount * 0.78);
  }
  return amount;
}

// 부동소수점 리터럴 표현 차이(0.78 vs 1-0.22)만으로도 floor 결과가 1원 어긋날 수 있다 —
// 이 스모크가 잡아야 할 KX-01 회귀(초과분 미분리)는 3천만원대 오차이므로, 그와 무관한
// 반올림 잡음만 허용한다.
const ROUNDING_TOLERANCE_WON = 2;

function parseWon(text: string): number {
  const digits = text.replace(/[^0-9]/g, "");
  return Number(digits);
}

test("/ — 1·2등 세후 표시금액이 세법 구간 분리 공식과 일치한다(KX-01 회귀 스모크)", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/");

  // FE-105: 640px 미만에서는 표가 카드로 재구성되어 스크롤할 것이 없으므로 region/tabIndex를
  // 부여하지 않는다. 이 검사는 세후 금액 계산이 목적이라 뷰포트와 무관해야 하므로
  // 래퍼 클래스로 잡는다(모바일 프로젝트에서도 동일하게 동작).
  const table = page.locator(".prize-table-wrap");
  const rows = table.locator("tbody tr");

  const firstPreTax = parseWon((await rows.nth(0).locator(".prize-table-amount").innerText()).trim());
  const firstAfterTax = parseWon((await rows.nth(0).locator(".prize-table-after-tax-value").innerText()).trim());
  const secondPreTax = parseWon((await rows.nth(1).locator(".prize-table-amount").innerText()).trim());
  const secondAfterTax = parseWon((await rows.nth(1).locator(".prize-table-after-tax-value").innerText()).trim());

  // 픽스처 1등 금액이 실제로 3억을 넘는지 먼저 확인 — 픽스처가 바뀌어 더 이상 이
  // 회귀를 검증하지 못하게 되는 상황을 조용히 지나치지 않는다.
  expect(firstPreTax).toBeGreaterThan(300_000_000);

  expect(Math.abs(firstAfterTax - expectedAfterTax(firstPreTax))).toBeLessThanOrEqual(ROUNDING_TOLERANCE_WON);
  expect(Math.abs(secondAfterTax - expectedAfterTax(secondPreTax))).toBeLessThanOrEqual(ROUNDING_TOLERANCE_WON);
});
