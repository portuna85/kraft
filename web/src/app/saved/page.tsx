import type { Metadata } from "next";
import { SavedNumbersClient } from "@/components/saved-numbers-client";
import { AccountLibrarySection } from "@/features/identity/account-library-section";
import { getLatestWinningNumber } from "@/lib/api";

export const metadata: Metadata = {
  title: "저장 번호",
  description: "브라우저에 저장한 번호를 확인하고 관리할 수 있습니다.",
  robots: {
    index: false,
    follow: false,
  },
  alternates: { canonical: "/saved" },
};

export default async function SavedPage() {
  const latest = await getLatestWinningNumber().catch(() => null);

  return (
    <section className="panel">
      <p className="eyebrow">저장 번호</p>
      <h1 className="page-title">저장 번호</h1>
      <p className="muted">
        저장한 번호는 이 브라우저에만 연결됩니다. 다른 기기·브라우저에서는 보이지 않으며,
        로그인하면 계정에 연결해 보관할 수 있습니다.
      </p>
      <SavedNumbersClient latestRound={latest?.round ?? 0} />
      <AccountLibrarySection />
    </section>
  );
}
