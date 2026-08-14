import { formatDrawDate, formatPrize } from "@/shared/lib/format";
import { Table } from "@/shared/ui/surface";

/** 분석·통계 응답이 함께 주는 역대 1등 이력 항목. */
export type FirstPrizeRecord = {
  round: number;
  drawDate: string;
  firstPrizeAmount: number;
};

/**
 * 역대 1등 이력
 *
 * 같은 조합이 여러 회차에 걸쳐 1등이 된 적이 있으면 전부 보여준다. 회차와 추첨일을
 * 함께 두는 이유는 회차 번호만으로는 언제인지 감이 오지 않기 때문이다.
 */
export function PrizeTable({ records }: { records: readonly FirstPrizeRecord[] }) {
  return (
    <Table caption="이 조합이 1등으로 당첨된 회차" captionVisible={false}>
      <thead>
        <tr>
          <th scope="col">회차</th>
          <th scope="col">추첨일</th>
          <th scope="col">1등 당첨금</th>
        </tr>
      </thead>
      <tbody>
        {records.map((record) => (
          <tr key={record.round}>
            <th scope="row">{record.round}회</th>
            <td>
              <time dateTime={record.drawDate}>{formatDrawDate(record.drawDate)}</time>
            </td>
            <td>{formatPrize(record.firstPrizeAmount)}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}
