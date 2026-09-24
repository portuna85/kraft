import { qs } from './dom.js';

/**
 * @typedef {Object} BootstrapUiHandle
 * @property {() => void} show
 * @property {() => void} hide
 */

/**
 * `modal()`/`toast()`가 Bootstrap을 불러오지 못했을 때 돌려주는 값. 호출부가 이 참조와
 * 비교하면(===) 화면에 아무것도 뜨지 않았다는 것을 알 수 있다 — 중요한 오류라면 flash 같은
 * Bootstrap JS 비의존 수단으로 대신 알려야 한다(F05, ui/toast.js 참고).
 * @type {BootstrapUiHandle}
 */
export const NOOP_HANDLE = Object.freeze({
    show() {},
    hide() {},
});

/**
 * 전역 `bootstrap` 객체를 만지는 유일한 모듈.
 *
 * Bootstrap 5는 jQuery 플러그인($el.modal('show') 같은 형태)을 제공하지 않으므로 클래스 API를
 * 쓰되, 같은 요소에 인스턴스가 중복 생성되지 않도록 getOrCreateInstance로 감싼다.
 *
 * Bootstrap JS는 이 서버가 직접 제공하지만(layout/footer.html, 개선 보고서 F08) 그 요청 자체가
 * 실패하거나 늦게 도착할 수는 있다. 그때 `window.bootstrap`이 없는 채로 예외를 던지면 모달과
 * 무관한 기능(댓글·추천)까지 함께 죽으므로, 아무 일도 하지 않는 손잡이를 돌려주고 경고만 남긴다.
 *
 * 'show.bs.modal' 같은 이벤트는 요소에서 발생하는 실제 DOM 이벤트라 addEventListener로 그대로
 * 받을 수 있다 — 그 부분은 이 모듈을 거치지 않는다.
 *
 * @returns {typeof import('bootstrap') | null}
 */
function getBootstrap() {
    if (typeof window !== 'undefined' && window.bootstrap) {
        return window.bootstrap;
    }
    if (typeof bootstrap !== 'undefined') {
        return bootstrap;
    }
    return null;
}

/**
 * @param {string | Element | null} selectorOrElement
 * @returns {import('bootstrap').Modal | BootstrapUiHandle}
 */
export function modal(selectorOrElement) {
    const element = typeof selectorOrElement === 'string'
        ? qs(selectorOrElement)
        : selectorOrElement;
    if (!element) {
        return NOOP_HANDLE;
    }
    const bs = getBootstrap();
    if (!bs || !bs.Modal || typeof bs.Modal.getOrCreateInstance !== 'function') {
        console.warn(`Bootstrap을 불러오지 못해 Modal(${selectorOrElement})을 열 수 없습니다.`);
        return NOOP_HANDLE;
    }
    return bs.Modal.getOrCreateInstance(element);
}

/**
 * @param {string | Element | null} selectorOrElement
 * @param {{autohide?: boolean, delay?: number} | undefined} [options] 매번 다른 옵션이
 *   필요할 때만 넘긴다(FE-13, 오류 토스트는 자동으로 닫히지 않게). `getOrCreateInstance`는
 *   같은 요소에 이미 인스턴스가 있으면 두 번째 인자를 무시하므로, options가 주어지면 기존
 *   인스턴스를 버리고 새 옵션으로 다시 만든다 — `#app-toast`처럼 여러 화면이 공유하는 단일
 *   요소가 메시지 종류에 따라 다른 동작을 해야 할 때 쓴다.
 * @returns {import('bootstrap').Toast | BootstrapUiHandle}
 */
export function toast(selectorOrElement, options) {
    const element = typeof selectorOrElement === 'string'
        ? qs(selectorOrElement)
        : selectorOrElement;
    if (!element) {
        return NOOP_HANDLE;
    }
    const bs = getBootstrap();
    if (!bs || !bs.Toast || typeof bs.Toast.getOrCreateInstance !== 'function') {
        console.warn(`Bootstrap을 불러오지 못해 Toast(${selectorOrElement})를 열 수 없습니다.`);
        return NOOP_HANDLE;
    }
    if (options) {
        bs.Toast.getInstance(element)?.dispose();
        return bs.Toast.getOrCreateInstance(element, options);
    }
    return bs.Toast.getOrCreateInstance(element);
}
