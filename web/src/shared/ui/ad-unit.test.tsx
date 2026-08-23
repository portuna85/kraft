import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AdSenseSidebar, AdSenseUnit, AdUnit, InArticleAd, PageAd } from "./ad-unit";

// matchMedia mock — AdSenseSidebar가 useMediaQuery(min-width:1024px)로 "이 뷰포트에서
// 아예 노출되는가"를 판단한다(콘텐츠 컬럼 폭과 무관). StickyMobileAd의 같은 목적 테스트는
// sticky-mobile-ad.test.tsx로 옮겨졌다(FE-SEC-01, docs/improvement.md).
function mockMatchMedia(matches: boolean | ((query: string) => boolean)) {
  const resolve = typeof matches === "function" ? matches : () => matches;
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: resolve(query),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// ResizeObserver mock — PageAd/InArticleAd는 뷰포트가 아니라 컨테이너 실측 폭
// (useElementWidth)으로 포맷을 고른다.
let mockContainerWidth = 0;

class MockResizeObserver {
  #callback: ResizeObserverCallback;
  constructor(callback: ResizeObserverCallback) {
    this.#callback = callback;
  }
  observe() {
    this.#callback(
      [{ contentRect: { width: mockContainerWidth } } as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    );
  }
  unobserve() {}
  disconnect() {}
}

beforeEach(() => {
  vi.stubGlobal("ResizeObserver", MockResizeObserver);
  mockContainerWidth = 0;
  mockMatchMedia(false);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("광고 유닛", () => {
  it("전달받은 unit·width·height로 ins 태그를 렌더링하고 폭은 컨테이너에 맞춘다", () => {
    render(<AdUnit unit="DAN-abc123" width={320} height={100} />);

    const ins = document.querySelector("ins.kakao_ad_area");
    expect(ins).toBeInTheDocument();
    expect(ins).toHaveAttribute("data-ad-unit", "DAN-abc123");
    expect(ins).toHaveAttribute("data-ad-width", "320");
    expect(ins).toHaveAttribute("data-ad-height", "100");

    const container = screen.getByLabelText("광고");
    expect(container).toHaveStyle({ maxWidth: "100%", minHeight: "100px" });
  });

  it("label을 지정하면 aria-label에 반영된다", () => {
    render(<AdUnit unit="DAN-abc123" width={320} height={100} label="빈도 통계 광고" />);

    expect(screen.getByRole("group", { name: "빈도 통계 광고" })).toBeInTheDocument();
  });

  it("모바일·데스크톱 유닛이 모두 없으면 아무것도 렌더링하지 않는다", () => {
    const { container } = render(<PageAd slot="frequency" />);

    expect(container).toBeEmptyDOMElement();
  });
});

describe("PageAd 컨테이너 폭 기반 포맷 선택", () => {
  // 슬롯별 env 매핑이 모듈 로드 시점에 한 번 계산되므로(빌드타임 치환과 동일한 전제),
  // 값을 바꿔서 검증하려면 매번 모듈을 새로 import해야 한다.
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  it("측정 전(마운트 직후)에는 자리만 예약하고 아무 포맷도 mount하지 않는다", async () => {
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY: "DAN-mobile",
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP: "DAN-desktop",
    };
    vi.resetModules();
    // ResizeObserver.observe가 콜백을 동기 호출하지 않도록 잠시 no-op으로 교체
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
    const { PageAd: FreshPageAd } = await import("./ad-unit");

    const { container } = render(<FreshPageAd slot="frequency" />);

    expect(container.querySelector(".ad-mobile")).not.toBeInTheDocument();
    expect(container.querySelector(".ad-desktop")).not.toBeInTheDocument();
  });

  it("컨테이너 폭이 728px 이상이면 데스크톱 광고만 mount한다", async () => {
    mockContainerWidth = 900;
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY: "DAN-mobile",
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP: "DAN-desktop",
    };
    vi.resetModules();
    const { PageAd: FreshPageAd } = await import("./ad-unit");

    render(<FreshPageAd slot="frequency" />);

    expect(document.querySelector(".ad-desktop")).toBeInTheDocument();
    expect(document.querySelector(".ad-mobile")).not.toBeInTheDocument();
  });

  it("컨테이너 폭이 728px 미만·320px 이상이면 모바일 광고만 mount한다", async () => {
    mockContainerWidth = 360;
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY: "DAN-mobile",
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP: "DAN-desktop",
    };
    vi.resetModules();
    const { PageAd: FreshPageAd } = await import("./ad-unit");

    render(<FreshPageAd slot="frequency" />);

    expect(document.querySelector(".ad-mobile")).toBeInTheDocument();
    expect(document.querySelector(".ad-desktop")).not.toBeInTheDocument();
  });

  it("컨테이너 폭이 320px 미만이면 아무 포맷도 mount하지 않는다", async () => {
    mockContainerWidth = 280;
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY: "DAN-mobile",
      NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP: "DAN-desktop",
    };
    vi.resetModules();
    const { PageAd: FreshPageAd } = await import("./ad-unit");

    render(<FreshPageAd slot="frequency" />);

    expect(document.querySelector(".ad-mobile")).not.toBeInTheDocument();
    expect(document.querySelector(".ad-desktop")).not.toBeInTheDocument();
  });
});

