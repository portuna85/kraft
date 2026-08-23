"use client";

import { useEffect, useState } from "react";

import { BP } from "@/shared/config/breakpoints";
import { useKeyboardOpen } from "@/shared/hooks/use-keyboard-open";
import { useMediaQuery } from "@/shared/hooks/use-media-query";

import { AdUnit } from "./ad-unit";
import styles from "./ad-unit.module.css";

/**
 * FE-SEC-01(docs/improvement.md): `StickyMobileAd`를 `ad-unit.tsx`(312줄, 애드핏·애드센스
 * 전 계열을 담은 모듈)에서 분리했다. 루트 레이아웃(`app/layout.tsx`)이 이 컴포넌트 하나만
 * 필요로 하는데, 분리 전에는 `ad-unit.tsx` 전체가 루트 진입 청크에 딸려와 `/ops`·
 * `/_not-found`를 포함한 모든 라우트의 초기 JS에 포함됐다. `AdUnit`(카카오 애드핏 SDK를
 * 부르는 실제 렌더 로직)만 import하고 나머지 애드센스 계열 컴포넌트는 가져오지 않는다.
 */
const DESKTOP_QUERY = `(min-width: ${BP.desktop}px)`;

/** 광고가 화면에 떠 있는 동안 본문·오버레이 하단 여백이 겹치지 않게, 셸이 소비하는
 * `--fixed-bottom-inset`(shell.module.css:85,99, overlay.module.css:167)에 광고
 * 높이를 게시한다 — 전역 토큰을 광고 CSS가 직접 재정의하지 않는다.
 *
 * KF-06(docs/improvement.md): 이 값은 광고의 실제 콘텐츠 높이만 담는다 —
 * `.adStickyMobile`(ad-unit.module.css) 자신의 안전영역 여백은 더 이상 여기
 * 포함되지 않는다(탭바가 이미 그 아래에서 흡수한다, 아래 `.adStickyMobile`
 * 위치 참고). `.adStickyMobile .adUnit`의 `min-height:50px` + `border-top:1px`
 * 와 반드시 함께 바뀌어야 한다 — 어긋나면 광고 위 요소들이 실제 높이와 다른
 * 만큼 밀린다. */
const STICKY_AD_INSET_PX = "51px";
/** 낮은 뷰포트(가로모드 휴대폰 등)에서는 고정 광고를 내린다. ad-unit.module.css에는
 * 높이 기준 숨김 규칙이 없으므로(≥1024px 숨김만 CSS에 있음) 이 게이트가 유일한
 * 판정 소스다 — 게이트가 없으면 광고 유닛이 계속 mount된 채 --fixed-bottom-inset이
 * 66px로 게시돼, 세로 공간이 가장 부족한 화면에서 유령 여백이 남는다. */
const SHORT_VIEWPORT_QUERY = "(max-height: 480px)";

export function StickyMobileAd({ unit }: { unit: string }) {
  const [closed, setClosed] = useState(false);
  // CSS로는 ≥1024px에서 display:none으로만 숨기므로, 게이트 없이는 데스크톱에서도
  // 광고 유닛이 mount되어 숨겨진 채로 SDK 요청이 발생한다.
  const isDesktop = useMediaQuery(DESKTOP_QUERY);
  const isShortViewport = useMediaQuery(SHORT_VIEWPORT_QUERY);
  // 가상 키보드가 열려 있는 동안은 고정 광고를 내려 입력 폼과의 겹침을 막는다.
  const keyboardOpen = useKeyboardOpen();

  const visible = !isDesktop && !isShortViewport && !closed && !keyboardOpen && Boolean(unit);

  useEffect(() => {
    document.documentElement.style.setProperty(
      "--fixed-bottom-inset",
      visible ? STICKY_AD_INSET_PX : "0px",
    );
    return () => {
      document.documentElement.style.removeProperty("--fixed-bottom-inset");
    };
  }, [visible]);

  if (!visible) return null;

  return (
    <div className={styles.adStickyMobile}>
      <button
        type="button"
        onClick={() => setClosed(true)}
        aria-label="광고 닫기"
        className={styles.adStickyMobileClose}
      >
        <span aria-hidden="true">✕</span>
      </button>
      <AdUnit unit={unit} width={320} height={50} label="하단 고정 광고" />
    </div>
  );
}
