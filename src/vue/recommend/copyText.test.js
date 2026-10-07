import test from 'node:test';
import assert from 'node:assert/strict';
import { copyText } from './copyText.js';

/** execCommand 폴백을 확인하려는 최소한의 가짜 문서. */
function fakeDoc({ execResult = true, throws = false } = {}) {
    const appended = [];
    const removed = [];
    const textarea = {
        value: '',
        attributes: {},
        style: {},
        selected: false,
        setAttribute(name, value) {
            this.attributes[name] = value;
        },
        select() {
            this.selected = true;
        },
    };
    return {
        textarea,
        appended,
        removed,
        body: {
            appendChild: (node) => appended.push(node),
            removeChild: (node) => removed.push(node),
        },
        createElement: () => textarea,
        execCommand: (command) => {
            if (throws) {
                throw new Error('execCommand 실패');
            }
            assert.equal(command, 'copy');
            assert.equal(textarea.selected, true, '복사 전에 선택돼 있어야 한다');
            return execResult;
        },
    };
}

test('Clipboard API가 있으면 그것으로 복사하고 폴백은 쓰지 않는다', async () => {
    const written = [];
    const doc = fakeDoc();

    const ok = await copyText('3, 12, 19', {
        clipboard: { writeText: async (value) => written.push(value) },
        doc,
    });

    assert.equal(ok, true);
    assert.deepEqual(written, ['3, 12, 19']);
    assert.equal(doc.appended.length, 0);
});

test('Clipboard API가 거절되면 숨긴 textarea + execCommand로 물러서고, 끝나면 textarea를 지운다', async () => {
    const doc = fakeDoc();

    const ok = await copyText('1, 2, 3', {
        clipboard: { writeText: async () => { throw new Error('NotAllowedError'); } },
        doc,
    });

    assert.equal(ok, true);
    assert.equal(doc.textarea.value, '1, 2, 3');
    assert.equal(doc.textarea.attributes.readonly, '');
    assert.equal(doc.textarea.attributes['aria-hidden'], 'true');
    assert.equal(doc.textarea.style.opacity, '0');
    assert.equal(doc.appended.length, 1);
    assert.deepEqual(doc.removed, doc.appended, '복사 뒤 textarea가 문서에 남지 않아야 한다');
});

test('Clipboard API가 아예 없어도(옛 브라우저·비보안 컨텍스트) 폴백으로 복사한다', async () => {
    const doc = fakeDoc();

    assert.equal(await copyText('x', { clipboard: null, doc }), true);
    assert.equal(doc.textarea.value, 'x');
});

test('폴백도 실패하면 false다 — execCommand가 false를 돌려주거나 던지는 경우', async () => {
    assert.equal(await copyText('x', { clipboard: null, doc: fakeDoc({ execResult: false }) }), false);

    const throwing = fakeDoc({ throws: true });
    assert.equal(await copyText('x', { clipboard: null, doc: throwing }), false);
    assert.deepEqual(throwing.removed, throwing.appended, '던져도 textarea는 지운다');
});

test('복사할 방법이 전혀 없으면(문서도 클립보드도 없음) false다', async () => {
    assert.equal(await copyText('x', { clipboard: null, doc: null }), false);
});
