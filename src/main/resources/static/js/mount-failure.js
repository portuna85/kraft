/**
 * Vue 아일랜드가 뜨지 못했을 때(초기 부트스트랩 JSON이 깨졌거나, 진입 스크립트·공유 청크
 * 자체가 404 등으로 못 왔을 때) 마운트 지점에 남기는 최소 안내(개선 보고서 F12).
 *
 * 일반(비-모듈) 스크립트로 header.html <head>에서 가장 먼저 불러온다 — 모듈이 아예 못 왔을
 * 때도 실행되어야 하므로 번들·트랜스파일에 기대지 않고 브라우저가 그대로 실행한다. CSP에서
 * script-src를 'self'로 좁혀도(SEC-05) 인라인 스크립트 없이 동작하도록 별도 파일로 분리했다.
 *
 * 두 경로가 이 함수 하나로 모인다:
 *   - 초기 JSON 검증 실패는 각 mount.js가 직접 부른다.
 *   - 모듈 스크립트 로드 실패는 이 파일이 등록하는 전역 error 리스너(capture 단계)가 감지해
 *     부른다 — <script type="module">의 error 이벤트는 버블링하지 않지만 캡처 단계로는
 *     전파되므로, data-mount-fallback 속성을 붙인 스크립트 엘리먼트를 찾아 호출한다
 *     (onerror="..." 인라인 속성은 그 자체가 CSP가 막는 인라인 스크립트라 쓰지 않는다).
 */
window.kraftVueMountFailed = function (mountPointId) {
    const el = document.getElementById(mountPointId);
    if (!el || el.dataset.mountFailureShown) {
        return;
    }
    el.dataset.mountFailureShown = 'true';
    el.textContent = '';
    const p = document.createElement('p');
    p.className = 'flash flash--danger';
    p.setAttribute('role', 'alert');
    p.textContent = '이 화면을 불러오지 못했습니다. 새로고침해 주세요.';
    el.appendChild(p);
};

document.addEventListener('error', function (event) {
    const target = event.target;
    if (target && target.tagName === 'SCRIPT' && target.dataset && target.dataset.mountFallback) {
        window.kraftVueMountFailed(target.dataset.mountFallback);
    }
}, true);
