"use client";

import { useState, type FormEvent } from "react";
import type { AnalysisResponse } from "@/lib/api";
import { AnalysisResult } from "./analysis-result";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import { validateLottoNumbers } from "@/lib/lotto-validation";
import { Button } from "@/ui/primitives/button";
import { TextField } from "@/ui/primitives/text-field";
import { AnalysisNumberPicker } from "./analysis-number-picker";
import styles from "./analysis.module.css";

export function AnalysisClient() {
  const errorMessageId = "analysis-number-error";
  const [input, setInput] = useState("");
  const [result, setResult] = useState<AnalysisResponse | null>(null);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setResult(null);

    const parts = input.trim().split(/[\s,]+/).filter(Boolean);
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
      <form onSubmit={handleSubmit} className={styles.form} noValidate>
        <div className={styles.fieldGroup}>
          {/* FE-037: 번호판을 주 입력 수단으로 두고, 직접 입력·붙여넣기를 위해
              텍스트 입력도 함께 남긴다. 둘은 같은 문자열 상태를 공유한다. */}
          <AnalysisNumberPicker input={input} onChange={setInput} disabled={pending} />
          <TextField
            id="analysis-numbers"
            label="번호 6개"
            value={input}
            onChange={setInput}
            placeholder="예: 3, 11, 19, 28, 34, 42"
            autoComplete="off"
            inputMode="numeric"
            disabled={pending}
            invalid={Boolean(error)}
            errorMessageId={error ? errorMessageId : undefined}
            required
          />
          <p className={styles.hint}>번호판에서 6개를 고르거나, 쉼표·공백으로 구분해 직접 입력하세요. 번호는 1부터 45까지 중복 없이 6개여야 합니다.</p>
        </div>
        <Button type="submit" variant="primary" loading={pending} loadingLabel="번호 조합을 분석하고 있습니다">
          분석하기
        </Button>
      </form>

      {pending ? <p className="muted" aria-live="polite">역대 1등 당첨 이력을 점검하고 있습니다.</p> : null}

      {error ? (
        <p id={errorMessageId} className={`status-text ${styles.error}`} role="alert" aria-live="assertive">
          {error}
        </p>
      ) : null}

      {result ? <AnalysisResult analysis={result} title="분석 결과" /> : null}
    </div>
  );
}
