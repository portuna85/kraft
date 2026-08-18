"use client";

import Script from "next/script";
import { useEffect, useState } from "react";

import { BP } from "@/shared/config/breakpoints";
import { publicEnv } from "@/shared/config/env";
import { useElementWidth } from "@/shared/hooks/use-element-width";
import { useKeyboardOpen } from "@/shared/hooks/use-keyboard-open";
import { useMediaQuery } from "@/shared/hooks/use-media-query";

import styles from "./ad-unit.module.css";

/**
 * 광고 유닛, web-legacy 승계
 *
 * 카카오 애드핏(`AdUnit`/`PageAd`)과 구글 애드센스(`AdSenseUnit`/`InArticleAd`
 * 안의 desktop/mobile 경로)를 함께 다룬다. 슬롯이 `frequency` 하나뿐이라도, 다른
 * 페이지에 슬롯을 추가할 때 `PageAdProps["slot"]` 유니온에 값만 더하면 되도록
 * web-legacy의 구조를 그대로 가져왔다 — 새로 설계하지 않는다.
 */
const DESKTOP_QUERY = `(min-width: ${BP.desktop}px)`;

type AdFormat = "desktop" | "mobile" | null;

/**
 * 728×90이 들어갈 폭인지, 모바일 포맷만 들어가는 폭인지, 아니면 광고 자체를
 * 생략해야 하는 폭인지를 컨테이너 실폭으로 판정한다 — 뷰포트 판정만 쓰면 사이드바가
 * 있는 1024~1163px 구간처럼 "데스크톱이지만 728px이 안 들어가는" 경우를 놓친다.
 */
function useAdSlotFormat(mobileMinWidth: number) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const format: AdFormat =
    width === null ? null : width >= 728 ? "desktop" : width >= mobileMinWidth ? "mobile" : null;
  return { ref, width, format };
}

type AdUnitProps = {
  unit: string;
  width: number;
  height: number;
  label?: string;
  className?: string;
};

export function AdUnit({ unit, width, height, label = "광고", className }: AdUnitProps) {
  return (
    <div
      className={`${styles.adUnit}${className ? ` ${className}` : ""}`}
      role="group"
      aria-label={label}
      style={{ maxWidth: "100%", minHeight: height }}
    >
      <ins
        className="kakao_ad_area"
        data-ad-unit={unit}
        data-ad-width={String(width)}
        data-ad-height={String(height)}
      />
      {/* id로 동일 src의 중복 로드를 Next가 자동으로 걸러낸다 — 한 페이지에 AdUnit이
          여러 개(모바일+데스크톱) 있어도 SDK 스크립트는 한 번만 삽입된다.
          lazyOnload: 페이지가 상호작용 가능해진 뒤 로드해 초기 렌더 성능에 영향 없음. */}
      <Script
        id="kakao-adfit-sdk"
        src="https://t1.kakaocdn.net/kas/static/ba.min.js"
        strategy="lazyOnload"
        onError={() => console.error("카카오 애드핏 SDK 로드 실패")}
      />
    </div>
  );
}

type PageAdProps = {
  slot: "frequency";
};

const AD_MOBILE: Record<PageAdProps["slot"], string> = {
  frequency: publicEnv.kakaoAdfitUnitFrequency,
};

const AD_DESKTOP: Record<PageAdProps["slot"], string> = {
  frequency: publicEnv.kakaoAdfitUnitFrequencyDesktop,
};

/**
 * 컨테이너 실폭에 맞는 포맷만 mount한다. 뷰포트 판정을 쓰면 사이드바가 있는
 * 1024~1163px 구간에서 728px 광고가 300px 컬럼 안에서 잘리는 문제가 생긴다.
 */
export function PageAd({ slot }: PageAdProps) {
  const mobile = AD_MOBILE[slot];
  const desktop = AD_DESKTOP[slot];
  const { ref, width, format } = useAdSlotFormat(320);
  if (!mobile && !desktop) return null;

  // width===null: 마운트 전이라 100px 자리를 잡아둔다. width가 측정됐는데도
  // format이 null이면 "포맷 불가"가 확정된 것이므로 자리를 접는다.
  const minHeight = width === null || format ? 100 : 0;

  return (
    <div ref={ref} className={styles.adSlotArticle} style={{ minHeight }} aria-hidden={!format}>
      {format === "desktop" && desktop && (
        <AdUnit unit={desktop} width={728} height={90} className="ad-desktop" />
      )}
      {format === "mobile" && mobile && (
        <AdUnit unit={mobile} width={320} height={100} className="ad-mobile" />
      )}
    </div>
  );
}

type AdSenseUnitProps = {
  slot: string;
  width: number;
  height: number;
  label?: string;
  className?: string;
};

/**
 * slot이 비어 있으면(애드센스 계정 승인 전 등) 기본적으로 아무것도 렌더하지 않는다
 * — 빈 자리를 예약하면 실제로 채워지지 않는 열이 운영 화면에 남는다. 로컬에서 CLS
 * 없이 사이드바/레이아웃을 미리 검증하고 싶을 때만
 * NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER=true로 켠다(운영 기본값은 off).
 */
