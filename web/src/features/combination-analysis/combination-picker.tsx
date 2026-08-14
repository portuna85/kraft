"use client";

import { useState } from "react";

// schema.ts가 아니라 combination.ts에서 가져온다 — schema.ts를 참조하면 valibot과
// 통계 스키마 전량이 이 클라이언트 컴포넌트의 번들로 딸려 온다.
import {
  COMBINATION_SIZE,
  isCompleteCombination,
  parseCombinationInput,
} from "@/entities/statistics/combination";
import { NumberGrid, type NumberMark } from "@/entities/round/ui/number-grid";
import { ROUTES } from "@/shared/config/routes";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/field";

import styles from "./picker.module.css";

/**
 * 조합 입력
 *
 * 분석 결과는 URL이 소유한다(`?numbers=1,2,...`). 그래서 이 컴포넌트는 결과를 가져오지
 * 않고 **입력만** 모아 GET 폼으로 넘긴다. 얻는 것 세 가지:
 *
 * - 분석 결과를 링크로 공유할 수 있고 뒤로가기가 자연히 동작한다(§5.6)
 * - 결과 렌더가 서버에 남아 클라이언트 번들에 분석 응답 처리 코드가 들어가지 않는다
 * - 진짜 `<form method="get">`이라 JS 없이도 직접 입력으로 분석할 수 있다
 *
 * 번호판과 직접 입력은 같은 상태를 본다 — 어느 쪽을 만져도 다른 쪽이 따라온다.
 */
export function CombinationPicker({ initialNumbers }: { initialNumbers: readonly number[] }) {
  const [selected, setSelected] = useState<number[]>([...initialNumbers]);
  /**
   * 친 텍스트를 그대로 들고 있는다 — 파싱 결과로 되돌려 쓰지 않는다.
   *
   * 입력 칸의 값을 `selected.join(", ")`로 두면 키를 하나 누를 때마다 정규화된
   * 문자열이 덮어써서 "3, 14"를 칠 수 없다. 쉼표를 치는 순간 파싱 결과가 [3]이라
   * 값이 "3"으로 되돌아가고, 이어 친 숫자가 앞 숫자에 들러붙는다.
   */
  const [text, setText] = useState(() => initialNumbers.join(", "));

  function apply(next: number[]) {
    setSelected(next);
    setText(next.join(", "));
  }

  function toggle(value: number) {
    if (selected.includes(value)) {
      apply(selected.filter((n) => n !== value));
      return;
    }
    // 6개를 넘겨 고르면 가장 먼저 고른 것을 밀어낸다. 조용히 무시하면 눌렀는데
    // 아무 일도 안 일어난 것처럼 보인다.
    const next = [...selected, value];
    apply(next.length > COMBINATION_SIZE ? next.slice(next.length - COMBINATION_SIZE) : next);
  }

  function onTyped(raw: string) {
    setText(raw);
    setSelected(parseCombinationInput(raw));
  }

  const marks = new Map<number, NumberMark>(selected.map((value) => [value, "locked"]));
  const complete = isCompleteCombination(selected);
  const remaining = COMBINATION_SIZE - selected.length;

  return (
    <form className="stack" method="get" action={ROUTES.analysis}>
      <NumberGrid marks={marks} onToggle={toggle} aria-label="분석할 번호 선택" />

      <TextField
        label="번호 직접 입력"
        hint={`쉼표나 공백으로 구분해 1~45 사이의 번호 ${COMBINATION_SIZE}개를 입력하세요. 위 번호판을 눌러도 됩니다.`}
        name="numbers"
        type="text"
        inputMode="numeric"
        autoComplete="off"
        placeholder="예: 1, 8, 17, 24, 33, 41"
        value={text}
        onChange={(event) => onTyped(event.target.value)}
      />

      {/* 선택 개수는 번호판을 누를 때마다 바뀐다 — 시각적으로만 알리면 화면을 못 보는
          사용자는 몇 개를 골랐는지 알 수 없다. */}
      <p className={styles.count} aria-live="polite">
        {complete
          ? `${COMBINATION_SIZE}개를 모두 골랐습니다.`
          : selected.length === 0
            ? `${COMBINATION_SIZE}개를 골라 주세요.`
            : `${COMBINATION_SIZE}개 중 ${selected.length}개 선택됨 — ${remaining}개 더 필요합니다.`}
      </p>

      <div className={styles.actions}>
        <Button type="submit" variant="primary" disabled={!complete}>
          분석하기
        </Button>
        <Button
          type="button"
          variant="quiet"
          onClick={() => apply([])}
          disabled={selected.length === 0}
        >
          초기화
        </Button>
      </div>
    </form>
  );
}
