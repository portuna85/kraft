import styles from "./winning-celebration.module.css";

/** 콘페티 조각 수. CSS가 조각마다 위치·지연을 미리 정의하므로 이 값과 맞아야 한다. */
const PIECE_COUNT = 12;
const PIECES = Array.from({ length: PIECE_COUNT }, (_, index) => index);

/**
 * 당첨 축하 연출
 *
 * **순수 장식이다.** 무엇이 몇 등인지는 MatchResultBadge가 텍스트로 말하고, 이 연출이
 * 없어도 정보는 하나도 줄지 않는다 — 그래서 통째로 `aria-hidden`이다.
 *
 * 조각의 위치·색·지연을 랜덤으로 만들면 조각마다 인라인 style이 필요하고, 그러면
 * style-src에서 'unsafe-inline'을 뺄 수 없다(§20.3). 12개 조각의 궤적을 CSS에
 * 미리 적어 두는 이유다. `prefers-reduced-motion`은 전역 규칙(base.css)이 처리한다.
 */
export function WinningCelebration() {
  return (
    <div className={styles.confetti} aria-hidden="true">
      {PIECES.map((index) => (
        <span key={index} className={styles.piece} />
      ))}
    </div>
  );
}
