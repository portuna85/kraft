import type { Metadata } from "next";

import { getLatestRound } from "@/entities/round/api";
import { SavedLibrary } from "@/features/saved-library/saved-library";

export const metadata: Metadata = {
  title: "보관함",
  description: "저장한 번호를 모아 보고 원하는 회차와 대조합니다.",
  // 개인 화면이라 색인하지 않는다(§23.7).
  robots: { index: false, follow: false },
};

/**
 * 보관함
 *
 * 최신 회차만 서버에서 가져온다. 대조 기본값으로 쓸 회차 번호가 필요한데, 그것까지
 * 클라이언트가 물어보면 화면이 두 번 왕복한다.
 *
 * **최신 회차 조회 실패가 이 화면을 죽이지 않는다.** 보관함 자체는 회차와 무관하게
 * 볼 수 있어야 한다 — 부수 데이터라 인라인으로 흡수한다(§7.6).
 */
export default async function SavedPage() {
  const latestRound = await getLatestRound()
    .then((round) => round.round)
    .catch(() => 0);

  return (
    <div className="stack">
      <header className="prose stack">
        <h1>보관함</h1>
        <p>
          저장한 번호를 모아 보고 원하는 회차와 대조할 수 있습니다. 로그인하지 않고 저장한 번호는 이
          브라우저에만 연결되며, 로그인하면 계정으로 옮겨집니다.
        </p>
      </header>

      <SavedLibrary latestRound={latestRound} />
    </div>
  );
}
