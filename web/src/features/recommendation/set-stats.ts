// 서버 API가 세트별 통계 필드를 내려주지 않으므로 (RecommendationItemView는
// numbers/score/explanationCodes만 가진다) 번호 6개만으로 클라이언트에서 계산한다.
// 계산 기준은 백엔드 com.kraft.statistics.WinningStatisticsCacheService#analyze /
// com.kraft.common.lotto.BallClassification과 동일하게 맞춘다 — /analysis 결과와
// 같은 번호 조합이면 같은 값이 나와야 한다.

const HIGH_THRESHOLD = 23; // 1~22 저번호, 23~45 고번호
const DECADE_BUCKETS: ReadonlyArray<readonly [number, number]> = [
  [1, 9],
  [10, 19],
  [20, 29],
  [30, 39],
  [40, 45],
];

export type SetStats = {
  oddCount: number;
  evenCount: number;
  lowCount: number;
  highCount: number;
  sum: number;
  consecutivePairCount: number;
  decadeSpreadCount: number;
};

export function computeSetStats(numbers: number[]): SetStats {
  const sorted = [...numbers].sort((a, b) => a - b);
  const oddCount = sorted.filter((n) => n % 2 !== 0).length;
  const highCount = sorted.filter((n) => n >= HIGH_THRESHOLD).length;
  const sum = sorted.reduce((total, n) => total + n, 0);

  let consecutivePairCount = 0;
  for (let i = 0; i < sorted.length - 1; i++) {
    const current = sorted[i];
    const next = sorted[i + 1];
    if (current !== undefined && next !== undefined && next - current === 1) {
      consecutivePairCount++;
    }
  }

  const decadeSpreadCount = DECADE_BUCKETS.filter(([min, max]) =>
    sorted.some((n) => n >= min && n <= max),
  ).length;

  return {
    oddCount,
    evenCount: sorted.length - oddCount,
    lowCount: sorted.length - highCount,
    highCount,
    sum,
    consecutivePairCount,
    decadeSpreadCount,
  };
}

export function formatSetStatsSummary(stats: SetStats): string {
  const consecutiveText =
    stats.consecutivePairCount === 0 ? "없음" : `${stats.consecutivePairCount}쌍`;
  return `홀짝 ${stats.oddCount}:${stats.evenCount} · 고저 ${stats.lowCount}:${stats.highCount} · 합계 ${stats.sum} · 연속번호 ${consecutiveText} · 구간 ${stats.decadeSpreadCount}개 분포`;
}
