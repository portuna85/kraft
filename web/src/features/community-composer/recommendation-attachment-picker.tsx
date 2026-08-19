"use client";

import { useEffect, useState } from "react";

import {
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
} from "@/entities/recommendation/api";
import { RecommendationCard } from "@/entities/recommendation/ui/recommendation-card";
import {
  canQueryOwnerScope,
  sessionReadiness,
  useSession,
} from "@/entities/user-session/session-context";
import { useResource } from "@/shared/hooks/use-resource";
import { Radio } from "@/shared/ui/field";
import { ListRowsSkeleton } from "@/shared/ui/page-skeleton";
import { EmptyState, ErrorState } from "@/shared/ui/states";
import { Button } from "@/shared/ui/button";

import styles from "./recommendation-attachment-picker.module.css";

/**
 * H-03: 게시글 작성 시 내 추천 세트 중 하나를 첨부로 고른다.
 *
 * 로그인 여부에 따라 계정 스코프(`listAccountRecommendationSets`)/기기 스코프
 * (`listDeviceRecommendationSets`) 중 하나만 조회한다 — `recommend-history-list.tsx`와
 * 같은 두 갈래 규칙이다(§3.3, 백엔드 B-P0-2). v1은 첫 페이지(최근 20건)만 보여준다 —
 * 작성 중인 글에 붙일 세트를 고르는 용도라 페이지네이션 없이도 충분하다.
 */
// I-22: 전부 펼치면(최대 20개) 저장 버튼이 화면 6배 넘게 아래로 밀렸다 — 기본은
// 이만큼만 보여주고 나머지는 "더 보기"로 넘긴다.
const INITIAL_VISIBLE_COUNT = 3;

export function RecommendationAttachmentPicker({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (setId: number | null) => void;
}) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const sessionReady = sessionReadiness(session) !== "unsettled";

  /**
   * KF-22(docs/improvement.md): 페이지네이션도, 재로드 후 자체 mutation도
   * 없는 가장 단순한 사례라 items를 로컬 state로 복사할 필요 없이
   * resource.data에서 바로 파생한다. 키는 recommend-history-list와
   * **다르게** 둔다 — 같은 API를 부르지만 용도(첨부 선택 vs 이력 목록)가
   * 달라 캐시를 섞지 않는다.
   */
  const resourceKey = sessionReady ? `me:attachment-sets:${loggedIn}` : null;
  const resource = useResource(resourceKey, (signal) =>
    loggedIn ? listAccountRecommendationSets(0, signal) : listDeviceRecommendationSets(0, signal),
  );
  const items = resource.status === "success" ? resource.data.items : null;
  const loadError = resource.status === "error";

  const [showAll, setShowAll] = useState(false);
  // 스코프 전환 시점 = 키 변경 시점과 정확히 일치한다.
  useEffect(() => {
    void Promise.resolve().then(() => setShowAll(false));
  }, [resourceKey]);

  if (loadError) {
    return (
      <ErrorState
        layout="inline"
        title="추천 세트를 불러오지 못했습니다"
        description="첨부 없이 계속 쓸 수 있습니다. 다시 시도하려면 아래 버튼을 눌러 주세요."
        action={
          <Button
            variant="secondary"
            onClick={() => resource.status === "error" && resource.retry()}
          >
            다시 시도
          </Button>
        }
      />
    );
  }

  // KF-08(docs/improvement.md): 이 위젯은 기본 3개만 보여준다 — 일반적인 8행
  // 스켈레톤을 그대로 쓰면 로딩 종료 시 콘텐츠가 스켈레톤보다 작아져 시프트가
  // 오히려 커질 수 있어 실제 기본 표시 개수에 맞춘다.
  if (items === null) {
    return <ListRowsSkeleton rows={INITIAL_VISIBLE_COUNT} />;
  }

  if (items.length === 0) {
    return (
      <EmptyState
        reason="no-data"
        title="첨부할 수 있는 추천 세트가 없습니다"
        description="번호 추천에서 조합을 만들면 여기서 골라 첨부할 수 있습니다."
      />
    );
  }

  return (
    <fieldset className={styles.fieldset}>
      <legend className={styles.legend}>추천 세트 첨부(선택)</legend>

      <div className={styles.none}>
        <Radio
          name="recommendation-attachment"
          label="첨부 안 함"
          checked={value === null}
          onChange={() => onChange(null)}
        />
      </div>

      <ol className={styles.options}>
        {(showAll ? items : items.slice(0, INITIAL_VISIBLE_COUNT)).map((set) => (
          <li key={set.id} data-selected={value === set.id}>
            <Radio
              name="recommendation-attachment"
              checked={value === set.id}
              onChange={() => onChange(set.id)}
              label={
                <RecommendationCard
                  strategy={set.strategy}
                  createdAt={set.createdAt}
                  historyThroughRound={set.historyThroughRound}
                  items={set.items}
                />
              }
            />
          </li>
        ))}
      </ol>

      {!showAll && items.length > INITIAL_VISIBLE_COUNT && (
        <Button variant="secondary" onClick={() => setShowAll(true)}>
          더 보기 ({items.length - INITIAL_VISIBLE_COUNT}개 더 있음)
        </Button>
      )}
    </fieldset>
  );
}
