import { Surface } from "@/ui/primitives/surface";
import { Divider } from "@/ui/primitives/divider";
import { AccountMenu } from "@/components/community/account-menu";
import { ThemeToggle } from "@/components/theme-toggle";
import styles from "./account-theme-group.module.css";

// "계정 및 테마"는 시각적 그룹화만 한다 — AccountMenu·
// ThemeToggle 내부 로직은 그대로, Surface로 감싸기만 한다.
export function AccountThemeGroup() {
  return (
    <Surface level={1}>
      <div className={styles.group}>
        <AccountMenu />
        <Divider orientation="vertical" />
        <ThemeToggle />
      </div>
    </Surface>
  );
}
