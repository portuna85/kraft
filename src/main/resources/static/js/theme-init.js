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
    // 스크립트가 도는 환경이라는 표시(FE-21). 모바일 내비는 서버가 펼친 채로 내려보내고, CSS가 이 클래스가
    // 있을 때만 main.js가 준비되기 전까지 접어 둔다 — main.js가 끝내 실행되지 않으면(로드 실패) load 시점에
    // 이 클래스를 떼어 내비게이션이 다시 보이게 한다(예전에는 hidden이라 영영 못 열었다).
    const html = document.documentElement;
    html.classList.add('js');
    window.addEventListener('load', function () {
        if (!html.classList.contains('js-ready')) {
            html.classList.remove('js');
        }
    });

    const STORAGE_KEY = 'kraft:theme';
    const ORDER = ['system', 'light', 'dark'];

    /**
     * 선택한 테마를 <html>에 반영한다. 'system'이면 data-theme을 떼어 Kraft 자체 토큰(--kraft-*)이 CSS의
     * prefers-color-scheme 미디어 쿼리를 따르게 하고, Bootstrap은 $color-mode-type: data라 속성 없이는
     * 미디어 쿼리에 반응하지 않으므로(bootstrap-custom.scss) 지금 시스템 설정을 읽어 data-bs-theme을
     * 정한다. theme-toggle.js가 같은 함수를 쓴다(window.kraftTheme, FE-31) — 키·순서·적용 로직의 사본을 두지 않는다.
     */
    function apply(value) {
        if (value === 'light' || value === 'dark') {
            html.setAttribute('data-theme', value);
            html.setAttribute('data-bs-theme', value);
            return;
        }
        html.removeAttribute('data-theme');
        const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        html.setAttribute('data-bs-theme', prefersDark ? 'dark' : 'light');
    }

    window.kraftTheme = { STORAGE_KEY: STORAGE_KEY, ORDER: ORDER, apply: apply };

    let stored = null;
    try {
        stored = window.localStorage.getItem(STORAGE_KEY);
    } catch {
        // 저장소를 쓸 수 없어도(프라이빗 모드 등) 시스템 설정만으로 계속 동작한다.
    }
    apply(ORDER.indexOf(stored) >= 0 ? stored : 'system');
})();
