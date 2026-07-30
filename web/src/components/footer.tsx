import Link from "next/link";

const infoLinks = [
  { href: "/info/data-source", label: "데이터 출처" },
  { href: "/info/methodology", label: "분석 기준" },
  { href: "/info/faq", label: "FAQ" },
  { href: "/status", label: "서비스 상태" },
  { href: "/info/privacy", label: "개인정보처리방침" },
  { href: "/info/terms", label: "이용약관" },
  { href: "/info/responsible-play", label: "건전한 이용" },
  { href: "/info/community-guidelines", label: "커뮤니티 이용규칙" },
  { href: "/info/contact", label: "문의하기" },
];

export function Footer() {
  return (
    <footer className="site-footer-bottom">
      <div className="shell footer-inner">
        <nav className="footer-nav">
          {infoLinks.map((link) => (
            <Link key={link.href} href={link.href}>
              {link.label}
            </Link>
          ))}
        </nav>
        <p className="footer-copy">
          당첨 결과는 동행복권 공식 데이터를 기준으로 제공하며, 표기 시간대는 KST입니다.
        </p>
        <p className="footer-copy muted">
          본 서비스는 당첨을 보장하지 않으며 모든 번호와 통계는 참고용입니다.
        </p>
      </div>
    </footer>
  );
}
