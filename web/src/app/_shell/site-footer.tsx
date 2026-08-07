import Link from "next/link";

import { FOOTER_NAV } from "./nav-items";
import styles from "./shell.module.css";

export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className="shell">
        <div className={styles.footerGroups}>
          {FOOTER_NAV.map((group) => (
            <nav key={group.title} aria-label={group.title}>
              <h2 className={styles.footerTitle}>{group.title}</h2>
              <ul>
                {group.items.map((item) => (
                  <li key={item.href}>
                    <Link className={styles.footerLink} href={item.href}>
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        {/*
         * 확률 고지는 법적 요구사항이라 모든 화면에서 보여야 한다(§3.2). 추천 화면
         * 안에만 두면 다른 경로로 들어온 사용자에게는 노출되지 않는다.
         */}
        <p className={styles.disclaimer}>
          이 서비스가 제공하는 번호는 과거 당첨 데이터를 바탕으로 한 통계 정보일 뿐이며, 당첨을
          보장하지 않습니다. 로또는 매 회차 독립적인 무작위 추첨이고 1등 당첨 확률은 8,145,060분의
          1입니다. 구매는 본인의 판단과 책임으로, 감당할 수 있는 범위에서만 하세요.
        </p>
      </div>
    </footer>
  );
}
