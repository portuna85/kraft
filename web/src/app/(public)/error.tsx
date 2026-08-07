"use client";

/**
 * 공개 셸 오류 경계 — improvement_fe.md §7.6
 *
 * 여기까지 올라온 오류는 **핵심 데이터 실패**다. 200 폴백으로 감추지 않는다 —
 * 감추면 업타임 체커와 크롤러가 장애를 보지 못하고, Caddy가 그 상태를 60초 캐시한다.
 * 부수 데이터 실패는 각 화면 안에서 ErrorState로 흡수해 여기까지 오지 않아야 한다.
 */
export default function PublicError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div className="prose stack">
      <h1>정보를 불러오지 못했습니다</h1>
      <p>
        잠시 후 다시 시도해 주세요. 문제가 계속되면 서비스 상태 페이지에서 공지를 확인할 수
        있습니다.
      </p>
      <p>
        <button type="button" onClick={reset}>
          다시 시도
        </button>
      </p>
    </div>
  );
}
