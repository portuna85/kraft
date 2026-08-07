import styles from "./shell.module.css";

/**
 * 공개 셸의 계정 영역 — improvement_fe.md §10.1, §15.1
 *
 * 세션을 조회하지 않는다. 완전 공개 라우트에서 세션 API를 부르면 익명 트래픽이 발생하고
 * (불변식 I-4), 세션 프로바이더가 번들에 딸려 들어온다. 로그인 링크는 fetch가 아니라
 * `<a href>`여야 한다 — OAuth는 브라우저 전체 이동이 필요하다.
 */
export function LoginLinks() {
  return (
    <div className="cluster">
      <a className={styles.navLink} href="/oauth2/authorization/google">
        Google 로그인
      </a>
      <a className={styles.navLink} href="/oauth2/authorization/naver">
        Naver 로그인
      </a>
    </div>
  );
}
