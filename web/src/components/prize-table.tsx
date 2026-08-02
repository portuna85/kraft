"use client";

import { useEffect, useState } from "react";
import { BP } from "@/lib/breakpoints";
import { formatCurrency } from "@/lib/format";
import { calcAfterTax } from "@/lib/tax";

type Props = {
  firstPrizeAmount: number;
  secondPrize: number;
};

function PrizeRow({ rank, prizeAmount }: { rank: string; prizeAmount: number }) {
  return (
    <tr>
      <th scope="row" className="prize-table-rank">{rank} 당첨금</th>
      <td className="prize-table-amount">
        <span className="prize-table-mobile-label">세전 당첨금</span>
        {formatCurrency(prizeAmount)}
      </td>
      <td className="prize-table-after-tax">
        {/* FE-105: 같은 문구를 mobile-label과 after-tax-label로 두 번 렌더한 뒤 CSS로 하나씩
            숨기고 있었다. 실제 노출은 항상 하나였지만(display:none은 AT에서도 제외된다)
            문구를 고칠 때 두 곳을 맞춰야 하는 구조라 하나로 합치고 CSS가 형태만 바꾼다. */}
        <span className="prize-table-after-tax-label">세후 예상 금액</span>
        <span className="prize-table-after-tax-value">{formatCurrency(calcAfterTax(prizeAmount))}</span>
      </td>
    </tr>
  );
}

export function PrizeTable({ firstPrizeAmount, secondPrize }: Props) {
  // FE-105: 이 래퍼는 가로 스크롤 영역이라 tabIndex/role=region으로 키보드 스크롤을
  // 지원한다. 그런데 pages.css가 640px 미만에서 overflow를 visible로 바꿔 카드 형태로
  // 재구성하므로, 모바일에서는 스크롤할 것이 없는데 탭 정지점만 남아 있었다.
  // 서버 렌더와 첫 클라이언트 렌더는 데스크톱 기준으로 맞추고(하이드레이션 불일치 방지)
  // 마운트 후 실제 뷰포트에 맞춘다.
  const [scrollable, setScrollable] = useState(true);

  useEffect(() => {
    const query = window.matchMedia(`(min-width: ${BP.tablet}px)`);
    const sync = () => setScrollable(query.matches);
    sync();
    query.addEventListener("change", sync);
    return () => query.removeEventListener("change", sync);
  }, []);

  return (
    <div
      className="prize-table-wrap"
      tabIndex={scrollable ? 0 : undefined}
      role={scrollable ? "region" : undefined}
      aria-label={scrollable ? "당첨금 표" : undefined}
      data-allow-overflow
    >
      <table className="prize-table">
        <caption className="sr-only">1등·2등 당첨금과 세후 예상 금액</caption>
        <tbody>
          <PrizeRow rank="1등" prizeAmount={firstPrizeAmount} />
          <PrizeRow rank="2등" prizeAmount={secondPrize} />
        </tbody>
      </table>
    </div>
  );
}
