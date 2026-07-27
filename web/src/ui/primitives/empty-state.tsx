import type { EmptyStateContract } from "./contracts";
import { Button } from "./button";
import styles from "./empty-state.module.css";

export function EmptyState({ title, description, action }: EmptyStateContract) {
  return (
    <div className={styles.state}>
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.description}>{description}</p>}
      {action && (
        <Button variant="secondary" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  );
}
