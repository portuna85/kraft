/**
 * 홈 — improvement_fe.md §23.1
 *
 * 실제 구현은 Phase 4다. 최신 회차 조회 실패는 200 폴백이 아니라 5xx로 전파해야 하고
 * (§6.5), 이 화면의 LCP 요소가 당첨번호이므로 RSC로 남는다.
 */
export default function HomePage() {
  return (
    <div className="prose stack">
      <h1>KRAFT Lotto</h1>
      <p>새 프론트엔드 셸입니다. 화면 구현은 Phase 4에서 시작합니다.</p>
    </div>
  );
}
