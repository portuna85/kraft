import { Button } from "@/shared/ui/button";

import styles from "./generate-action-bar.module.css";

/**
 * kraft-redesign-plan.md P0: "조합 만들기"는 원래 스크롤이 긴 폼 맨 아래
 * 인라인 버튼이라 화면 밖으로 밀려나 있었다 — 항상 눈에 보이는 위치에
 * 두기 위해 sticky 바로 감싼다.
 *
 * `position: fixed`로 뷰포트에 직접 고정하지 않는다 — 모바일에서는 이미
 * `StickyMobileAd`가 전역 `--fixed-bottom-inset` 싱글턴을 소유하고 있어
 * 두 번째 fixed 요소가 같은 변수를 같이 쓰면 마운트/언마운트 순서에 따라
 * 서로의 값을 덮어쓴다. 대신 `.main`이 이미 계산해 두는 예약 여백
 * (`--tabbar-reserve` + `--fixed-bottom-inset` + 세이프에어리어, `shell.module.css`
 * 참고)만큼 sticky bottom을 띄운다 — 광고·탭바가 있든 없든, 데스크톱에서
 * 둘 다 0이 되는 1152px 이상에서도 별도 분기 없이 같은 계산식이 맞아떨어진다.
 */
export function GenerateActionBar({
  generating,
  disabled,
  onGenerate,
}: {
  generating: boolean;
  disabled: boolean;
  onGenerate: () => void;
}) {
  return (
    <div className={styles.bar}>
      {generating ? (
        <Button size="lg" loading loadingLabel="조합을 만드는 중">
          조합 만들기
        </Button>
      ) : (
        // KF-09: 세션이 아직 미확정이면 클릭 자체를 막는다 — "만드는 중" 스피너를
        // 쓰면 아직 세션을 기다리는 중인데 생성이 진행 중이라고 오해를 준다.
        <Button size="lg" disabled={disabled} onClick={onGenerate}>
          조합 만들기
        </Button>
      )}
    </div>
  );
}
