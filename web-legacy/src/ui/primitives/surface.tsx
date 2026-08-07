import type { SurfaceContract } from "./contracts";
import styles from "./surface.module.css";

const LEVEL_CLASS = {
  1: styles.level1,
  2: styles.level2,
  3: styles.level3,
} as const;

export function Surface({ level, children }: SurfaceContract) {
  return <div className={`${styles.surface} ${LEVEL_CLASS[level]}`}>{children}</div>;
}
