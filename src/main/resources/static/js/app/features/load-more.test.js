// 더 보기 복원 데이터 검증 테스트. `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';

// load-more.js가 끌어오는 core/http.js가 import 시점에 document를 읽는다 — 순수 함수만 검증하므로 빈 stub이면 충분하다.
globalThis.document ??= { querySelector: () => null, querySelectorAll: () => [], getElementById: () => null };
const { parseSavedState } = await import('./load-more.js');

const NOW = 1_000_000_000_000;

const post = (id) => ({
    id, title: `글 ${id}`, category: 'FREE', author: '작성자',
    createdAt: '2026-10-16T09:00:00', viewCount: 3, commentCount: 1, modified: false,
});

function saved(overrides = {}) {
    return JSON.stringify({
        version: 1, savedAt: NOW - 1000, startPage: 0, nextPage: '2', isLast: false, posts: [post(1), post(2)],
        ...overrides,
    });
}

test('정상 복원 데이터는 그대로 돌려준다', () => {
    const state = parseSavedState(saved(), 0, NOW);

    assert.equal(state?.nextPage, '2');
    assert.equal(state?.posts.length, 2);
});

test('값이 없거나 깨진 JSON이면 null이다', () => {
    assert.equal(parseSavedState(null, 0, NOW), null);
    assert.equal(parseSavedState('', 0, NOW), null);
    assert.equal(parseSavedState('{not json', 0, NOW), null);
    assert.equal(parseSavedState('null', 0, NOW), null);
    assert.equal(parseSavedState('"문자열"', 0, NOW), null);
});

test('JSON은 유효하지만 posts가 배열이 아니면 null이다', () => {
    assert.equal(parseSavedState(saved({ posts: {} }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ posts: 'x' }), 0, NOW), null);
});

test('행에 null이나 필수 필드가 빠진 항목이 있으면 null이다', () => {
    assert.equal(parseSavedState(saved({ posts: [post(1), null] }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ posts: [{ id: 1 }] }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ posts: [{ ...post(1), viewCount: '3' }] }), 0, NOW), null);
});

test('구버전(version 없음)·다른 버전은 버린다', () => {
    const legacy = JSON.parse(saved());
    delete legacy.version;

    assert.equal(parseSavedState(JSON.stringify(legacy), 0, NOW), null);
    assert.equal(parseSavedState(saved({ version: 2 }), 0, NOW), null);
});

test('만료되었거나 저장 시각이 미래인 데이터는 버린다', () => {
    assert.equal(parseSavedState(saved({ savedAt: NOW - 31 * 60 * 1000 }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ savedAt: NOW + 1000 }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ savedAt: undefined }), 0, NOW), null);
});

test('시작 페이지가 다르면 다른 진입점의 데이터라 null이다', () => {
    assert.equal(parseSavedState(saved({ startPage: 1 }), 0, NOW), null);
});

test('nextPage와 isLast의 형식이 어긋나면 null이다', () => {
    assert.equal(parseSavedState(saved({ nextPage: 'abc' }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ nextPage: 2 }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ nextPage: '-1' }), 0, NOW), null);
    assert.equal(parseSavedState(saved({ isLast: 'false' }), 0, NOW), null);
});
