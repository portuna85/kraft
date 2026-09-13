import { qs } from './dom.js';

/**
 * 전역 `bootstrap` 객체를 만지는 유일한 모듈.
 *
 * Bootstrap 5는 jQuery 플러그인($el.modal('show') 같은 형태)을 제공하지 않으므로 클래스 API를
 * 쓰되, 같은 요소에 인스턴스가 중복 생성되지 않도록 getOrCreateInstance로 감싼다.
 *
 * CDN이 막히거나 SRI 해시가 어긋나면 `window.bootstrap`이 없다. 그때 예외를 던지면 모달과
 * 무관한 기능(댓글·추천)까지 함께 죽으므로, 아무 일도 하지 않는 손잡이를 돌려주고 경고만 남긴다.
 *
 * 'show.bs.modal' 같은 이벤트는 요소에서 발생하는 실제 DOM 이벤트라 addEventListener로 그대로
 * 받을 수 있다 — 그 부분은 이 모듈을 거치지 않는다.
 */

const NOOP_HANDLE = { show() {}, hide() {} };

function instanceOf(component, selector) {
    const element = qs(selector);
    if (!element) {
        return NOOP_HANDLE;
    }
    if (typeof bootstrap === 'undefined') {
        console.warn(`Bootstrap을 불러오지 못해 ${selector}를 열 수 없습니다.`);
        return NOOP_HANDLE;
    }
    return bootstrap[component].getOrCreateInstance(element);
}

export const modal = (selector) => instanceOf('Modal', selector);
export const toast = (selector) => instanceOf('Toast', selector);
