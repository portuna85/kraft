import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// R-001 회귀 스모크 — 홈(회차 API)과 /frequency·/stats·/companion(통계 API)은 서로 다른
// 백엔드 엔드포인트에서 각자 회차 수를 계산해 내려준다. 정상 상태라면 항상 같은 값이어야
// 하지만, 캐시가 어긋나거나 통계 파이프라인이 회차 API보다 뒤처지면 페이지마다 다른
// 회차가 보일 수 있다 — 이 스모크는 그 어긋남을 잡는다(정상 배포에서는 항상 통과해야
// 한다는 전제로, 값이 실제로 몇 회인지는 검증하지 않고 "페이지 간 일치"만 검증한다).
function extractRoundNumber(text: string): number {
  const match = text.match(/(\d+)\s*회/);
  if (!match) throw new Error(`회차 숫자를 찾을 수 없습니다: ${text}`);
  return Number(match[1]);
}

test("홈·출현 통계·패턴 통계·동반 출현 페이지의 회차 수가 서로 일치한다(R-001 회귀 스모크)", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/");
  // 홈 제목은 "이번 주 당첨결과"라 회차가 없다. 회차는 카드 안의 전용 훅에서 읽는다.
  const homeRoundText = await page.getByTestId("latest-round").innerText();
  const homeRound = extractRoundNumber(homeRoundText);

  await gotoAndWaitForRealContent(page, "/frequency");
  const frequencySummary = await page.getByText(/총 \d+회 전체 기준|최근 \d+회 기준/).innerText();
  const frequencyRound = extractRoundNumber(frequencySummary);

  await gotoAndWaitForRealContent(page, "/stats");
  const statsSummary = await page.getByText(/^총 \d+회 기준$/).innerText();
  const statsRound = extractRoundNumber(statsSummary);

  await gotoAndWaitForRealContent(page, "/companion");
  const companionSummary = await page.getByText(/^총 \d+회 기준/).innerText();
  const companionRound = extractRoundNumber(companionSummary);

  expect(frequencyRound).toBe(homeRound);
  expect(statsRound).toBe(homeRound);
  expect(companionRound).toBe(homeRound);
});
