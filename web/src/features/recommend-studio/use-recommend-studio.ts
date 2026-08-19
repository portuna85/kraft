"use client";

import { useCallback, useMemo, useRef, useState } from "react";

import { recommendNumbers, recommendNumbersForAccount } from "@/entities/recommendation/api";
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
 * 추천 스튜디오 상태
 *
 * 규칙 세 가지가 이 훅의 존재 이유다.
 *
 * 1. **마운트 시 자동 요청 금지**(레거시 F-P0-6/7). 화면에 들어오는 것만으로 POST가
 *    나가면 사용자가 원하지 않은 추천이 이력에 쌓이고, 크롤러 방문마다 서버가 조합을
 *    생성한다. 그래서 초기 상태는 idle이고 generate()는 오직 클릭으로만 불린다.
 * 2. **경쟁 응답 폐기.** 사용자가 버튼을 연타하면 응답이 순서 없이 도착한다. 시퀀스
 *    번호가 최신이 아닌 응답은 버린다 — 안 그러면 방금 요청한 것과 다른 결과가 남는다.
 * 3. **로그인 상태면 계정으로 즉시 저장**(레거시 C-2). claim 완료를 기다리지 않는다.
 * 4. **세션 준비 전에는 발사하지 않는다**(KF-09, docs/improvement.md). `sessionReady`가
 *    false인 동안 `generate()`/`save()`는 조용히 아무것도 안 한다 — 호출부
 *    (`recommend-studio.tsx`)가 이 구간에는 버튼을 비활성화해야 하므로 이 가드는
 *    이중 안전장치다. 세션이 아직 로그인 여부를 모르는 채로 생성이 발사되면 CSRF
 *    쿠키가 없어 무관한 오류가 뜨거나, 최악의 경우 로그인 사용자의 생성이 device
 *    스코프로 잘못 만들어질 수 있다.
 */

export type NumberMark = "none" | "locked" | "excluded";

export type StudioState =
  | { status: "idle" }
  | { status: "generating" }
  | { status: "ready"; result: RecommendNumbers }
  | { status: "error"; error: ApiError };

export type SaveOutcome =
  { kind: "saved" } | { kind: "duplicate" } | { kind: "failed"; message: string };

export type SelectionMode = "locked" | "excluded";

