"use client";

import { useState } from "react";

import { getOpsSummary } from "@/entities/ops/api";
import type { OpsSummary, WinningNumberResult } from "@/entities/ops/schema";
import { ApiError } from "@/shared/api/error";
import { formatPrize } from "@/shared/lib/format";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/field";
import { InlineAlert } from "@/shared/ui/states";
import { Card } from "@/shared/ui/surface";

import { CollectionPanel } from "./collection-panel";
import { ManualEntryForm } from "./manual-entry-form";
import type { LoadingAction } from "./ops-console-types";
import styles from "./ops-console.module.css";
import { SummaryPanel } from "./summary-panel";

type Message = { tone: "success" | "danger"; text: string };

function resultText(result: WinningNumberResult): string {
  return `${result.round}회 반영 완료 (1등 ${formatPrize(result.firstPrizeAmount)}).`;
}

/**
 * 운영 콘솔 — improvement_fe.md §23.14.
 *
 * 토큰은 서버에 저장하지 않는다. 이 컴포넌트의 state가 유일한 보관 장소이고,
 * 새로고침하면 사라진다 — 세션마다 운영자가 다시 입력하는 legacy 방식을 그대로 따른다.
 */
export function OpsConsole() {
  const [token, setToken] = useState("");
  const [summary, setSummary] = useState<OpsSummary | null>(null);
  const [loadingAction, setLoadingAction] = useState<LoadingAction>(null);
  const [message, setMessage] = useState<Message | null>(null);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  const tokenReady = token.trim().length > 0;

  async function checkStatus() {
    setLoadingAction("summary");
    setMessage(null);
    try {
      const result = await getOpsSummary(token);
      setSummary(result);
      setRefreshError(null);
    } catch (cause) {
      setMessage({
        tone: "danger",
        text: cause instanceof ApiError ? cause.message : "상태를 확인하지 못했습니다.",
      });
    } finally {
      setLoadingAction(null);
    }
  }

  async function refreshAfterSuccess(successText: string) {
    setMessage({ tone: "success", text: successText });
    setLoadingAction(null);
    try {
      const result = await getOpsSummary(token);
      setSummary(result);
      setRefreshError(null);
    } catch {
      // 성공 메시지는 지우지 않는다(legacy FE-077) — 갱신 실패만 별도로 알린다.
      setRefreshError("방금 반영됐지만 요약을 다시 불러오지 못했습니다. 상태 확인을 다시 눌러 주세요.");
    }
  }

  return (
    <div className="stack">
      <InlineAlert tone="warning" title="운영자 전용">
        이 화면은 운영자 전용입니다. 여기서 수행한 작업은 실제 서비스 데이터에 즉시 반영됩니다.
      </InlineAlert>

      <Card>
        <div className={`stack ${styles.tokenRow}`}>
          <TextField
            label="운영 토큰"
            name="ops-token"
            type="password"
            autoComplete="off"
            value={token}
            onChange={(event) => setToken(event.target.value)}
          />
          {loadingAction === "summary" ? (
            <Button loading loadingLabel="확인 중">
              운영 상태 확인
            </Button>
          ) : (
            <Button onClick={() => void checkStatus()} disabled={!tokenReady}>
              운영 상태 확인
            </Button>
          )}
        </div>
      </Card>

      {message !== null && <InlineAlert tone={message.tone}>{message.text}</InlineAlert>}
      {refreshError !== null && <InlineAlert tone="danger">{refreshError}</InlineAlert>}

      <Card>
        <div className="stack">
          <h2>상태 요약</h2>
          <SummaryPanel summary={summary} />
        </div>
      </Card>

      <Card>
        <div className="stack">
          <h2>수집 실행</h2>
          <CollectionPanel
            token={token}
            tokenReady={tokenReady}
            loadingAction={loadingAction}
            onStart={setLoadingAction}
            onSuccess={(_action, result) => void refreshAfterSuccess(resultText(result))}
            onError={(text) => {
              setMessage({ tone: "danger", text });
              setLoadingAction(null);
            }}
          />
        </div>
      </Card>

      <Card>
        <div className="stack">
          <h2>수동 적재</h2>
          <ManualEntryForm
            token={token}
            onStart={() => setLoadingAction("upsert")}
            onSuccess={(result) => void refreshAfterSuccess(resultText(result))}
            onError={(text) => {
              setMessage({ tone: "danger", text });
              setLoadingAction(null);
            }}
          />
        </div>
      </Card>
    </div>
  );
}
