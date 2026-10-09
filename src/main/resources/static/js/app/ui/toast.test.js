// 토스트 큐 테스트: 오류 토스트가 떠 있는 동안 도착한 알림이 그 위를 덮지 않고 줄을 서는지. `npm run test:unit`.
//
// Bootstrap과 DOM은 흉내만 낸다. toast.js는 byId()로 요소를 찾고 window.bootstrap.Toast로 보여 주므로,
// 필요한 만큼의 가짜 요소와 Toast 구현만 둔다.
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';


/** @type {Record<string, any>} */
let elements;
let hiddenListener;
let shown;
let showToast;
let loads = 0;

function fakeElement(id) {
    const classes = new Set();
    return {
        id,
        textContent: '',
        attributes: {},
        classList: {
            add: (...names) => names.forEach((n) => classes.add(n)),
            remove: (...names) => names.forEach((n) => classes.delete(n)),
            contains: (n) => classes.has(n),
        },
        setAttribute(name, value) { this.attributes[name] = value; },
        addEventListener(type, listener) {
            if (type === 'hidden.bs.toast') {
                hiddenListener = listener;
            }
        },
    };
}

beforeEach(async () => {
    elements = Object.fromEntries(['app-toast', 'app-toast-title', 'app-toast-body'].map((id) => [id, fakeElement(id)]));
    hiddenListener = null;
    shown = [];
    globalThis.document = /** @type {any} */ ({
        getElementById: (id) => elements[id] ?? null,
        querySelector: (selector) => (selector === '#app-toast' ? elements['app-toast'] : null),
        querySelectorAll: () => [],
    });
    // toast.js는 큐·표시 상태를 모듈 변수로 들고 있어, 테스트마다 새로 불러온다.
    ({ showToast } = await import(`./toast.js?load=${loads += 1}`));
    const instance = { _config: {}, show() { shown.push(elements['app-toast-body'].textContent); } };
    globalThis.window = /** @type {any} */ ({
        bootstrap: { Toast: { getInstance: () => instance, getOrCreateInstance: () => instance } },
    });
});

const body = () => elements['app-toast-body'].textContent;

test('오류 토스트가 떠 있는 동안 도착한 알림은 덮어쓰지 않고 기다린다', () => {
    showToast('첫 오류', 'danger');
    showToast('둘째 알림', 'success');

    assert.equal(body(), '첫 오류');
    assert.deepEqual(shown, ['첫 오류']);
});

test('오류 토스트가 닫히면 기다리던 알림을 순서대로 꺼낸다', () => {
    showToast('첫 오류', 'danger');
    showToast('둘째 오류', 'danger');
    showToast('셋째 알림', 'success');

    hiddenListener();
    assert.equal(body(), '둘째 오류');

    hiddenListener();
    assert.equal(body(), '셋째 알림');
    assert.deepEqual(shown, ['첫 오류', '둘째 오류', '셋째 알림']);
});

test('오류가 아니면 줄을 세우지 않고 바로 바꾼다', () => {
    showToast('완료 1', 'success');
    showToast('완료 2', 'success');

    assert.equal(body(), '완료 2');
});

test('오류 토스트는 role=alert로 알리고 자동으로 닫지 않는다', () => {
    showToast('오류', 'danger');

    assert.equal(elements['app-toast'].attributes.role, 'alert');
    assert.equal(elements['app-toast'].attributes['aria-live'], 'assertive');
});
