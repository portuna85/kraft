/**
 * DOM을 다루는 최소 헬퍼.
 *
 * jQuery를 걷어내면서 가장 조심해야 할 차이는 이것이다: jQuery는 선택자가 비어 있어도
 * 조용히 아무 일도 하지 않지만, 네이티브 DOM은 null에 접근하는 순간 던진다.
 * layout/footer 프래그먼트가 모든 페이지에 들어가므로 기능 초기화 코드는 해당 요소가 없는
 * 페이지에서도 실행되고, 한 곳에서 던지면 그 뒤에 등록될 기능이 전부 죽는다.
 *
 * 그래서 여기 있는 함수들은 대상이 없으면 조용히 넘어간다 — jQuery가 주던 안전함을 그대로
 * 유지하되, 그것이 의도된 동작임을 이름과 주석으로 드러낸다.
 */

/**
 * @template {HTMLElement} [T=HTMLElement]
 * @param {string} id
 * @returns {T | null}
 */
export const byId = (id) => /** @type {T | null} */ (document.getElementById(id));

/**
 * @template {Element} [T=Element]
 * @param {string} selector
 * @param {ParentNode} [root=document]
 * @returns {T | null}
 */
export const qs = (selector, root = document) => /** @type {T | null} */ (root.querySelector(selector));

/**
 * 요소가 있을 때만 이벤트를 건다.
 *
 * @template {Event} [E=Event]
 * @param {EventTarget | null} target
 * @param {string} type
 * @param {(event: E) => void} handler
 * @param {AddEventListenerOptions | boolean} [options]
 */
export function on(target, type, handler, options) {
    if (!target) {
        return;
    }
    target.addEventListener(type, /** @type {EventListener} */ (handler), options);
}

/**
 * 이벤트 위임. 댓글 목록처럼 나중에 다시 그려지는 영역은 document에 한 번만 걸어 둔다.
 * handler는 선택자에 맞는 요소를 첫 인자로 받는다.
 *
 * @template {Element} [T=Element]
 * @template {Event} [E=Event]
 * @param {string} type
 * @param {string} selector
 * @param {(element: T, event: E) => void} handler
 * @param {ParentNode} [root=document]
 */
export function delegate(type, selector, handler, root = document) {
    root.addEventListener(type, (event) => {
        const target = /** @type {Element | null} */ (event.target);
        const match = target?.closest?.(selector);
        if (match && root.contains(match)) {
            handler(/** @type {T} */ (match), /** @type {E} */ (event));
        }
    });
}

/**
 * 버튼을 눌린 상태로 잠그고 보조기기에도 알린다.
 *
 * @param {HTMLButtonElement | HTMLElement | null} target
 * @param {boolean} busy
 */
export function setBusy(target, busy) {
    if (!target) {
        return;
    }
    if ('disabled' in target) {
        /** @type {HTMLButtonElement} */ (target).disabled = busy;
    }
    target.setAttribute('aria-busy', busy ? 'true' : 'false');
}

/**
 * 값 읽기. 요소가 없으면 빈 문자열 — jQuery의 .val()과 같은 관용구다.
 *
 * @param {HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | Element | null} target
 * @returns {string}
 */
export const valueOf = (target) => ('value' in (target || {}) ? /** @type {HTMLInputElement} */ (target).value.trim() : '');

/**
 * 값을 trim 없이 그대로 읽는다. 비밀번호 전용이다 — 앞뒤 공백도 사용자가 실제로 입력한
 * 값의 일부이므로, 회원가입(SignupApp.vue)이 원문을 그대로 보내는 정책과 다른 진입점이
 * 어긋나면 안 된다. 예전에는 이 파일의 비밀번호 필드도 {@link valueOf}로 읽어 trim됐는데,
 * 그 값으로 만든 계정을 나중에 같은(공백 포함) 비밀번호로 "현재 비밀번호" 확인을 하면
 * 서버가 일치하지 않는다고 거절했다(개선 보고서 "비밀번호 공백 처리 불일치와 길이 정책").
 *
 * @param {HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | Element | null} target
 * @returns {string}
 */
export const rawValueOf = (target) => ('value' in (target || {}) ? /** @type {HTMLInputElement} */ (target).value : '');

/**
 * @param {Element | null} target
 * @param {string | number | null | undefined} text
 */
export function setText(target, text) {
    if (!target) {
        return;
    }
    target.textContent = text == null ? '' : String(text);
}

/**
 * @param {number} bytes
 * @returns {string}
 */
export function formatFileSize(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(0)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
