// 마크다운 툴바 순수 로직 테스트(12단계). `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { applyMarkup } from './markdownToolbar.js';

test('굵게: 선택한 글자를 **로 감싼다', () => {
    const result = applyMarkup('안녕 세상', 3, 5, 'bold'); // "세상" 선택
    assert.equal(result.value, '안녕 **세상**');
    assert.equal(result.value.slice(result.selStart, result.selEnd), '세상');
});

test('기울임: 선택한 글자를 *로 감싼다', () => {
    const result = applyMarkup('안녕 세상', 3, 5, 'italic');
    assert.equal(result.value, '안녕 *세상*');
});

test('코드: 선택한 글자를 백틱으로 감싼다', () => {
    const result = applyMarkup('결과는 42', 4, 6, 'code');
    assert.equal(result.value, '결과는 `42`');
});

test('선택이 없으면 마커 사이에 커서만 놓는다(빈 선택)', () => {
    const result = applyMarkup('안녕', 2, 2, 'bold');
    assert.equal(result.value, '안녕****');
    assert.equal(result.selStart, 4);
    assert.equal(result.selEnd, 4);
});

test('글머리 목록: 선택한 줄마다 "- "를 붙인다', () => {
    const value = '첫째\n둘째\n셋째';
    // "첫째\n둘째" 부분을 선택(둘째 줄 중간까지)해도 그 줄 전체가 대상이 된다.
    const result = applyMarkup(value, 0, value.indexOf('둘째') + 1, 'ul');
    assert.equal(result.value, '- 첫째\n- 둘째\n셋째');
});

test('번호 목록: 선택한 줄마다 순서대로 번호를 붙인다', () => {
    const value = '첫째\n둘째\n셋째';
    const result = applyMarkup(value, 0, value.length, 'ol');
    assert.equal(result.value, '1. 첫째\n2. 둘째\n3. 셋째');
});

test('목록: 선택 없이 커서만 있는 줄 하나에도 적용된다', () => {
    const value = '첫째\n둘째\n셋째';
    const cursor = value.indexOf('둘째') + 1; // "둘째" 줄 중간
    const result = applyMarkup(value, cursor, cursor, 'ul');
    assert.equal(result.value, '첫째\n- 둘째\n셋째');
});

test('링크: 선택한 글자를 라벨로 쓰고 주소 자리가 선택된다', () => {
    const result = applyMarkup('여기 클릭', 3, 5, 'link'); // "클릭" 선택
    assert.equal(result.value, '여기 [클릭](https://)');
    assert.equal(result.value.slice(result.selStart, result.selEnd), 'https://');
});

test('링크: 선택이 없으면 안내 라벨이 들어가고 라벨이 선택된다', () => {
    const result = applyMarkup('문장', 2, 2, 'link');
    assert.equal(result.value, '문장[링크 설명](https://)');
    assert.equal(result.value.slice(result.selStart, result.selEnd), '링크 설명');
});

test('결과가 10,000자를 넘으면 아무것도 하지 않는다', () => {
    const longValue = 'a'.repeat(9999);
    const result = applyMarkup(longValue, 0, 0, 'bold'); // 마커 4글자 추가 시 10,003자
    assert.equal(result.value, longValue);
    assert.equal(result.selStart, 0);
    assert.equal(result.selEnd, 0);
});

test('길이 제한 안에서는 정상 적용된다(경계값)', () => {
    const value = 'a'.repeat(9996);
    const result = applyMarkup(value, 0, 0, 'bold'); // 정확히 10,000자
    assert.equal(result.value.length, 10_000);
});
