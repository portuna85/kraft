import type { SectionContract } from "./contracts";
import styles from "./section.module.css";

export function Section({ headingLevel, title, children }: SectionContract) {
  const Heading = `h${headingLevel}` as "h2" | "h3" | "h4";
  return (
    <section className={styles.section}>
      <Heading className={styles.title}>{title}</Heading>
      {children}
    </section>
  );
}
