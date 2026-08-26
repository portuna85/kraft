"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef } from "react";

import { ROUTES } from "@/shared/config/routes";
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
import { sessionReadiness, useSession } from "@/entities/user-session/session-context";
import { Badge } from "@/shared/ui/badge";
import { Button, LinkButton } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/field";
import { SegmentedControl } from "@/shared/ui/segmented-control";
import { ErrorState } from "@/shared/ui/states";
import { Card } from "@/shared/ui/surface";

import { RecommendationDisclaimer } from "./disclaimer";
import { GenerateActionBar } from "./generate-action-bar";
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
  // KF-09: 세션이 미확정인 동안은 생성·저장을 게이팅한다(아래 버튼 비활성화 + 훅
  // 내부의 이중 안전장치).
  const sessionReady = sessionReadiness(session) !== "unsettled";

  const studio = useRecommendStudio({ loggedIn, sessionReady });

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

  // FE-PERF-03(docs/improvement.md): 인라인 화살표로 넘기면 NumberGrid의 memo()가
  // 매 렌더 무효화된다 — selectionMode/lockedNumbers.length/marks가 실제로 바뀔 때만
  // 새로 만든다.
  const isNumberDisabled = useCallback(
    (value: number) =>
      studio.selectionMode === "locked" &&
      studio.lockedNumbers.length >= MAX_LOCKED_NUMBERS &&
      (studio.marks.get(value) ?? "none") !== "locked",
    [studio.selectionMode, studio.lockedNumbers.length, studio.marks],
  );

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

          <TextField
            label={`조합 개수 (${MIN_COUNT}~${MAX_COUNT})`}
            hint={
              studio.countClamped
                ? `${MIN_COUNT}~${MAX_COUNT} 사이 값만 가능해 자동으로 조정했습니다.`
                : undefined
            }
            name="count"
            type="number"
            min={MIN_COUNT}
            max={MAX_COUNT}
            value={studio.count}
            onChange={(event) => studio.setCount(Number(event.target.value))}
          />
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
          isDisabled={isNumberDisabled}
          aria-label="고정하거나 제외할 번호 선택"
        />

        {/* kraft-redesign-plan.md P0: "선택 요약 칩" — aria-live 영역은 그대로,
            시각적으로만 Badge 톤을 입혀 눈에 더 잘 띄게 한다. */}
        <p className={styles.markSummary} aria-live="polite">
          <Badge tone="brand" label={`고정 ${studio.lockedNumbers.length}/${MAX_LOCKED_NUMBERS}`} />{" "}
          <Badge tone="neutral" label={`제외 ${studio.excludedNumbers.length}`} />
        </p>
        {/* I-27: 상한 도달 후 클릭이 조용히 무시돼 키보드가 고장 난 것처럼 보였다 —
            거부된 번호가 바뀔 때마다 문구가 갱신돼 aria-live가 매번 재공지한다. */}
        {studio.rejectedNumber !== null && (
          <p className={styles.markSummary} role="status">
            {studio.rejectedNumber}번은 선택할 수 없습니다 — 고정은 최대 {MAX_LOCKED_NUMBERS}
            개까지입니다. 다른 번호를 해제한 뒤 다시 선택해 주세요.
          </p>
        )}

        <Button variant="quiet" onClick={studio.clearMarks}>
          선택 모두 해제
        </Button>
      </Card>

      {/*
       * 생성은 이 버튼 클릭으로만 일어난다. 화면 진입만으로 POST가 나가면 사용자가
       * 원하지 않은 추천이 이력에 쌓인다(F-P0-6/7).
       */}
      <GenerateActionBar
        generating={studio.state.status === "generating"}
        disabled={!sessionReady}
        onGenerate={() => void studio.generate()}
      />

      {studio.state.status === "error" && (
        <ErrorState
          title="조합을 만들지 못했습니다"
          description={studio.state.error.message}
          action={<Button onClick={() => void studio.generate()}>다시 시도</Button>}
        />
      )}

      {studio.state.status === "ready" && (
        <section aria-labelledby="results" className="stack">
          <div className={styles.resultsHeader}>
            <h2 id="results" ref={resultsHeadingRef} tabIndex={-1}>
              추천 조합
            </h2>
            <div className={styles.resultsHeaderActions}>
              {/* kraft-redesign-plan.md P0: 조합마다 따로 누르지 않아도 전부
                  저장할 수 있게 한다 — studio.save는 이미 index별 독립 호출을
                  지원하므로(useRecommendStudio) 한 번의 클릭에서 N번 호출할
                  뿐, 훅에는 새 API가 필요 없다. */}
              <Button
                variant="secondary"
                size="sm"
                disabled={studio.isStale}
                onClick={() => {
                  if (studio.state.status !== "ready") return;
                  studio.state.result.recommendations.forEach((numbers, index) => {
                    void studio.save(index, numbers);
                  });
                }}
              >
                전체 저장
              </Button>
              {/* I-21: /recommend/history가 어느 내비게이션에서도 도달 불가능했다 —
                  결과가 막 생겼을 때 바로 확인할 수 있는 진입점을 여기도 둔다. */}
              <Link href={ROUTES.recommendHistory} className={styles.historyLink}>
                추천 이력 보기
              </Link>
            </div>
          </div>
          <p className="sr-only" role="status">
            {studio.state.result.recommendations.length}개 조합이 생성되었습니다.
          </p>

          {/* I-28: 결과가 나온 뒤 고정·제외를 바꿔도 아래 결과는 그대로였다 — 지금
              조건으로 다시 만들기 전까지는 저장을 막고 눈에 띄게 알린다. */}
          {studio.isStale && (
            <p className={styles.staleNotice} role="status">
              조건이 바뀌었습니다 · 다시 만들기
            </p>
          )}

          <Card level={1}>
            <ol className={styles.results}>
              {studio.state.result.recommendations.map((numbers, index) => {
                const item =
                  studio.state.status === "ready" ? studio.state.result.items?.[index] : undefined;
                const outcome = studio.saveOutcomes.get(index);

                return (
                  // KF-26(FE-OPT-43, docs/improvement.md): 값 기반 key였는데
                  // 바로 아래 saveOutcomes는 index로 조회한다 — 중복 조합이
                  // 섞이면 두 축이 어긋나 "저장했습니다" 배지가 엉뚱한 행에
                  // 붙을 수 있었다. recommendations는 generate() 호출마다
                  // 통째로 교체되는 원자적 결과라 index를 key로 써도 안전하다.
                  <li key={index}>
                    <RecommendationResultRow
                      index={index + 1}
                      numbers={numbers}
                      explanationCodes={item?.explanationCodes}
                      action={
                        <div className={styles.resultAction}>
                          <div className={styles.resultActionButtons}>
                            <Button
                              variant="secondary"
                              disabled={studio.isStale}
                              onClick={() => void studio.save(index, numbers)}
                            >
                              보관함에 저장
                            </Button>
                            {/* kraft-redesign-plan.md P0: 이 조합을 그대로 분석
                                화면으로 넘긴다 — CombinationPicker와 같은 GET
                                쿼리 계약(`?numbers=1,2,...`)을 쓴다. */}
                            <LinkButton
                              variant="quiet"
                              href={`${ROUTES.analysis}?numbers=${numbers.join(",")}`}
                            >
                              이 조합으로 진단하기
                            </LinkButton>
                          </div>
                          {/* I-29: 저장 결과 문구가 나타날 때마다 아래 조합들이
                              밀렸다 — 결과가 없을 때도 자리를 미리 잡아둔다.
                              "이미 저장" 은 실패가 아니지만 새 성공과 같은 색으로
                              보이면 구분이 안 되어 톤을 분리한다. */}
                          <div className={styles.saveStatus} role="status">
                            {outcome?.kind === "saved" && (
                              <Badge tone="success" label="저장했습니다." />
                            )}
                            {outcome?.kind === "duplicate" && (
                              <Badge tone="neutral" label="이미 저장한 조합입니다." />
                            )}
                            {outcome?.kind === "failed" && (
                              <span className={styles.saveError}>{outcome.message}</span>
                            )}
                          </div>
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
