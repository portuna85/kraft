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
      <SavedNumbersClient latestRound={latest?.round ?? 0} />
      <AccountLibrarySection />
    </section>
  );
}
