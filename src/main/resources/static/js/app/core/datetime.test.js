// 시각 표시 로직 테스트(FE-11). `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatDateTime } from './datetime.js';

test('시간대 표시가 없는 서버 시각은 글자를 그대로 쓴다', () => {
    assert.equal(formatDateTime('2026-09-26T18:05:00'), '2026.09.26 18:05');
    assert.equal(formatDateTime('2026-09-26T18:05:00.123456'), '2026.09.26 18:05');
});

test('시간대가 붙은 시각은 Asia/Seoul로 바꿔 보여 준다', () => {
    assert.equal(formatDateTime('2026-09-26T09:05:00Z'), '2026.09.26 18:05');
    assert.equal(formatDateTime('2026-09-26T18:05:00+09:00'), '2026.09.26 18:05');
    assert.equal(formatDateTime('2026-09-26T23:30:00-05:00'), '2026.09.27 13:30');
});

test('비었거나 읽을 수 없으면 빈 문자열이다', () => {
    assert.equal(formatDateTime(null), '');
    assert.equal(formatDateTime(''), '');
    assert.equal(formatDateTime('2026-13-99T99:99:99Z'), '');
});
