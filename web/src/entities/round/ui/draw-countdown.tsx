"use client";

import { useEffect, useState } from "react";

import { countdownUntil, formatCountdown, nextDrawTime } from "../draw-schedule";

import styles from "./draw-countdown.module.css";

/**
 * 다음 추첨까지 남은 시간
 *
 * **서버에서는 시간을 그리지 않는다.** 이 화면은 ISR로 캐시되므로 서버가 찍은 남은
 * 시간은 캐시된 순간에 굳어 버리고, 하이드레이션 시점의 값과도 어긋난다. 마운트 후에만
 * 채우고 그 전에는 자리만 잡아 둔다 — 레이아웃이 덜컹거리지 않게 자리는 항상 있다.
 *
 * 홈의 LCP 요소는 당첨번호이고 이 컴포넌트는 그 아래에 있다. `"use client"`를 홈
 * 페이지가 아니라 이 잎에만 붙이는 이유다(§19.2 P-8).
 */
export function DrawCountdown({ nextRound }: { nextRound: number }) {
  const [remaining, setRemaining] = useState<string | null>(null);

  useEffect(() => {
    function tick() {
      const now = new Date();
      setRemaining(formatCountdown(countdownUntil(nextDrawTime(now), now)));
    }

    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <p className={styles.countdown}>
      <span className={styles.label}>다음 {nextRound}회 추첨까지</span>
      {/* 매초 바뀌는 값을 aria-live로 읽으면 스크린리더가 쉬지 않고 떠든다.
          남은 시간은 보조 정보이므로 조용히 갱신하고, 추첨 요일·시각은 아래
          문장이 정적으로 알려 준다. */}
      <span className={styles.value} aria-hidden={remaining === null}>
        {remaining ?? "계산 중"}
      </span>
    </p>
  );
}
