import type { ErrorStateContract } from "./contracts";
import { Button } from "./button";
import styles from "./error-state.module.css";

export function ErrorState({ title, description, retry, variant = "block" }: ErrorStateContract) {
  // FE-008: 인라인은 목록·패널 안에 끼어드는 자리라 문단 흐름을 유지하고 재시도를
  // 텍스트 버튼으로 둔다. 블록과 계약(제목·설명·재시도)은 동일하다.
  if (variant === "inline") {
    return (
      <p className={styles.inline} role="alert">
        <span className={styles.inlineTitle}>{title}</span>
        {description ? <span className={styles.inlineDescription}> {description}</span> : null}
        {retry ? (
          <>
            {" "}
            <button type="button" className="link-button" onClick={retry.onClick}>
              {retry.label}
            </button>
          </>
        ) : null}
      </p>
    );
  }

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