const RESERVE_PLACEHOLDER = publicEnv.adsenseReservePlaceholder;

export function AdSenseUnit({ slot, width, height, label = "광고", className }: AdSenseUnitProps) {
  const clientId = publicEnv.adsenseClientId;
  const enabled = Boolean(clientId && slot);

  useEffect(() => {
    if (!enabled) return;
    try {
      // @ts-expect-error adsbygoogle 전역
      (window.adsbygoogle = window.adsbygoogle || []).push({});
    } catch (e) {
      console.error("[AdSenseUnit] adsbygoogle push 실패", e);
    }
  }, [enabled]);

  if (!enabled && !RESERVE_PLACEHOLDER) return null;

  return (
    <div
      className={`${styles.adUnit}${className ? ` ${className}` : ""}`}
      role="group"
      aria-label={label}
      style={{ maxWidth: "100%", minHeight: height }}
    >
      {enabled ? (
        <>
          <ins
            className="adsbygoogle"
            style={{ display: "inline-block", width, height, maxWidth: "100%" }}
            data-ad-client={clientId}
            data-ad-slot={slot}
          />
          <Script
            id="adsbygoogle-sdk"
            src={`https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${clientId}`}
            strategy="lazyOnload"
            crossOrigin="anonymous"
            onError={() => console.error("애드센스 SDK 로드 실패")}
          />
        </>
      ) : null}
    </div>
  );
}

const IN_ARTICLE_ADSENSE_MOBILE_ENV: Record<PageAdProps["slot"], string | undefined> = {
  frequency: publicEnv.adsenseUnitFrequencyMobile,
};

const IN_ARTICLE_ADSENSE_DESKTOP_ENV: Record<PageAdProps["slot"], string | undefined> = {
  frequency: publicEnv.adsenseUnitFrequency,
};

/** 애드센스 모바일 포맷(300×250)도 372px 미만 컨테이너에서는 잘리므로 동일하게
 * 컨테이너 실폭 기준으로 포맷을 고른다. */
function InArticleAdSense({ slot }: PageAdProps) {
  const { ref, width, format } = useAdSlotFormat(300);
  const desktopSlot = IN_ARTICLE_ADSENSE_DESKTOP_ENV[slot] ?? "";
  const mobileSlot = IN_ARTICLE_ADSENSE_MOBILE_ENV[slot] ?? "";

  const minHeight = width === null || format ? 250 : 0;

  return (
    <div ref={ref} className={styles.adSlotArticle} style={{ minHeight }} aria-hidden={!format}>
      {format === "desktop" && (
        <AdSenseUnit slot={desktopSlot} width={728} height={90} className="ad-desktop" />
      )}
      {format === "mobile" && (
        <AdSenseUnit slot={mobileSlot} width={300} height={250} className="ad-mobile" />
      )}
    </div>
  );
}

/**
 * 같은 슬롯에 애드핏(PageAd)과 애드센스(AdSenseUnit)를 동시에 넣으면 페이지 무게·
 * 광고 밀도가 두 배가 된다. NEXT_PUBLIC_AD_NETWORK로 슬롯당 한 네트워크만 렌더한다
 * (기본값 "adfit" — 애드센스 승인 전까지의 운영 상태를 유지).
 */
export function InArticleAd({ slot }: PageAdProps) {
  const network = publicEnv.adNetwork;

  if (network === "adsense") {
    return <InArticleAdSense slot={slot} />;
  }

  return <PageAd slot={slot} />;
}

export function AdSenseSidebar({ slot }: { slot: string }) {
  // 사이드바는 컨테이너 폭이 아니라 뷰포트 자체로 노출 여부가 갈린다. 모바일에서
  // mount하면 adsbygoogle.push가 availableWidth=0 상태로 호출돼 낭비 요청·오류가
  // 발생하므로 뷰포트 판정으로 아예 mount하지 않는다.
  const isDesktop = useMediaQuery(DESKTOP_QUERY);

  // useMediaQuery의 SSR 스냅샷은 항상 false라 데스크톱에서도 첫 페인트에는
  // 사이드바가 없다가 하이드레이션 후 300×600이 삽입되며 레이아웃이 밀린다.
  // shell.module.css의 themeTogglePlaceholder와 같은 패턴 — CSS 미디어쿼리로
  // 자리를 먼저 예약하고, JS는 그 안에서 mount 여부만 정한다.
  return (
    <div className={styles.adSidebarSlot}>
      {isDesktop ? (
        <AdSenseUnit
          slot={slot}
          width={300}
          height={600}
          label="사이드바 광고"
          className={`ad-sidebar ${styles.adSidebar}`}
        />
      ) : null}
    </div>
  );
}

/** 광고가 화면에 떠 있는 동안 본문·오버레이 하단 여백이 겹치지 않게, 셸이 소비하는
 * `--fixed-bottom-inset`(shell.module.css:85,99, overlay.module.css:167)에 광고
 * 높이를 게시한다 — 전역 토큰을 광고 CSS가 직접 재정의하지 않는다. */
const STICKY_AD_INSET_PX = "66px";
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
