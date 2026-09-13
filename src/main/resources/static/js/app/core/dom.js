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

export const byId = (id) => document.getElementById(id);

export const qs = (selector, root = document) => root.querySelector(selector);

export const qsa = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/** 요소가 있을 때만 이벤트를 건다. */
export function on(target, type, handler) {
    if (!target) {
        return;
    }
    target.addEventListener(type, handler);
}

/**
 * 이벤트 위임. 댓글 목록처럼 나중에 다시 그려지는 영역은 document에 한 번만 걸어 둔다.
 * handler는 선택자에 맞는 요소를 첫 인자로 받는다.
 */
export function delegate(type, selector, handler, root = document) {
    root.addEventListener(type, (event) => {
        const match = event.target.closest?.(selector);
        if (match && root.contains(match)) {
            handler(match, event);
        }
    });
}

/** hidden 속성 토글. 대상이 없으면 넘어간다. */
export function setHidden(target, hidden) {
    if (!target) {
        return;
    }
    target.hidden = hidden;
}

/** 버튼을 눌린 상태로 잠그고 보조기기에도 알린다. */
export function setBusy(target, busy) {
    if (!target) {
        return;
    }
    target.disabled = busy;
    target.setAttribute('aria-busy', busy ? 'true' : 'false');
}

/** 값 읽기. 요소가 없으면 빈 문자열 — jQuery의 .val()과 같은 관용구다. */
export const valueOf = (target) => (target ? target.value : '');

export function setText(target, text) {
    if (!target) {
        return;
    }
    target.textContent = text;
}

export function formatFileSize(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(0)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
