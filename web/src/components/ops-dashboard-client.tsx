"use client";

import { useRef, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { formatCurrency, formatDateTime, formatDrawDate } from "@/lib/format";
import { validateLottoNumbers } from "@/lib/lotto-validation";

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

const MANUAL_ERROR_ID = "ops-manual-error";

type ManualEntryField = "round" | "drawDate" | "numbers" | "bonusNumber" | "firstPrizeAmount";

type ManualEntryValidation =
  | { ok: true; body: WinningNumberUpsertBody }
  | { ok: false; field: ManualEntryField; message: string };

type WinningNumberUpsertBody = {
  round: number;
  drawDate: string;
  numbers: number[];
  bonusNumber: number;
  firstPrizeAmount: number;
};

// FE-074: 이전에는 검증이 전혀 없어 빈 회차가 Number("")===0으로 전송되고, 번호를 3개만 적어도
// 요청이 나갔다. 운영 데이터를 직접 쓰는 화면이라 잘못된 값이 서버에 닿기 전에 막는다.
// 최종 판정은 여전히 서버가 한다 — 여기서는 명백히 틀린 입력만 걸러 사용자에게 이유를 알린다.
function validateManualEntry(form: ManualEntryForm): ManualEntryValidation {
  const round = Number(form.round);
  if (!form.round.trim() || !Number.isInteger(round) || round < 1) {
    return { ok: false, field: "round", message: "회차는 1 이상의 정수여야 합니다." };
  }
  if (!form.drawDate.trim()) {
    return { ok: false, field: "drawDate", message: "추첨일을 입력하세요." };
  }

  // 쉼표·공백 어느 쪽으로 구분해도 받되, 빈 토큰을 걸러낸 뒤 개수를 그대로 검증에 넘긴다.
  // (NaN을 필터링해 버리면 "3, 어, 19"가 2개로 줄어 개수 오류가 엉뚱하게 보고된다.)
  const rawNumbers = form.numbers.split(/[,\s]+/).map((item) => item.trim()).filter((item) => item.length > 0);
  const numbersResult = validateLottoNumbers(rawNumbers);
  if (!numbersResult.ok) {
    return { ok: false, field: "numbers", message: numbersResult.message };
  }

  const bonusNumber = Number(form.bonusNumber);
  if (!form.bonusNumber.trim() || !Number.isInteger(bonusNumber) || bonusNumber < 1 || bonusNumber > 45) {
    return { ok: false, field: "bonusNumber", message: "보너스 번호는 1에서 45 사이의 정수여야 합니다." };
  }
  if (numbersResult.numbers.includes(bonusNumber)) {
    return { ok: false, field: "bonusNumber", message: "보너스 번호는 당첨 번호 6개와 달라야 합니다." };
  }

  const firstPrizeAmount = Number(form.firstPrizeAmount);
  if (!form.firstPrizeAmount.trim() || !Number.isFinite(firstPrizeAmount) || firstPrizeAmount < 0) {
    return { ok: false, field: "firstPrizeAmount", message: "1등 당첨금은 0 이상의 숫자여야 합니다." };
  }

  return {
    ok: true,
    body: { round, drawDate: form.drawDate, numbers: numbersResult.numbers, bonusNumber, firstPrizeAmount },
  };
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
  const [manualError, setManualError] = useState<{ field: ManualEntryField; message: string } | null>(null);
  const manualFieldRefs = useRef<Partial<Record<ManualEntryField, HTMLInputElement | null>>>({});

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

    const validation = validateManualEntry(manualEntry);
    if (!validation.ok) {
      setManualError(validation);
      setMessage(validation.message);
      manualFieldRefs.current[validation.field]?.focus();
      return;
    }
    setManualError(null);
    const body = validation.body;

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
              autoComplete="off"
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
              required
              ref={(el) => { manualFieldRefs.current.round = el; }}
              aria-invalid={manualError?.field === "round" || undefined}
              aria-describedby={manualError?.field === "round" ? MANUAL_ERROR_ID : undefined}
              value={manualEntry.round}
              onChange={(event) => setManualEntry((current) => ({ ...current, round: event.target.value }))}
              placeholder="예: 1201"
            />
          </label>
          <label className="ops-field">
            <span>추첨일</span>
            <input
              type="date"
              required
              ref={(el) => { manualFieldRefs.current.drawDate = el; }}
              aria-invalid={manualError?.field === "drawDate" || undefined}
              aria-describedby={manualError?.field === "drawDate" ? MANUAL_ERROR_ID : undefined}
              value={manualEntry.drawDate}
              onChange={(event) => setManualEntry((current) => ({ ...current, drawDate: event.target.value }))}
            />
          </label>
          <label className="ops-field">
            <span>당첨 번호 6개</span>
            <input
              required
              ref={(el) => { manualFieldRefs.current.numbers = el; }}
              aria-invalid={manualError?.field === "numbers" || undefined}
              aria-describedby={manualError?.field === "numbers" ? MANUAL_ERROR_ID : undefined}
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
              required
              ref={(el) => { manualFieldRefs.current.bonusNumber = el; }}
              aria-invalid={manualError?.field === "bonusNumber" || undefined}
              aria-describedby={manualError?.field === "bonusNumber" ? MANUAL_ERROR_ID : undefined}
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
              required
              ref={(el) => { manualFieldRefs.current.firstPrizeAmount = el; }}
              aria-invalid={manualError?.field === "firstPrizeAmount" || undefined}
              aria-describedby={manualError?.field === "firstPrizeAmount" ? MANUAL_ERROR_ID : undefined}
              value={manualEntry.firstPrizeAmount}
              onChange={(event) => setManualEntry((current) => ({ ...current, firstPrizeAmount: event.target.value }))}
              placeholder="예: 2100000000"
            />
          </label>
          {manualError ? (
            <p id={MANUAL_ERROR_ID} className="status-text ops-status" role="alert">
              {manualError.message}
            </p>
          ) : null}
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
