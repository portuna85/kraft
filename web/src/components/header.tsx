import Link from "next/link";
import { DesktopNav } from "@/components/desktop-nav";
import { AccountThemeGroup } from "@/components/account-theme-group";
import { MobileSecondaryMenu } from "@/components/mobile-secondary-menu";
import styles from "./header.module.css";

export function Header() {
  return (
    <header className="site-header">
      <div className="shell header-inner">
        <Link href="/" className="brand" aria-label="KRAFT Lotto 홈">
          KRAFT Lotto
        </Link>
        <div className="header-actions">
          <div className={styles.desktopOnly}>
            <DesktopNav />
            <AccountThemeGroup />
          </div>
          <div className={styles.mobileOnly}>
            <MobileSecondaryMenu />
          </div>
        </div>
      </div>
    </header>
  );
}
