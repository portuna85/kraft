import { test } from 'node:test';
import assert from 'node:assert/strict';
import { truncate } from './text.js';

test('max 이하이면 그대로 둔다', () => {
    assert.equal(truncate('abc', 3), 'abc');
});

test('max를 넘으면 자르고 말줄임표를 붙인다', () => {
    assert.equal(truncate('abcdef', 3), 'abc…');
});

test('비어 있거나 없으면 빈 문자열이다', () => {
    assert.equal(truncate('', 5), '');
    assert.equal(truncate(null, 5), '');
    assert.equal(truncate(undefined, 5), '');
});