describe("애드센스 유닛", () => {
  // TD-010: clientId는 이제 publicEnv.adsenseClientId(env.ts, 모듈 스코프 상수)에서 온다 —
  // 모듈 로드 시점에 한 번 계산되므로, 값을 바꿔서 검증하려면 매번 모듈을 새로 import해야
  // 한다(PageAd 테스트와 같은 패턴).
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  it("client ID와 slot이 모두 있으면 ins.adsbygoogle 태그를 올바른 속성으로 렌더링한다", async () => {
    process.env = { ...originalEnv, NEXT_PUBLIC_ADSENSE_CLIENT_ID: "ca-pub-1234567890123456" };
    vi.resetModules();
    const { AdSenseUnit: FreshAdSenseUnit } = await import("./ad-unit");

    render(<FreshAdSenseUnit slot="1111111111" width={728} height={90} />);

    const ins = document.querySelector("ins.adsbygoogle");
    expect(ins).toBeInTheDocument();
    expect(ins).toHaveAttribute("data-ad-client", "ca-pub-1234567890123456");
    expect(ins).toHaveAttribute("data-ad-slot", "1111111111");
  });

  it("slot이 비어있으면 아무것도 렌더링하지 않는다(기본값은 빈 자리를 예약하지 않음)", async () => {
    process.env = { ...originalEnv, NEXT_PUBLIC_ADSENSE_CLIENT_ID: "ca-pub-1234567890123456" };
    vi.resetModules();
    const { AdSenseUnit: FreshAdSenseUnit } = await import("./ad-unit");

    const { container } = render(
      <FreshAdSenseUnit slot="" width={300} height={600} label="사이드바 광고" />,
    );

    expect(document.querySelector("ins.adsbygoogle")).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });

  it("client ID가 없으면 아무것도 렌더링하지 않는다", async () => {
    process.env = { ...originalEnv, NEXT_PUBLIC_ADSENSE_CLIENT_ID: "" };
    vi.resetModules();
    const { AdSenseUnit: FreshAdSenseUnit } = await import("./ad-unit");

    const { container } = render(<FreshAdSenseUnit slot="1111111111" width={728} height={90} />);

    expect(document.querySelector("ins.adsbygoogle")).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });
});

describe("애드센스 플레이스홀더 옵트인(NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER)", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  it("RESERVE_PLACEHOLDER=true면 slot이 비어 있어도 자리를 예약한다", async () => {
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER: "true",
    };
    vi.resetModules();
    const { AdSenseUnit: FreshAdSenseUnit } = await import("./ad-unit");

    render(<FreshAdSenseUnit slot="" width={300} height={600} label="사이드바 광고" />);

    expect(document.querySelector("ins.adsbygoogle")).not.toBeInTheDocument();
    const placeholder = screen.getByLabelText("사이드바 광고");
    expect(placeholder).toHaveStyle({ maxWidth: "100%", minHeight: "600px" });
  });
});

describe("본문 광고(슬롯당 한 네트워크만, 컨테이너 폭 기반)", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  it("NEXT_PUBLIC_AD_NETWORK 미설정 시 애드핏만 렌더한다(기본값)", async () => {
    mockContainerWidth = 900;
    process.env = { ...originalEnv };
    delete process.env.NEXT_PUBLIC_AD_NETWORK;
    vi.resetModules();
    const { InArticleAd: FreshInArticleAd } = await import("./ad-unit");

    render(<FreshInArticleAd slot="frequency" />);

    expect(document.querySelector("ins.adsbygoogle")).not.toBeInTheDocument();
  });

  it("NEXT_PUBLIC_AD_NETWORK=adsense면 애드센스만 렌더한다", async () => {
    mockContainerWidth = 900;
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_AD_NETWORK: "adsense",
      NEXT_PUBLIC_ADSENSE_CLIENT_ID: "ca-pub-1234567890123456",
      NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY: "3333333333",
    };
    vi.resetModules();
    const { InArticleAd: FreshInArticleAd } = await import("./ad-unit");

    render(<FreshInArticleAd slot="frequency" />);

    expect(document.querySelector("ins.kakao_ad_area")).not.toBeInTheDocument();
    expect(document.querySelector("ins.adsbygoogle")).toBeInTheDocument();
  });

  it("애드센스 경로도 컨테이너 폭이 300px 미만이면 아무 포맷도 mount하지 않는다", async () => {
    mockContainerWidth = 280;
    process.env = {
      ...originalEnv,
      NEXT_PUBLIC_AD_NETWORK: "adsense",
      NEXT_PUBLIC_ADSENSE_CLIENT_ID: "ca-pub-1234567890123456",
      NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE: "2222222222",
    };
    vi.resetModules();
    const { InArticleAd: FreshInArticleAd } = await import("./ad-unit");

    render(<FreshInArticleAd slot="frequency" />);

    expect(document.querySelector("ins.adsbygoogle")).not.toBeInTheDocument();
  });
});

describe("사이드바 광고(뷰포트 게이트)", () => {
  // TD-010: 위 "애드센스 유닛"과 같은 이유로 모듈을 새로 import해야 한다.
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
    vi.resetModules();
  });

  it("데스크톱 미만 뷰포트에서는 mount하지 않는다(adsbygoogle.push 낭비 요청 방지)", () => {
    mockMatchMedia(false);
    render(<AdSenseSidebar slot="1234567890" />);

    // CLS 방지용 자리 예약 div는 남지만(F-16), 광고 유닛 자체는 mount되지 않는다.
    expect(screen.queryByLabelText("사이드바 광고")).not.toBeInTheDocument();
  });

  it("데스크톱 뷰포트에서는 mount한다", async () => {
    mockMatchMedia(true);
    process.env = { ...originalEnv, NEXT_PUBLIC_ADSENSE_CLIENT_ID: "ca-pub-1234567890123456" };
    vi.resetModules();
    const { AdSenseSidebar: FreshAdSenseSidebar } = await import("./ad-unit");

    render(<FreshAdSenseSidebar slot="1234567890" />);

    expect(screen.getByLabelText("사이드바 광고")).toBeInTheDocument();
  });
});
