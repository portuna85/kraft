"use client";

import { useDisclosure } from "@/shared/hooks/use-disclosure";
import { InlineAlert } from "@/shared/ui/states";

import styles from "./disclaimer.module.css";

/**
 * 확률 고지 (법적 요구)
 *
 * **모든 추천 표면에 노출한다.** 추천 결과가 보이는 곳에 함께 있어야 하고, 결과 아래로
 * 밀려 스크롤해야 보이는 위치는 노출로 치지 않는다. 푸터에도 같은 고지가 있지만
 * 그것과 별개다 — 사용자는 추천을 받는 그 순간에 읽어야 한다.
 *
 * kraft-redesign-plan.md P0: 두 문단짜리 고지가 화면 상단을 길게 차지해 "조합 만들기"에
 * 닿기까지의 체감 스크롤을 늘렸다 — 확률 문구(법적으로 반드시 남겨야 하는 숫자)는
 * 기본으로 보이게 유지하고, 구매 책임 문단은 "자세히 보기"로 접는다. 확률 문구 자체는
 * 절대 숨기지 않는다.
 */
export function RecommendationDisclaimer() {
  const { isOpen, toggle } = useDisclosure();

  return (
    <InlineAlert tone="warning" title="추천 번호에 대해 알아두세요">
      <p>
        이 조합은 통계 결과일 뿐 당첨을 보장하지 않으며, 1등 확률은 어떤 조합이든 8,145,060분의 1로
        같습니다.{" "}
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={isOpen}
          aria-controls="disclaimer-detail"
          onClick={toggle}
        >
          {isOpen ? "간단히 보기" : "자세히 보기"}
        </button>
      </p>
      <div id="disclaimer-detail" hidden={!isOpen}>
        <p>
          이 조합은 과거 당첨 데이터를 바탕으로 만든 통계 결과일 뿐이며, 로또는 매 회차 독립적인
          무작위 추첨입니다.
        </p>
        <p>구매는 본인의 판단과 책임으로, 감당할 수 있는 범위에서만 하세요.</p>
      </div>
    </InlineAlert>
  );
}
