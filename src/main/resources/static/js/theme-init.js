/**
 * 다크 모드 초기화(11단계). 페이지 CSS가 로드된 직후, 본문이 그려지기 전에 실행돼야
 * 한다 — 그래서 이 스크립트만 예외적으로 defer를 붙이지 않는다(다른 정적 스크립트는
 * mount-failure.js처럼 defer가 원칙). defer를 붙이면 본문 파싱·첫 페인트가 먼저 끝난 뒤에야
 * data-theme이 붙어, 시스템이 다크인데 사용자가 이전에 "밝게"를 골라 뒀던 경우 한 프레임
 * 밝은 화면이 번쩍였다가 어두워지는 것을 볼 수 있다. 파일이 아주 작고 장기 캐시(FE-01)되므로
 * 렌더 블로킹 비용보다 이 깜빡임을 막는 이점이 크다.
 *
 * theme-toggle.js(계정 메뉴의 토글 버튼)와 저장 키·값을 공유한다 — 한쪽만 고치면 저장된
 * 값을 다른 쪽이 못 알아듣는 상황이 생기므로 두 파일을 항상 같이 살펴본다.
 */
(function () {
    const STORAGE_KEY = 'kraft:theme'; // 'system' | 'light' | 'dark'

    let stored = null;
    try {
        stored = window.localStorage.getItem(STORAGE_KEY);
    } catch {
        // 저장소를 쓸 수 없어도(프라이빗 모드 등) 시스템 설정만으로 계속 동작한다.
    }

    const root = document.documentElement;
    if (stored === 'light' || stored === 'dark') {
        root.setAttribute('data-theme', stored);
        root.setAttribute('data-bs-theme', stored);
        return;
    }

    // stored가 'system'이거나 없으면: Kraft 자체 토큰(--kraft-*)은 data-theme을 건드리지
    // 않아도 CSS의 prefers-color-scheme 미디어 쿼리가 알아서 따라간다(_tokens.scss). 하지만
    // Bootstrap은 $color-mode-type: data라 [data-bs-theme=dark] 속성 없이는 미디어 쿼리로
    // 자동 반응하지 않는다(bootstrap-custom.scss) — 지금 이 순간의 시스템 설정을 한 번 읽어
    // 반영해 둔다. 이후 시스템 설정이 바뀌면 theme-toggle.js의 matchMedia 리스너가 갱신한다
    // (그 스크립트는 navbar.html의 독립된 테마 토글 버튼과 함께 모든 페이지에 로드된다 —
    // 계정 메뉴 안이 아니라 항상 보이는 버튼이다).
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    root.setAttribute('data-bs-theme', prefersDark ? 'dark' : 'light');
})();
