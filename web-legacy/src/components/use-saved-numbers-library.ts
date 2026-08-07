import { useCallback, useEffect, useRef, useState } from "react";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import { useCommunitySession } from "@/lib/community-session-provider";
import { deleteMySavedNumber, getMySavedNumberMatches, getMySavedNumbers } from "@/lib/community-client";

export type SavedNumber = {
  id: number;
  numbers: number[];
  label: string | null;
  source: string;
  createdAt: string;
};

export type SavedNumberMatchResult = {
  savedNumber: SavedNumber;
  round: number;
  drawDate: string;
  drawNumbers: number[];
  bonusNumber: number;
  matchedCount: number;
  bonusMatch: boolean;
  prizeTier: string;
};

export type MatchState = "idle" | "loading" | "success" | "error";

function isSavedNumber(body: unknown): body is SavedNumber {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return typeof b.id === "number" && Array.isArray(b.numbers);
}

function isSavedNumberArray(body: unknown): body is SavedNumber[] {
  return Array.isArray(body) && body.every(isSavedNumber);
}

function isSavedNumberMatchResultArray(body: unknown): body is SavedNumberMatchResult[] {
  return (
    Array.isArray(body) &&
    body.every((item) => {
      if (typeof item !== "object" || item === null) return false;
      const i = item as Record<string, unknown>;
      return (
        isSavedNumber(i.savedNumber) &&
        typeof i.round === "number" &&
        typeof i.matchedCount === "number" &&
        typeof i.prizeTier === "string"
      );
    })
  );
}

/**
 * 저장 번호 목록/회차 대조/삭제 상태를 들고 있는 훅. 로그인 여부에 따라 계정(소유자)
 * 스코프 API 또는 기기(디바이스 토큰) 스코프 API 중 하나로 읽고 쓴다.
 */
export function useSavedNumbersLibrary(latestRound: number) {
  const { session, claimStatus } = useCommunitySession();
  // C-2: 로그인 상태면 계정(소유자) 스코프로 읽고 쓴다 — claim이 진행 중일 때는 아직
  // 기다린다(귀속이 옮기는 도중의 데이터를 읽지 않기 위해). claimStatus가 settled/error로
  // 넘어가면(비로그인도 이 훅에서는 항상 settled·idle) 아래 effect가 다시 읽어온다.
  const isClaimSettling = claimStatus === "claiming";
  const useOwnerScope = Boolean(session?.loggedIn) && !isClaimSettling;

  const [items, setItems] = useState<SavedNumber[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [message, setMessage] = useState("");
  const [selectedRound, setSelectedRound] = useState<string>("latest");
  const [customRoundInput, setCustomRoundInput] = useState("");
  const [matchMap, setMatchMap] = useState<Map<number, SavedNumberMatchResult>>(new Map());
  const [matchState, setMatchState] = useState<MatchState>("idle");
  const matchFetchSeqRef = useRef(0);
  // FE-003: 삭제 확인 대상. null이면 다이얼로그가 닫힌 상태다.
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const loadSavedNumbers = useCallback(() => {
    if (isClaimSettling) {
      // 귀속이 진행 중이면 어느 쪽 목록을 보여줘도 곧 stale해진다 — claimStatus가
      // settled로 바뀌는 순간 이 effect가 다시 실행되어 최종 상태를 가져온다.
      return;
    }
    const request = useOwnerScope
      ? getMySavedNumbers()
      : browserFetch<SavedNumber[]>("/api/v1/saved", { includeDeviceToken: true }, isSavedNumberArray);
    request
      .then((savedItems) => {
        setItems(savedItems);
        setHasError(false);
      })
      .catch(() => {
        setHasError(true);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [isClaimSettling, useOwnerScope]);

  useEffect(() => {
    loadSavedNumbers();
  }, [loadSavedNumbers]);

  function retrySavedNumbers() {
    setIsLoading(true);
    setHasError(false);
    loadSavedNumbers();
  }

  const fetchMatches = useCallback(() => {
    if (items.length === 0 || isClaimSettling) {
      return;
    }

    const seq = ++matchFetchSeqRef.current;

    const request = useOwnerScope
      ? getMySavedNumberMatches(selectedRound)
      : browserFetch<SavedNumberMatchResult[]>(
          `/api/v1/saved/matches?round=${encodeURIComponent(selectedRound)}`,
          { includeDeviceToken: true },
          isSavedNumberMatchResultArray,
        );
    request
      .then((results) => {
        if (seq !== matchFetchSeqRef.current) return;
        const map = new Map<number, SavedNumberMatchResult>();
        for (const result of results) {
          map.set(result.savedNumber.id, result);
        }
        setMatchMap(map);
        setMatchState("success");
      })
      .catch(() => {
        if (seq !== matchFetchSeqRef.current) return;
        // 실패해도 이전에 성공한 matchMap은 그대로 유지 — "불러오기 실패"가
        // "대조 결과 없음"처럼 보이지 않게 한다.
        setMatchState("error");
      });
  }, [items, selectedRound, isClaimSettling, useOwnerScope]);

  useEffect(() => {
    fetchMatches();
  }, [fetchMatches]);

  // FE-003: 확인 방식을 다이얼로그로 통일하면서 5초 유예 삭제를 걷어냈다. 유예 방식은
  // 타이머·대기 상태·이탈 시 flush(FE-045)까지 따라붙는데, 확인이 선행되면 그 복잡도가
  // 통째로 사라진다. 삭제는 확인 직후 한 번만 일어난다.
  async function confirmDelete() {
    if (pendingDeleteId === null) return;
    const id = pendingDeleteId;
    setDeleting(true);
    setDeleteError(null);
    try {
      if (useOwnerScope) {
        await deleteMySavedNumber(id);
      } else {
        await browserFetch(`/api/v1/saved/${id}`, {
          method: "DELETE",
          includeDeviceToken: true,
        });
      }
      setItems((prev) => prev.filter((x) => x.id !== id));
      setPendingDeleteId(null);
      setMessage("저장 번호를 삭제했습니다.");
    } catch (err) {
      // 삭제 실패 — 항목은 그대로 두고 다이얼로그 안에서 이유를 알린다.
      if (!(err instanceof BrowserApiError || err instanceof Error)) throw err;
      setDeleteError("저장 번호를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeleting(false);
    }
  }

  // 회차 변경 즉시(사용자 조작 시점에) 이전 회차의 대조 결과를 비우고 로딩 상태로
  // 전환한다(P1-06) — 그러지 않으면 새 회차를 fetch하는 동안 이전 회차의 당첨 배지가
  // 그대로 보여서 사용자가 새 회차 결과로 오인할 수 있다.
  function changeSelectedRound(round: string) {
    setMatchMap(new Map());
    setMatchState("loading");
    setSelectedRound(round);
  }

  function applyCustomRound(event: React.FormEvent) {
    event.preventDefault();
    const round = Number.parseInt(customRoundInput.trim(), 10);
    if (Number.isNaN(round) || round < 1 || round > latestRound) {
      return;
    }
    changeSelectedRound(String(round));
    setCustomRoundInput("");
  }

  return {
    items,
    isLoading,
    hasError,
    message,
    selectedRound,
    customRoundInput,
    setCustomRoundInput,
    matchMap,
    matchState,
    pendingDeleteId,
    setPendingDeleteId,
    deleting,
    deleteError,
    setDeleteError,
    retrySavedNumbers,
    fetchMatches,
    confirmDelete,
    changeSelectedRound,
    applyCustomRound,
  };
}
