import styles from "./feedback.module.css";
import type { EmptyStateContract, ErrorStateContract, Tone } from "./contracts";

/**
 * EmptyState, 레거시 FE-048
 *
 * `reason`이 필수라 "검색 결과 없음"과 "원래 비어 있음"을 같은 화면으로 낼 수 없다.
 * 이 구분이 없으면 사용자는 필터를 지워야 한다는 것을 알 수 없고, 서비스가 고장 난
 * 것으로 오해한다. 필터로 인한 빈 결과에는 필터 해제 액션을 주는 것이 원칙이다.
 */
export function EmptyState({ reason, title, description, action }: EmptyStateContract) {
  return (
    <div className={styles.state} data-reason={reason}>
      <p className={styles.stateTitle}>{title}</p>
      <div className={styles.stateDescription}>{description}</div>
      {action}
    </div>
  );
}

/**
 * ErrorState — `action`이 **필수**다.
 * 복구 경로 없는 오류 화면은 막다른 길이고, 사용자가 할 수 있는 일은 이탈뿐이다.
 */
export function ErrorState({ layout = "block", title, description, action }: ErrorStateContract) {
  return (
    <div
      className={`${styles.state} ${layout === "inline" ? styles.stateInline : ""}`}
      role="alert"
    >
      <p className={styles.stateTitle}>{title}</p>
      <div className={styles.stateDescription}>{description}</div>
      {action}
    </div>
  );
}

const ALERT_CLASS: Record<Tone, string> = {
  neutral: styles.alertNeutral ?? "",
  brand: styles.alertNeutral ?? "",
  success: styles.alertSuccess ?? "",
  warning: styles.alertWarning ?? "",
  danger: styles.alertDanger ?? "",
};

/**
 * InlineAlert — 위험·오류는 role="alert"로 즉시 읽히고, 그 외는 role="status"로
 * 진행 중인 작업을 방해하지 않게 읽힌다. 모든 알림을 alert로 두면 스크린리더 사용자의
 * 작업이 계속 끊긴다.
 */
export function InlineAlert({
  tone = "neutral",
  title,
  children,
}: {
  tone?: Tone;
  title?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={`${styles.alert} ${ALERT_CLASS[tone]}`}
      role={tone === "danger" ? "alert" : "status"}
    >
      {title !== undefined && <p className={styles.stateTitle}>{title}</p>}
      <div>{children}</div>
    </div>
  );
}

/** 장식용 구분선. 의미 있는 구분이 아니면 보조 기술에서 숨긴다. */
export function Divider({ decorative = true }: { decorative?: boolean }) {
  return <hr className={styles.divider} aria-hidden={decorative || undefined} />;
}

/**
 * Skeleton — 자체는 aria-hidden이고, 로딩 상태는 컨테이너의 `aria-busy`가 전달한다.
 * 스켈레톤 자체를 읽히게 두면 의미 없는 빈 요소가 낭독된다.
 *
 * 크기 prop을 받지 않는 이유는 CSP 때문이다. width/height를 인라인 style로 받으면
 * style-src에서 'unsafe-inline'을 뺄 수 없고, 그러면 §20.3의 정책 축소가 이번에도
 * 무산된다. 크기는 부모가 자기 CSS Module에서 정한다.
 */
export function Skeleton({ shape = "text" }: { shape?: "text" | "block" | "circle" }) {
  const shapeClass =
    shape === "block"
      ? styles.skeletonBlock
      : shape === "circle"
        ? styles.skeletonCircle
        : styles.skeletonText;

  return <span className={`${styles.skeleton} ${shapeClass}`} aria-hidden="true" />;
}
