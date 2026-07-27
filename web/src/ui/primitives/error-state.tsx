import type { ErrorStateContract } from "./contracts";
import { Button } from "./button";
import styles from "./error-state.module.css";

export function ErrorState({ title, description, retry }: ErrorStateContract) {
  return (
    <div className={styles.state} role="alert">
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.description}>{description}</p>}
      {retry && (
        <Button variant="secondary" onClick={retry.onClick}>
          {retry.label}
        </Button>
      )}
    </div>
  );
}
