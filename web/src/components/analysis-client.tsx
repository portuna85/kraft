"use client";

import { useState, type FormEvent } from "react";
import type { AnalysisResponse } from "@/lib/api";
import { AnalysisResult } from "@/components/analysis-result";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import { validateLottoNumbers } from "@/lib/lotto-validation";
import styles from "@/app/analysis/analysis.module.css";

export function AnalysisClient() {
  const [input, setInput] = useState("");
  const [result, setResult] = useState<AnalysisResponse | null>(null);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setResult(null);

    const parts = input.split(",").map((value) => value.trim());
    const validation = validateLottoNumbers(parts);

    if (!validation.ok) {
      setError(validation.message);
      return;
    }

    setPending(true);
    try {
      const analysis = await browserFetch<AnalysisResponse>("/api/v1/stats/analysis", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ numbers: validation.numbers }),
      });
      setResult(analysis);
    } catch (cause) {
      setError(
        cause instanceof BrowserApiError && cause.message
          ? cause.message
          : "번호 분석 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="analysis-layout">
      <form onSubmit={handleSubmit} className={styles.form}>
        <label>
          번호 6개
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="예: 3, 11, 19, 28, 34, 42"
            autoComplete="off"
            disabled={pending}
          />
        </label>
        <button type="submit" disabled={pending}>
          {pending ? "점검 중..." : "분석하기"}
        </button>
      </form>

      {pending ? <p className="muted" aria-live="polite">역대 1등 당첨 이력을 점검하고 있습니다.</p> : null}

      {error ? (
        <p className={`status-text ${styles.error}`} role="alert" aria-live="assertive">
          {error}
        </p>
      ) : null}

      {result ? <AnalysisResult analysis={result} title="분석 결과" /> : null}
    </div>
  );
}
