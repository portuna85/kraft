"use client";

import { useCallback, useRef, useState } from "react";

import { recommendNumbers } from "@/entities/recommendation/api";
import { saveNumbersToAccount, saveNumbersToDevice } from "@/entities/saved-number/api";
import {
  MAX_COUNT,
  MAX_LOCKED_NUMBERS,
  MIN_COUNT,
  type RecommendNumbers,
  type Strategy,
} from "@/entities/recommendation/schema";
import { ApiError, toApiError } from "@/shared/api/error";

/**
 * 추천 스튜디오 상태 — improvement_fe.md §5.2
 *
 * 규칙 세 가지가 이 훅의 존재 이유다.
 *
 * 1. **마운트 시 자동 요청 금지**(레거시 F-P0-6/7). 화면에 들어오는 것만으로 POST가
 *    나가면 사용자가 원하지 않은 추천이 이력에 쌓이고, 크롤러 방문마다 서버가 조합을
 *    생성한다. 그래서 초기 상태는 idle이고 generate()는 오직 클릭으로만 불린다.
 * 2. **경쟁 응답 폐기.** 사용자가 버튼을 연타하면 응답이 순서 없이 도착한다. 시퀀스
 *    번호가 최신이 아닌 응답은 버린다 — 안 그러면 방금 요청한 것과 다른 결과가 남는다.
 * 3. **로그인 상태면 계정으로 즉시 저장**(레거시 C-2). claim 완료를 기다리지 않는다.
 */

export type NumberMark = "none" | "locked" | "excluded";

export type StudioState =
  | { status: "idle" }
  | { status: "generating" }
  | { status: "ready"; result: RecommendNumbers }
  | { status: "error"; error: ApiError };

export type SaveOutcome =
  { kind: "saved" } | { kind: "duplicate" } | { kind: "failed"; message: string };

export function useRecommendStudio({ loggedIn }: { loggedIn: boolean }) {
  const [strategy, setStrategy] = useState<Strategy>("balanced");
  const [count, setCount] = useState(5);
  const [marks, setMarks] = useState<Map<number, NumberMark>>(new Map());
  const [state, setState] = useState<StudioState>({ status: "idle" });
  const [saveOutcomes, setSaveOutcomes] = useState<Map<number, SaveOutcome>>(new Map());

  // 최신 요청 번호. 응답이 도착했을 때 이 값과 다르면 낡은 응답이다.
  const sequenceRef = useRef(0);

  const lockedNumbers = [...marks.entries()]
    .filter(([, mark]) => mark === "locked")
    .map(([value]) => value)
    .sort((a, b) => a - b);

  const excludedNumbers = [...marks.entries()]
    .filter(([, mark]) => mark === "excluded")
    .map(([value]) => value)
    .sort((a, b) => a - b);

  /** 3단 토글: none → locked → excluded → none (§3.2) */
  const toggleNumber = useCallback(
    (value: number) => {
      setMarks((current) => {
        const next = new Map(current);
        const mark = next.get(value) ?? "none";

        if (mark === "none") {
          // 고정 상한을 넘기면 조용히 무시하지 않고 아무 일도 하지 않는다 —
          // 화면이 상한을 함께 표시하므로 사용자가 이유를 알 수 있다.
          if (lockedNumbers.length >= MAX_LOCKED_NUMBERS) return current;
          next.set(value, "locked");
        } else if (mark === "locked") {
          next.set(value, "excluded");
        } else {
          next.delete(value);
        }
        return next;
      });
    },
    [lockedNumbers.length],
  );

  const clearMarks = useCallback(() => setMarks(new Map()), []);

  const setCountSafely = useCallback((next: number) => {
    setCount(Math.min(Math.max(next, MIN_COUNT), MAX_COUNT));
  }, []);

  /** 오직 사용자 클릭으로만 호출한다. 이펙트에서 부르지 않는다(F-P0-6/7). */
  const generate = useCallback(async () => {
    sequenceRef.current += 1;
    const sequence = sequenceRef.current;
    setState({ status: "generating" });
    setSaveOutcomes(new Map());

    try {
      const result = await recommendNumbers({ strategy, count, lockedNumbers, excludedNumbers });
      if (sequence !== sequenceRef.current) return; // 낡은 응답 폐기
      setState({ status: "ready", result });
    } catch (cause) {
      if (sequence !== sequenceRef.current) return;
      setState({ status: "error", error: toApiError(cause, "조합을 만들지 못했습니다.") });
    }
  }, [strategy, count, lockedNumbers, excludedNumbers]);

  const save = useCallback(
    async (index: number, numbers: readonly number[]) => {
      try {
        // 로그인 상태면 claim 결과와 무관하게 계정으로 보낸다(C-2).
        const result = loggedIn
          ? await saveNumbersToAccount(numbers)
          : await saveNumbersToDevice(numbers);

        /**
         * 중복은 **오류가 아니라 응답 필드**다. 백엔드는 이미 저장된 조합이면 200 +
         * created:false를, 새로 저장했으면 201 + created:true를 준다(SavedNumbersController).
         * 409를 기다리면 중복이 영영 "저장했습니다"로 표시된다.
         */
        setSaveOutcomes((current) =>
          new Map(current).set(index, result.created ? { kind: "saved" } : { kind: "duplicate" }),
        );
      } catch (cause) {
        const error = toApiError(cause, "저장하지 못했습니다.");
        setSaveOutcomes((current) =>
          new Map(current).set(index, { kind: "failed", message: error.message }),
        );
      }
    },
    [loggedIn],
  );

  return {
    strategy,
    setStrategy,
    count,
    setCount: setCountSafely,
    marks,
    lockedNumbers,
    excludedNumbers,
    toggleNumber,
    clearMarks,
    state,
    generate,
    save,
    saveOutcomes,
  };
}
