"use client";

import { useState } from "react";
import { ErrorState } from "@/ui/primitives/error-state";
import { callOps } from "@/lib/ops-client";
import type { OpsSummary, WinningNumber, WinningNumberUpsertBody } from "@/lib/ops-types";
import { ManualEntryFormPanel } from "@/components/ops/manual-entry-form";
import { CollectedPanel, SummaryPanel } from "@/components/ops/result-panels";

/**
 * FE-078: 전송(ops-client)·검증(ops-manual-entry)·수동 적재 폼·표시 패널을 각각
 * 분리하고, 여기서는 토큰과 작업 진행 상태만 조율한다.
 */
export function OpsDashboardClient() {
  const [token, setToken] = useState("");
  const [round, setRound] = useState("");
  const [summary, setSummary] = useState<OpsSummary | null>(null);
  const [lastCollected, setLastCollected] = useState<WinningNumber | null>(null);
  const [message, setMessage] = useState("");
  const [loadingAction, setLoadingAction] = useState<"summary" | "latest" | "round" | "manual" | null>(null);
  const [summaryStale, setSummaryStale] = useState(false);

  const opsHeaders = { "X-Ops-Token": token };
  const busy = !token || loadingAction !== null;

  // FE-077: 작업 성공 후 자동 갱신인데 실패해도 else가 없어 아무 표시가 없었다.
  // 그러면 화면에 남은 요약이 옛 값인데도 사용자는 최신 상태를 보고 있다고 오인한다.
  // 작업 자체는 성공했으므로 그 메시지를 덮지 않고 뒤에 덧붙인다.
  async function refreshSummary() {
    const result = await callOps<OpsSummary>("/ops-api/summary", { headers: opsHeaders });
    if (result.ok) {
      setSummary(result.data);
      setSummaryStale(false);
      return;
    }
    setSummaryStale(true);
  }

  async function loadSummary() {
    setLoadingAction("summary");
    setMessage("");
    const result = await callOps<OpsSummary>("/ops-api/summary", { headers: opsHeaders });
    setLoadingAction(null);
    if (!result.ok) { setMessage(result.message); return; }
    setSummary(result.data);
    setSummaryStale(false);
    setMessage("운영 상태를 불러왔습니다.");
  }

  async function collectLatest() {
    setLoadingAction("latest");
    setMessage("");
    const result = await callOps<WinningNumber>("/ops-api/collect/latest", { method: "POST", headers: opsHeaders });
    setLoadingAction(null);
    if (!result.ok) { setMessage(result.message); return; }
    setLastCollected(result.data);
    setMessage("최신 회차 데이터를 반영했습니다.");
    await refreshSummary();
  }

  async function collectRound() {
    const roundNumber = Number(round);
    if (!Number.isInteger(roundNumber) || roundNumber < 1) {
      setMessage("수집할 회차는 1 이상의 정수여야 합니다.");
      return;
    }
    setLoadingAction("round");
    setMessage("");
    const result = await callOps<WinningNumber>(`/ops-api/collect/${roundNumber}`, { method: "POST", headers: opsHeaders });
    setLoadingAction(null);
    if (!result.ok) { setMessage(result.message); return; }
    setLastCollected(result.data);
    setMessage(`${roundNumber}회차 데이터를 반영했습니다.`);
    await refreshSummary();
  }

  /** 폼은 검증을 통과한 본문만 넘긴다 — 여기서 원시 입력을 다시 만지지 않는다. */
  async function saveManualEntry(body: WinningNumberUpsertBody): Promise<boolean> {
    setLoadingAction("manual");
    setMessage("");
    const result = await callOps<WinningNumber>("/ops-api/rounds", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...opsHeaders },
      body: JSON.stringify(body),
    });
    setLoadingAction(null);
    if (!result.ok) { setMessage(result.message); return false; }
    setLastCollected(result.data);
    setMessage(`${result.data.round}회차 데이터를 수동으로 저장했습니다.`);
    await refreshSummary();
    return true;
  }

  return (
    <section className="ops-grid">
      <article className="panel">
        <p className="eyebrow">운영 인증</p>
        <h2 className="ops-title">Ops 토큰 입력</h2>
        <p className="page-subtitle">입력한 토큰은 현재 브라우저 세션에서만 사용되며 별도로 저장하지 않습니다.</p>
        <div className="ops-controls">
          <label className="ops-field">
            <span>운영 토큰</span>
            <input
              type="password"
              autoComplete="off"
              value={token}
              onChange={(event) => setToken(event.target.value)}
              placeholder="X-Ops-Token 값을 입력하세요"
            />
          </label>
          <button type="button" onClick={loadSummary} disabled={busy}>
            {loadingAction === "summary" ? "조회 중..." : "운영 상태 확인"}
          </button>
        </div>
      </article>

      <article className="panel">
        <p className="eyebrow">수집 작업</p>
        <h2 className="ops-title">회차 수집 실행</h2>
        <div className="ops-actions">
          <button type="button" onClick={collectLatest} disabled={busy}>
            {loadingAction === "latest" ? "수집 중..." : "최신 회차 반영"}
          </button>
          <div className="ops-inline">
            <label className="ops-field">
              <span>특정 회차</span>
              <input
                type="number"
                inputMode="numeric"
                min="1"
                value={round}
                onChange={(event) => setRound(event.target.value)}
                placeholder="예: 1201"
              />
            </label>
            <button type="button" onClick={collectRound} disabled={busy}>
              {loadingAction === "round" ? "수집 중..." : "지정 회차 반영"}
            </button>
          </div>
        </div>
      </article>

      <ManualEntryFormPanel
        disabled={busy}
        submitting={loadingAction === "manual"}
        onSubmit={saveManualEntry}
        onValidationError={setMessage}
      />

      {message ? <p className="status-text ops-status" role="status" aria-live="polite">{message}</p> : null}

      {summaryStale ? (
        <ErrorState
          variant="inline"
          title="아래 운영 상태를 갱신하지 못했습니다."
          description="작업은 처리됐지만 표시된 값은 이전 조회 결과입니다."
          retry={{ label: "다시 불러오기", onClick: loadSummary }}
        />
      ) : null}

      {summary ? <SummaryPanel summary={summary} /> : null}

      {lastCollected ? <CollectedPanel result={lastCollected} /> : null}
    </section>
  );
}
