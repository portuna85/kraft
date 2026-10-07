import test from 'node:test';
import assert from 'node:assert/strict';
import { IF_MATCH, ifMatchHeaders, ifMatchValue } from './etag.js';

test('버전을 따옴표로 감싼 강한 ETag 값으로 만든다', () => {
    assert.equal(ifMatchValue(3), '"3"');
    assert.equal(ifMatchValue('12'), '"12"');
    assert.equal(ifMatchValue(0), '"0"');
});

test('수정 요청용 헤더 객체는 If-Match 하나만 담는다', () => {
    assert.deepEqual(ifMatchHeaders(5), { 'If-Match': '"5"' });
    assert.equal(IF_MATCH, 'If-Match');
});
