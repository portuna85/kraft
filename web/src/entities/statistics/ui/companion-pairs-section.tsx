"use client";

import { useState } from "react";

import { Button } from "@/shared/ui/button";
import { Table } from "@/shared/ui/surface";

import type { CompanionPair } from "../schema";
import { CompanionPairRow } from "./companion-pair-row";

/**
 * 필터 없는 상태의 동반 출현 결과
 *
 * 서버는 지금처럼 상위 50쌍을 그대로 내려준다(불변식 — 990쌍 전량 전송 금지,
 * 페이로드는 50개로 유지). 이 컴포넌트는 그 50개를 **다 받아 둔 채로** 처음엔
 * 12개만 그리고, "더 보기"를 누르면 추가 API 호출 없이 나머지를 펼친다 —
 * 이 불변식은 네트워크 페이로드 개수에 대한 것이지 초기 DOM 렌더 개수에 대한
 * 것이 아니라 둘 다 만족시킬 수 있다.
 */
const INITIAL_VISIBLE_LIMIT = 12;

export function CompanionPairsSection({
  pairs,
  totalRounds,
  expectedLabel,
}: {
  pairs: readonly CompanionPair[];
  totalRounds: number;
  expectedLabel: string;
}) {
  const [visibleCount, setVisibleCount] = useState(INITIAL_VISIBLE_LIMIT);
  const visible = pairs.slice(0, visibleCount);
  const hasMore = visibleCount < pairs.length;

  return (
    <>
      <p className="note" role="status">
        {visible.length}개 쌍을 표시하고 있습니다. 배율은 쌍당 평균 {expectedLabel}회 대비 값입니다.
      </p>

      <Table caption="번호 쌍별 동반 출현 횟수와 평균 대비 배율" captionVisible={false}>
        <thead>
          <tr>
            <th scope="col">번호 쌍</th>
            <th scope="col">함께 나온 횟수</th>
            <th scope="col">평균 대비</th>
          </tr>
        </thead>
        <tbody>
          {visible.map((pair, index) => (
            <CompanionPairRow
              key={`${pair.ballA}-${pair.ballB}`}
              pair={pair}
              totalRounds={totalRounds}
              rank={index + 1}
            />
          ))}
        </tbody>
      </Table>

      {hasMore && (
        <Button variant="secondary" onClick={() => setVisibleCount(pairs.length)}>
          상위 {pairs.length}개 모두 보기
        </Button>
      )}
    </>
  );
}
