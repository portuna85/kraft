"use client";

import { useState } from "react";

import { collectLatest, collectRound } from "@/entities/ops/api";
import type { WinningNumberResult } from "@/entities/ops/schema";
import { ApiError } from "@/shared/api/error";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/field";

import type { LoadingAction } from "./ops-console-types";
import styles from "./ops-console.module.css";

type CollectionPanelProps = {
  token: string;
  tokenReady: boolean;
  loadingAction: LoadingAction;
  onStart: (action: LoadingAction) => void;
  onSuccess: (action: "collect-latest" | "collect-round", result: WinningNumberResult) => void;
  onError: (message: string) => void;
};

/**
 * 수집 실행 패널 ③.
 *
 * 지정 회차 입력은 정수·1 이상이 아니면 **요청 자체를 보내지 않는다** — 무효한
 * 회차로 백엔드를 때리지 않는다(legacy와 같은 이유).
 */
export function CollectionPanel({
  token,
  tokenReady,
  loadingAction,
  onStart,
  onSuccess,
  onError,
}: CollectionPanelProps) {
  const [roundInput, setRoundInput] = useState("");
  const [roundError, setRoundError] = useState<string | null>(null);

  const locked = loadingAction !== null || !tokenReady;

  async function runCollectLatest() {
    onStart("collect-latest");
    try {
      const result = await collectLatest(token);
      onSuccess("collect-latest", result);
    } catch (cause) {
      onError(cause instanceof ApiError ? cause.message : "최신 회차 반영에 실패했습니다.");
    }
  }

  async function runCollectRound() {
    const parsed = Number(roundInput);
    if (!Number.isInteger(parsed) || parsed < 1) {
      setRoundError("1 이상의 정수를 입력해 주세요.");
      return;
    }
    setRoundError(null);

    onStart("collect-round");
    try {
      const result = await collectRound(token, parsed);
      onSuccess("collect-round", result);
    } catch (cause) {
      onError(cause instanceof ApiError ? cause.message : "지정 회차 반영에 실패했습니다.");
    }
  }

  return (
    <div className={styles.actions}>
      {loadingAction === "collect-latest" ? (
        <Button loading loadingLabel="수집 중" disabled={locked}>
          최신 회차 반영
        </Button>
      ) : (
        <Button onClick={() => void runCollectLatest()} disabled={locked}>
          최신 회차 반영
        </Button>
      )}

      <div className={styles.roundRow}>
        <TextField
          label="지정 회차"
          name="collectRound"
          type="number"
          min={1}
          value={roundInput}
          onChange={(event) => setRoundInput(event.target.value)}
          error={roundError}
          disabled={locked}
        />
        {loadingAction === "collect-round" ? (
          <Button loading loadingLabel="수집 중" disabled={locked}>
            지정 회차 반영
          </Button>
        ) : (
          <Button onClick={() => void runCollectRound()} disabled={locked}>
            지정 회차 반영
          </Button>
        )}
      </div>
    </div>
  );
}
