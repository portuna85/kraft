"use client";

import { useEffect, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { browserFetch } from "@/lib/browser-api";
import type { RecommendationSetSummary } from "@/features/recommendation/types";

export function RecommendationAttachmentPicker({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (setId: number | null) => void;
}) {
  const [sets, setSets] = useState<RecommendationSetSummary[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    browserFetch<RecommendationSetSummary[]>("/api/v1/recommendation-sets", {
      headers: { "X-Device-Token": getDeviceToken() },
    })
      .then((result) => setSets(Array.isArray(result) ? result : []))
      .catch(() => setSets([]))
      .finally(() => setLoaded(true));
  }, []);

  if (!loaded || sets.length === 0) {
    return null;
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
