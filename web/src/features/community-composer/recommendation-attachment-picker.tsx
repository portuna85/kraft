"use client";

import { useCallback, useEffect, useState } from "react";

import {
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
} from "@/entities/recommendation/api";
import type { RecommendationSet } from "@/entities/recommendation/schema";
import { RecommendationCard } from "@/entities/recommendation/ui/recommendation-card";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { Radio } from "@/shared/ui/field";
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
export function RecommendationAttachmentPicker({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (setId: number | null) => void;
}) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);

  const [items, setItems] = useState<RecommendationSet[] | null>(null);
  const [loadError, setLoadError] = useState(false);

  const load = useCallback(async () => {
    setLoadError(false);
    setItems(null);
    try {
      const result = loggedIn
        ? await listAccountRecommendationSets(0)
        : await listDeviceRecommendationSets(0);
      setItems(result.items);
    } catch {
      setItems(null);
      setLoadError(true);
    }
  }, [loggedIn]);

  useEffect(() => {
    if (session.loading) return;

    let cancelled = false;
    void Promise.resolve().then(() => {
      if (!cancelled) void load();
    });

    return () => {
      cancelled = true;
    };
  }, [load, session.loading]);

  if (loadError) {
    return (
      <ErrorState
        layout="inline"
        title="추천 세트를 불러오지 못했습니다"
        description="첨부 없이 계속 쓸 수 있습니다. 다시 시도하려면 아래 버튼을 눌러 주세요."
        action={
          <Button variant="secondary" onClick={() => void load()}>
            다시 시도
          </Button>
        }
      />
    );
  }

  if (items === null) {
    return <p className={styles.note}>추천 세트를 불러오는 중입니다…</p>;
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
        {items.map((set) => (
          <li key={set.id}>
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
    </fieldset>
  );
}
