"use client";

import { useEffect, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { browserFetch } from "@/lib/browser-api";
import type { PageResponse } from "@/lib/community-api";
import type { RecommendationSetSummary } from "@/features/recommendation/types";
import { ErrorState } from "@/ui/primitives/error-state";
import { asyncError, asyncLoading, asyncSuccess, type AsyncState } from "@/lib/async-state";

export function RecommendationAttachmentPicker({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (setId: number | null) => void;
}) {
  // FE-036: 예전에는 sets·loaded·loadError 세 플래그가 나란히 있어
  // "로딩 끝났는데 실패했고 데이터도 있다" 같은 조합이 타입상 가능했다.
  const [state, setState] = useState<AsyncState<RecommendationSetSummary[]>>(asyncLoading);
  const [retryKey, setRetryKey] = useState(0);

  // FE-053: 이전에는 로딩 중·조회 실패·세트 0개가 모두 null 렌더로 같아 보였다.
  // 특히 실패를 .catch(() => setSets([]))로 흡수해 "첨부 기능이 없는 화면"처럼 보였다.
  useEffect(() => {
    let cancelled = false;
    browserFetch<PageResponse<RecommendationSetSummary>>(
      "/api/v1/recommendation-sets?page=0&size=50",
      { headers: { "X-Device-Token": getDeviceToken() } }
    )
      .then((result) => {
        if (!cancelled) setState(asyncSuccess(Array.isArray(result?.items) ? result.items : []));
      })
      .catch(() => {
        if (!cancelled) setState(asyncError);
      });
    return () => {
      cancelled = true;
    };
  }, [retryKey]);

  if (state.status === "loading" || state.status === "idle") {
    return (
      <fieldset className="community-recommendation-picker" aria-busy="true">
        <legend>추천 세트 첨부(선택)</legend>
        <span className="skeleton-line skeleton-body" />
      </fieldset>
    );
  }

  if (state.status === "error") {
    return (
      <fieldset className="community-recommendation-picker">
        <legend>추천 세트 첨부(선택)</legend>
        <ErrorState
          variant="inline"
          title="추천 이력을 불러오지 못했습니다."
          retry={{
            label: "다시 시도",
            onClick: () => {
              setState(asyncLoading);
              setRetryKey((current) => current + 1);
            },
          }}
        />
      </fieldset>
    );
  }

  const sets = state.data;

  if (sets.length === 0) {
    return (
      <fieldset className="community-recommendation-picker">
        <legend>추천 세트 첨부(선택)</legend>
        <p className="muted">첨부할 추천 세트가 없습니다. 번호 추천에서 조합을 만들면 여기에서 첨부할 수 있습니다.</p>
      </fieldset>
    );
  }

  return (
    <fieldset className="community-recommendation-picker">
      <legend>추천 세트 첨부(선택)</legend>
      <label>
        <input
          type="radio"
          name="recommendation-attachment"
          checked={value === null}
          onChange={() => onChange(null)}
        />
        첨부 안 함
      </label>
      {sets.map((set) => (
        <label key={set.id}>
          <input
            type="radio"
            name="recommendation-attachment"
            checked={value === set.id}
            onChange={() => onChange(set.id)}
          />
          {set.items.map((item) => (
            <LottoBalls key={item.position} numbers={item.numbers} />
          ))}
        </label>
      ))}
    </fieldset>
  );
}
