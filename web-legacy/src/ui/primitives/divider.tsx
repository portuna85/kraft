import type { DividerContract } from "./contracts";
import styles from "./divider.module.css";

export function Divider({ orientation = "horizontal" }: DividerContract) {
  if (orientation === "vertical") {
    return <div className={styles.vertical} role="separator" aria-orientation="vertical" />;
  }
  return <hr className={styles.horizontal} />;
}
