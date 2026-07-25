"use client";

import { useState } from "react";
import { LottoBalls } from "@/components/lotto-balls";
import { formatCurrency, formatDateTime, formatDrawDate } from "@/lib/format";

type OpsSummary = {
  service: string;
  timezone: string;
  status: string;
  latestRound: number | null;
  latestDrawDate: string | null;
  checkedAt: string;
  fresh: boolean;
};

type WinningNumber = {
  round: number;
  drawDate: string;
  numbers: number[];
  bonusNumber: number;
  firstPrizeAmount: number;
};

type ManualEntryForm = {
  round: string;
  drawDate: string;
  numbers: string;
  bonusNumber: string;
  firstPrizeAmount: string;
};

const initialManualEntryForm: ManualEntryForm = {
  round: "",
  drawDate: "",
  numbers: "",
  bonusNumber: "",
  firstPrizeAmount: ""
};

type OpsResult<T> = { ok: true; data: T } | { ok: false; message: string };

async function callOps<T>(url: string, init?: RequestInit): Promise<OpsResult<T>> {
  try {
    const response = await fetch(url, { cache: "no-store", ...init });
    const json = await response.json() as T | { message?: string };
    if (!response.ok) {
      return { ok: false, message: (json as { message?: string }).message ?? "요청에 실패했습니다." };
    }
    return { ok: true, data: json as T };
  } catch {
    return { ok: false, message: "네트워크 오류가 발생했습니다." };
  }
}

function parseNumbersInput(value: string): number[] {
  return value
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => !Number.isNaN(item));
}

function SummaryPanel({ summary }: { summary: OpsSummary }) {
  return (
    <article className="panel">
      <p className="eyebrow">운영 상태</p>
      <h2 className="ops-title">{summary.service}</h2>
      <div className="ops-summary-grid">
        <div className="ops-summary-card">
          <strong>서비스 상태</strong>
          <span>{summary.status}</span>
        </div>
        <div className="ops-summary-card">
          <strong>시간대</strong>
          <span>{summary.timezone}</span>
        </div>
        <div className="ops-summary-card">
          <strong>최신 저장 회차</strong>
          <span>{summary.latestRound === null ? "없음" : `${summary.latestRound}회`}</span>
        </div>
        <div className="ops-summary-card">
          <strong>최신 추첨일</strong>
          <span>{summary.latestDrawDate ? formatDrawDate(summary.latestDrawDate) : "없음"}</span>
        </div>
        <div className="ops-summary-card">
          <strong>신선도</strong>
          <span>{summary.fresh ? "최신 상태" : "점검 필요"}</span>
        </div>
        <div className="ops-summary-card">
          <strong>확인 시각</strong>
          <span>{formatDateTime(summary.checkedAt)}</span>
        </div>
      </div>
    </article>
  );
}

function CollectedPanel({ result }: { result: WinningNumber }) {
  return (
    <article className="panel">
      <p className="eyebrow">최근 반영 결과</p>
      <h2 className="ops-title">{result.round}회차</h2>
      <p className="page-subtitle">{formatDrawDate(result.drawDate)} 기준 반영 데이터</p>
      <LottoBalls numbers={result.numbers} bonusNumber={result.bonusNumber} />
      <p className="muted prize-line">1등 당첨금 {formatCurrency(result.firstPrizeAmount)}</p>
    </article>
  );
}

