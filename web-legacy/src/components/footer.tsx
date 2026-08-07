import Link from "next/link";

import { footerLinks as infoLinks } from "@/lib/nav-items";

export function Footer() {
  return (
    <footer className="site-footer-bottom">
      <div className="shell footer-inner">
        <div className="footer-brand-block">
          <Link href="/" className="footer-brand">KRAFT <span>LOTTO</span></Link>
          <p>공식 데이터 기반 로또 정보 서비스</p>
          <p className="footer-copy">당첨 결과는 동행복권 공식 데이터를 기준으로 제공하며 표기 시간대는 KST입니다.</p>
        </div>
        <div className="footer-link-block">
          <p className="footer-heading">서비스 안내</p>
          <nav className="footer-nav" aria-label="서비스 안내">
            {infoLinks.map((link) => <Link key={link.href} href={link.href}>{link.label}</Link>)}
          </nav>
        </div>
        <p className="footer-copy footer-responsible">
          모든 유효한 조합의 당첨 확률은 동일합니다. 추천과 과거 통계는 당첨을 보장하지 않으며 미래 결과를 예측하지 않습니다.
        </p>
      </div>
    </footer>
  );
}
