"use client";

import { useState, type FormEvent } from "react";
import type { AnalysisResponse } from "@/lib/api";
import { analyzeNumbers } from "@/lib/analyze";
import { AnalysisResult } from "@/components/analysis-result";
import { validateLottoNumbers } from "@/lib/lotto-validation";
import styles from "@/app/analysis/analysis.module.css";

export function AnalysisClient() {
  const [input, setInput] = useState("");
  const [result, setResult] = useState<AnalysisResponse | null>(null);
  const [error, setError] = useState("");

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setResult(null);

    const parts = input.split(",").map((value) => value.trim());
    const validation = validateLottoNumbers(parts);

    if (!validation.ok) {
      setError(validation.message);
      return;
    }

    setResult(analyzeNumbers(validation.numbers));
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
          />
        </label>
        <button type="submit">분석하기</button>
      </form>

      {error ? (
        <p className={`status-text ${styles.error}`} role="alert" aria-live="assertive">
          {error}
        </p>
      ) : null}

      {result ? <AnalysisResult analysis={result} title="분석 결과" /> : null}
    </div>
  );
}