export function OpsDashboardClient() {
  const [token, setToken] = useState("");
  const [round, setRound] = useState("");
  const [manualEntry, setManualEntry] = useState<ManualEntryForm>(initialManualEntryForm);
  const [summary, setSummary] = useState<OpsSummary | null>(null);
  const [lastCollected, setLastCollected] = useState<WinningNumber | null>(null);
  const [message, setMessage] = useState("");
  const [loadingAction, setLoadingAction] = useState<"summary" | "latest" | "round" | "manual" | null>(null);

  const opsHeaders = { "X-Ops-Token": token };

  async function refreshSummary() {
    const result = await callOps<OpsSummary>("/ops-api/summary", { headers: opsHeaders });
    if (result.ok) setSummary(result.data);
  }

  async function loadSummary() {
    setLoadingAction("summary");
    setMessage("");
    const result = await callOps<OpsSummary>("/ops-api/summary", { headers: opsHeaders });
    setLoadingAction(null);
    if (!result.ok) { setMessage(result.message); return; }
    setSummary(result.data);
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

  async function submitManualEntry(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const body = {
      round: Number(manualEntry.round),
      drawDate: manualEntry.drawDate,
      numbers: parseNumbersInput(manualEntry.numbers),
      bonusNumber: Number(manualEntry.bonusNumber),
      firstPrizeAmount: Number(manualEntry.firstPrizeAmount)
    };
    setLoadingAction("manual");
    setMessage("");
    const result = await callOps<WinningNumber>("/ops-api/rounds", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...opsHeaders },
      body: JSON.stringify(body)
    });
    setLoadingAction(null);
    if (!result.ok) { setMessage(result.message); return; }
    setLastCollected(result.data);
    setManualEntry(initialManualEntryForm);
    setMessage(`${result.data.round}회차 데이터를 수동으로 저장했습니다.`);
    await refreshSummary();
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
              value={token}
              onChange={(event) => setToken(event.target.value)}
              placeholder="X-Ops-Token 값을 입력하세요"
            />
          </label>
          <button type="button" onClick={loadSummary} disabled={!token || loadingAction !== null}>
            {loadingAction === "summary" ? "조회 중..." : "운영 상태 확인"}
          </button>
        </div>
      </article>

      <article className="panel">
        <p className="eyebrow">수집 작업</p>
        <h2 className="ops-title">회차 수집 실행</h2>
        <div className="ops-actions">
          <button type="button" onClick={collectLatest} disabled={!token || loadingAction !== null}>
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
            <button type="button" onClick={collectRound} disabled={!token || loadingAction !== null}>
              {loadingAction === "round" ? "수집 중..." : "지정 회차 반영"}
            </button>
          </div>
        </div>
      </article>

      <article className="panel">
        <p className="eyebrow">수동 적재</p>
        <h2 className="ops-title">회차 직접 입력</h2>
        <p className="page-subtitle">외부 수집 실패나 데이터 보정이 필요한 경우 회차 정보를 직접 등록합니다.</p>
        <form className="ops-manual-form" onSubmit={submitManualEntry}>
          <label className="ops-field">
            <span>회차</span>
            <input
              type="number"
              inputMode="numeric"
              min="1"
              value={manualEntry.round}
              onChange={(event) => setManualEntry((current) => ({ ...current, round: event.target.value }))}
              placeholder="예: 1201"
            />
          </label>
          <label className="ops-field">
            <span>추첨일</span>
            <input
              type="date"
              value={manualEntry.drawDate}
              onChange={(event) => setManualEntry((current) => ({ ...current, drawDate: event.target.value }))}
            />
          </label>
          <label className="ops-field">
            <span>당첨 번호 6개</span>
            <input
              value={manualEntry.numbers}
              onChange={(event) => setManualEntry((current) => ({ ...current, numbers: event.target.value }))}
              placeholder="예: 3, 11, 19, 28, 34, 42"
            />
          </label>
          <label className="ops-field">
            <span>보너스 번호</span>
            <input
              type="number"
              inputMode="numeric"
              min="1"
              max="45"
              value={manualEntry.bonusNumber}
              onChange={(event) => setManualEntry((current) => ({ ...current, bonusNumber: event.target.value }))}
              placeholder="예: 7"
            />
          </label>
          <label className="ops-field">
            <span>1등 당첨금</span>
            <input
              type="number"
              inputMode="numeric"
              min="0"
              value={manualEntry.firstPrizeAmount}
              onChange={(event) => setManualEntry((current) => ({ ...current, firstPrizeAmount: event.target.value }))}
              placeholder="예: 2100000000"
            />
          </label>
          <button type="submit" disabled={!token || loadingAction !== null}>
            {loadingAction === "manual" ? "저장 중..." : "수동 등록"}
          </button>
        </form>
      </article>

      {message ? <p className="status-text ops-status" role="status" aria-live="polite">{message}</p> : null}

      {summary ? <SummaryPanel summary={summary} /> : null}

      {lastCollected ? <CollectedPanel result={lastCollected} /> : null}
    </section>
  );
}
