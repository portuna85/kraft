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
        <span className="prize-table-mobile-label">세후 예상 금액</span>
        <span className="prize-table-after-tax-label">세후 예상 금액</span>
        <span className="prize-table-after-tax-value">{formatCurrency(calcAfterTax(prizeAmount))}</span>
      </td>
    </tr>
  );
}

export function PrizeTable({ firstPrizeAmount, secondPrize }: Props) {
  return (
    <div className="prize-table-wrap" tabIndex={0} role="region" aria-label="당첨금 표" data-allow-overflow>
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
