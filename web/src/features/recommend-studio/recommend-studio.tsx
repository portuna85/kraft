"use client";

import { useEffect, useRef } from "react";

import {
  MAX_COUNT,
  MAX_LOCKED_NUMBERS,
  MIN_COUNT,
  STRATEGIES,
  STRATEGY_DESCRIPTIONS,
  STRATEGY_LABELS,
} from "@/entities/recommendation/schema";
import { RecommendationResultRow } from "@/entities/recommendation/ui/recommendation-result-row";
import { NumberGrid } from "@/entities/round/ui/number-grid";
import { useSession } from "@/entities/user-session/session-context";
import { Button } from "@/shared/ui/button";
import { SegmentedControl } from "@/shared/ui/segmented-control";
import { ErrorState } from "@/shared/ui/states";
import { Card } from "@/shared/ui/surface";

import { RecommendationDisclaimer } from "./disclaimer";
import styles from "./studio.module.css";
import { useRecommendStudio } from "./use-recommend-studio";

/**
 * 추천 스튜디오
 *
 * 확률 고지가 결과보다 **위에** 있다. 결과 아래로 밀면 스크롤해야 보이고, 그건 노출이
 * 아니다(§3.2 법적 요구).
 */
export function RecommendStudio() {
  const session = useSession();
  // claimStatus는 보지 않는다 — claim 실패는 저장 대상 스코프와 무관하다(불변식 I-1).
  const loggedIn = session.session?.loggedIn === true;

  const studio = useRecommendStudio({ loggedIn });

  const resultsHeadingRef = useRef<HTMLHeadingElement>(null);

  /**
   * 생성 성공 시 결과로 포커스를 옮긴다.
   *
   * 특히 모바일에서 "조합 만들기"를 누른 뒤 스크린리더·키보드 사용자가 결과를
   * 직접 찾아 내려가야 하는 부담을 없앤다. `tabIndex={-1}`이라 클릭으로는
   * 못 가지만 스크립트로는 갈 수 있다.
   */
  useEffect(() => {
    if (studio.state.status === "ready") {
      resultsHeadingRef.current?.focus();
    }
  }, [studio.state.status]);

  return (
    <div className="stack">
      <RecommendationDisclaimer />

      <Card as="section" level={2}>
        <h2>어떻게 고를까요</h2>
        <div className={styles.controls}>
          <fieldset>
            <legend className="sr-only">추천 전략</legend>
            <div className={styles.strategyList}>
              {STRATEGIES.map((value) => (
                <label
                  key={value}
                  className={styles.strategyOption}
                  data-selected={studio.strategy === value}
                >
                  <input
                    type="radio"
                    name="strategy"
                    value={value}
                    checked={studio.strategy === value}
                    onChange={() => studio.setStrategy(value)}
                    className="sr-only"
                  />
                  <span className={styles.strategyName}>{STRATEGY_LABELS[value]}</span>
                  <span className={styles.strategyDescription}>{STRATEGY_DESCRIPTIONS[value]}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <div>
            <label htmlFor="count">
              조합 개수 ({MIN_COUNT}~{MAX_COUNT})
            </label>
            <input
              id="count"
              name="count"
              type="number"
              min={MIN_COUNT}
              max={MAX_COUNT}
              value={studio.count}
              onChange={(event) => studio.setCount(Number(event.target.value))}
            />
          </div>
        </div>
      </Card>

      <Card as="section" level={2}>
        <h2>고정하거나 뺄 번호</h2>
        <p className={styles.strategyDescription}>
          모드를 고른 뒤 번호를 누르면 그 모드로 선택되거나 해제됩니다. 고정은 최대{" "}
          {MAX_LOCKED_NUMBERS}개까지입니다.
        </p>

        <SegmentedControl
          aria-label="번호 선택 모드"
          options={[
            { value: "locked", label: "고정 번호" },
            { value: "excluded", label: "제외 번호" },
          ]}
          value={studio.selectionMode}
          onChange={studio.setSelectionMode}
        />

        <NumberGrid
          marks={studio.marks}
          onToggle={studio.toggleNumber}
          aria-label="고정하거나 제외할 번호 선택"
        />

        <p className={styles.markSummary} aria-live="polite">
          고정 {studio.lockedNumbers.length}/{MAX_LOCKED_NUMBERS} · 제외{" "}
          {studio.excludedNumbers.length}
        </p>

        <Button variant="quiet" onClick={studio.clearMarks}>
          선택 모두 해제
        </Button>
      </Card>

      {/*
       * 생성은 이 버튼 클릭으로만 일어난다. 화면 진입만으로 POST가 나가면 사용자가
       * 원하지 않은 추천이 이력에 쌓인다(F-P0-6/7).
       */}
      {studio.state.status === "generating" ? (
        <Button size="lg" loading loadingLabel="조합을 만드는 중">
          조합 만들기
        </Button>
      ) : (
        <Button size="lg" onClick={() => void studio.generate()}>
          조합 만들기
        </Button>
      )}

      {studio.state.status === "error" && (
        <ErrorState
          title="조합을 만들지 못했습니다"
          description={studio.state.error.message}
          action={<Button onClick={() => void studio.generate()}>다시 시도</Button>}
        />
      )}

      {studio.state.status === "ready" && (
        <section aria-labelledby="results" className="stack">
          <h2 id="results" ref={resultsHeadingRef} tabIndex={-1}>
            추천 조합
          </h2>
          <p className="sr-only" role="status">
            {studio.state.result.recommendations.length}개 조합이 생성되었습니다.
          </p>

          <Card level={1}>
            <ol className={styles.results}>
              {studio.state.result.recommendations.map((numbers, index) => {
                const item =
                  studio.state.status === "ready" ? studio.state.result.items?.[index] : undefined;
                const outcome = studio.saveOutcomes.get(index);

                return (
                  <li key={numbers.join("-")}>
                    <RecommendationResultRow
                      index={index + 1}
                      numbers={numbers}
                      explanationCodes={item?.explanationCodes}
                      action={
                        <div className={styles.resultAction}>
                          <Button
                            variant="secondary"
                            onClick={() => void studio.save(index, numbers)}
                          >
                            보관함에 저장
                          </Button>
                          {outcome !== undefined && (
                            <p className={styles.saveStatus} role="status">
                              {outcome.kind === "saved" && "저장했습니다."}
                              {outcome.kind === "duplicate" && "이미 저장한 조합입니다."}
                              {outcome.kind === "failed" && outcome.message}
                            </p>
                          )}
                        </div>
                      }
                    />
                  </li>
                );
              })}
            </ol>
          </Card>
        </section>
      )}
    </div>
  );
}
