"use client";

/**
 * 루트 레이아웃 자체가 실패한 경우 — improvement_fe.md §7.6
 * 이 경계는 자체 <html>/<body>를 렌더해야 한다(루트 레이아웃이 죽은 상태이므로).
 * 토큰 CSS도 못 붙은 상황을 가정해 인라인 스타일 없이 최소한만 그린다.
 */
export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <html lang="ko">
      <body>
        <main>
          <h1>화면을 표시할 수 없습니다</h1>
          <p>일시적인 오류일 수 있습니다. 다시 시도해 주세요.</p>
          <button type="button" onClick={reset}>
            다시 시도
          </button>
        </main>
      </body>
    </html>
  );
}