export function useRecommendStudio({
  loggedIn,
  sessionReady,
}: {
  loggedIn: boolean;
  sessionReady: boolean;
}) {
  const [strategy, setStrategy] = useState<Strategy>("balanced");
  const [count, setCount] = useState(5);
  const [selectionMode, setSelectionMode] = useState<SelectionMode>("locked");
  const [marks, setMarks] = useState<Map<number, NumberMark>>(new Map());
  const [state, setState] = useState<StudioState>({ status: "idle" });
  const [saveOutcomes, setSaveOutcomes] = useState<Map<number, SaveOutcome>>(new Map());
  // I-20: 범위 밖 입력이 조용히 잘려나가면(99→10, 0→1) 사용자가 왜 값이 바뀌었는지
  // 모른다 — 클램프가 실제로 일어났을 때만 안내 문구를 보여준다.
  const [countClamped, setCountClamped] = useState(false);
  // I-27: 고정 5/5에서 6번째를 눌러도 시각적으로도 보조기기로도 아무 신호가 없었다 —
  // 마지막으로 거부된 번호를 기억해 live region에 안내한다.
  const [rejectedNumber, setRejectedNumber] = useState<number | null>(null);

  // 최신 요청 번호. 응답이 도착했을 때 이 값과 다르면 낡은 응답이다.
  const sequenceRef = useRef(0);
  // I-28: 결과가 나온 뒤 고정/제외를 바꿔도 결과는 그대로라 "결과가 이 조건으로
  // 만들어졌다"는 착각을 준다 — 생성 성공 시점의 조건을 스냅샷으로 남겨 지금
  // 조건과 다르면 낡은 결과로 취급한다. 렌더 중에는 ref를 읽지 않도록 state로 둔다.
  const [resultMarksKey, setResultMarksKey] = useState<string | null>(null);

  // KF-26(FE-OPT-41, docs/improvement.md): useMemo 없이 매 렌더 새 배열을
  // 만들면 그 정체성이 아래 generate의 의존성이라 매 렌더 재생성되고,
  // NumberGrid의 45개 버튼 props가 계속 바뀐다 — marks가 실제로 바뀔 때만
  // 다시 계산한다.
  const lockedNumbers = useMemo(
    () =>
      [...marks.entries()]
        .filter(([, mark]) => mark === "locked")
        .map(([value]) => value)
        .sort((a, b) => a - b),
    [marks],
  );

  const excludedNumbers = useMemo(
    () =>
      [...marks.entries()]
        .filter(([, mark]) => mark === "excluded")
        .map(([value]) => value)
        .sort((a, b) => a - b),
    [marks],
  );

  const marksKey = `${lockedNumbers.join(",")}|${excludedNumbers.join(",")}`;
  const isStale = state.status === "ready" && resultMarksKey !== marksKey;

  /**
   * 모드 기반 선택
   *
   * 3단 순환 클릭(none→locked→excluded→none) 대신, 상단 세그먼트 컨트롤로 먼저
   * 모드를 고르고 번호는 그 모드로만 선택/해제한다. 반대 모드에 이미 있는
   * 번호를 누르면 반대 모드에서 빠지고 현재 모드로 옮겨간다 — `marks`가 번호당
   * 값 하나만 갖는 Map이라 `.set()` 한 번으로 "제거 후 이동"이 함께 일어난다.
   */
  const toggleNumber = useCallback(
    (value: number) => {
      const mark = marks.get(value) ?? "none";

      if (
        mark !== selectionMode &&
        selectionMode === "locked" &&
        lockedNumbers.length >= MAX_LOCKED_NUMBERS
      ) {
        // I-27: 화면 요약(고정 X/5)은 있었지만 정적 텍스트라 값이 안 바뀌면 live
        // region이 재공지하지 않는다 — 거부된 번호 자체를 상태로 남겨 매 거부마다
        // 문구가 바뀌게 한다.
        setRejectedNumber(value);
        return;
      }

      setRejectedNumber(null);
      setMarks((current) => {
        const next = new Map(current);
        if (mark === selectionMode) {
          next.delete(value); // 같은 모드에서 다시 누르면 해제
        } else {
          next.set(value, selectionMode);
        }
        return next;
      });
    },
    [selectionMode, lockedNumbers.length, marks],
  );

  const clearMarks = useCallback(() => setMarks(new Map()), []);

  const setCountSafely = useCallback((next: number) => {
    const clamped = Math.min(Math.max(next, MIN_COUNT), MAX_COUNT);
    setCount(clamped);
    setCountClamped(clamped !== next);
  }, []);

  /** 오직 사용자 클릭으로만 호출한다. 이펙트에서 부르지 않는다(F-P0-6/7). */
  const generate = useCallback(async () => {
    if (!sessionReady) return; // KF-09: 세션이 아직 미확정이면 발사하지 않는다.
    sequenceRef.current += 1;
    const sequence = sequenceRef.current;
    setState({ status: "generating" });
    setSaveOutcomes(new Map());

    try {
      // KF-01: 로그인 상태면 계정 소유로 직접 생성한다 — device 스코프에 만들었다가
      // 나중에 claim으로 옮기는 경로에 기대지 않는다.
      const result = loggedIn
        ? await recommendNumbersForAccount({ strategy, count, lockedNumbers, excludedNumbers })
        : await recommendNumbers({ strategy, count, lockedNumbers, excludedNumbers });
      if (sequence !== sequenceRef.current) return; // 낡은 응답 폐기
      setResultMarksKey(marksKey);
      setState({ status: "ready", result });
    } catch (cause) {
      if (sequence !== sequenceRef.current) return;
      setState({ status: "error", error: toApiError(cause, "조합을 만들지 못했습니다.") });
    }
  }, [strategy, count, lockedNumbers, excludedNumbers, marksKey, loggedIn, sessionReady]);

  const save = useCallback(
    async (index: number, numbers: readonly number[]) => {
      if (!sessionReady) return; // KF-09: 세션이 아직 미확정이면 발사하지 않는다.
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
    [loggedIn, sessionReady],
  );

  return {
    strategy,
    setStrategy,
    count,
    setCount: setCountSafely,
    countClamped,
    selectionMode,
    setSelectionMode,
    marks,
    lockedNumbers,
    excludedNumbers,
    toggleNumber,
    clearMarks,
    rejectedNumber,
    isStale,
    state,
    generate,
    save,
    saveOutcomes,
  };
}
