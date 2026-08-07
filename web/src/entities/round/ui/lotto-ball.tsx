import styles from "./lotto-ball.module.css";

/**
 * 로또 볼 — improvement_fe.md §9.4
 *
 * 구간별 색은 도메인 관례라 유지하되, **색은 보조 정보다** — 숫자 자체가 항상 텍스트로
 * 보인다. 색만으로 구간을 구분하게 만들면 색각 이상 사용자에게는 아무 정보도 아니다.
 */
function rangeClass(value: number): string {
  if (value <= 10) return styles.r1 ?? "";
  if (value <= 20) return styles.r2 ?? "";
  if (value <= 30) return styles.r3 ?? "";
  if (value <= 40) return styles.r4 ?? "";
  return styles.r5 ?? "";
}

export function LottoBall({
  value,
  size = "md",
  bonus = false,
}: {
  value: number;
  size?: "sm" | "md" | "lg";
  bonus?: boolean;
}) {
  return (
    <span className={`${styles.ball} ${styles[size]} ${bonus ? styles.bonus : rangeClass(value)}`}>
      {value}
    </span>
  );
}

/**
 * 당첨 번호 6개(+보너스).
 *
 * `<ol>`로 감싼다 — 순서가 있는 값의 목록이고, 스크린리더가 "6개 중 3번째"를 읽어 준다.
 * 보너스는 목록 밖에 두되 "보너스"라는 말이 실제로 읽히게 한다. 기호(+)만 두면
 * 무엇이 보너스인지 알 수 없다.
 */
export function LottoBallSet({
  numbers,
  bonusNumber,
  size = "md",
}: {
  numbers: readonly number[];
  bonusNumber?: number;
  size?: "sm" | "md" | "lg";
}) {
  return (
    <div className={styles.set}>
      <ol className={styles.set}>
        {numbers.map((value) => (
          <li key={value}>
            <LottoBall value={value} size={size} />
          </li>
        ))}
      </ol>

      {bonusNumber !== undefined && (
        <>
          <span className={styles.plus} aria-hidden="true">
            +
          </span>
          <span className={styles.set}>
            <span className="sr-only">보너스 번호</span>
            <LottoBall value={bonusNumber} size={size} bonus />
          </span>
        </>
      )}
    </div>
  );
}
