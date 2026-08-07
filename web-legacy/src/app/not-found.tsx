import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "페이지를 찾을 수 없습니다",
  robots: { index: false, follow: false },
};

export default function NotFound() {
  return (
    <section className="panel not-found">
      <p className="eyebrow">오류 404</p>
      <h1 className="page-title not-found-title">페이지를<br />찾을 수 없습니다</h1>
      <p className="page-subtitle">
        요청하신 페이지가 없거나 주소가 변경되었습니다. 아래 메뉴에서 다시 이동해 주세요.
      </p>
      <div className="not-found-actions">
        <Link href="/" className="button">홈으로 이동</Link>
        <Link href="/recommend" className="button secondary">번호 추천 받기</Link>
      </div>
    </section>
  );
}
