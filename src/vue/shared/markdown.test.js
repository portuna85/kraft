// 가벼운 마크다운 파서 테스트(12단계). `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseMarkdown } from './markdown.js';

test('빈 문자열은 빈 배열이다', () => {
    assert.deepEqual(parseMarkdown(''), []);
    assert.deepEqual(parseMarkdown('   \n  \n'), []);
});

test('평문 한 문단', () => {
    const blocks = parseMarkdown('안녕하세요');
    assert.deepEqual(blocks, [{ type: 'p', children: ['안녕하세요'] }]);
});

test('빈 줄로 문단을 나눈다', () => {
    const blocks = parseMarkdown('첫 문단\n\n둘째 문단');
    assert.equal(blocks.length, 2);
    assert.deepEqual(blocks[0], { type: 'p', children: ['첫 문단'] });
    assert.deepEqual(blocks[1], { type: 'p', children: ['둘째 문단'] });
});

test('문단 안의 줄바꿈은 br로 보존된다(pre-wrap 모양 유지)', () => {
    const blocks = parseMarkdown('첫 줄\n둘째 줄');
    assert.deepEqual(blocks, [{ type: 'p', children: ['첫 줄', { type: 'br' }, '둘째 줄'] }]);
});

test('굵게', () => {
    const blocks = parseMarkdown('**굵은 글자**입니다');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: [{ type: 'strong', children: ['굵은 글자'] }, '입니다'],
    }]);
});

test('기울임', () => {
    const blocks = parseMarkdown('*기울인 글자*입니다');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: [{ type: 'em', children: ['기울인 글자'] }, '입니다'],
    }]);
});

test('코드', () => {
    const blocks = parseMarkdown('`const x = 1`을 씁니다');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: [{ type: 'code', text: 'const x = 1' }, '을 씁니다'],
    }]);
});

test('굵게와 기울임을 함께 쓸 수 있다(중첩)', () => {
    const blocks = parseMarkdown('**굵고 *기울인* 글자**');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: [{
            type: 'strong',
            children: ['굵고 ', { type: 'em', children: ['기울인'] }, ' 글자'],
        }],
    }]);
});

test('5*3*2 같은 숫자 곱셈 표기는 기울임으로 해석하지 않는다', () => {
    const blocks = parseMarkdown('5*3*2=30');
    assert.deepEqual(blocks, [{ type: 'p', children: ['5*3*2=30'] }]);
});

test('안쪽 양끝이 공백이면 강조로 보지 않는다', () => {
    // 줄 맨 앞의 "* "는 글머리 목록으로 해석되므로, 문단임을 확실히 하려고 문장 중간에 둔다.
    const blocks = parseMarkdown('여기 * 공백 시작*과 *공백 끝 *가 있다');
    assert.deepEqual(blocks, [{ type: 'p', children: ['여기 * 공백 시작*과 *공백 끝 *가 있다'] }]);
});

test('닫는 기호가 없으면 글자 그대로 남는다', () => {
    const blocks = parseMarkdown('**닫히지 않음');
    assert.deepEqual(blocks, [{ type: 'p', children: ['**닫히지 않음'] }]);
});

test('http/https 링크', () => {
    const blocks = parseMarkdown('[카카오](https://kakao.com) 방문');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: [
            { type: 'a', href: 'https://kakao.com', children: ['카카오'] },
            ' 방문',
        ],
    }]);
});

test('javascript: 링크는 평문으로 남는다(XSS 방지)', () => {
    const blocks = parseMarkdown('[클릭](javascript:alert(1))');
    assert.deepEqual(blocks, [{ type: 'p', children: ['[클릭](javascript:alert(1))'] }]);
});

test('data: 링크도 평문으로 남는다', () => {
    const blocks = parseMarkdown('[이미지](data:text/html,<script>alert(1)</script>)');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: ['[이미지](data:text/html,<script>alert(1)</script>)'],
    }]);
});

test('HTML 태그는 해석하지 않고 글자 그대로 남는다', () => {
    const blocks = parseMarkdown('<script>alert(1)</script>');
    assert.deepEqual(blocks, [{ type: 'p', children: ['<script>alert(1)</script>'] }]);
});

test('글머리 목록', () => {
    const blocks = parseMarkdown('- 첫째\n- 둘째');
    assert.deepEqual(blocks, [{ type: 'ul', items: [['첫째'], ['둘째']] }]);
});

test('* 로 시작하는 글머리 목록', () => {
    const blocks = parseMarkdown('* 첫째\n* 둘째');
    assert.deepEqual(blocks, [{ type: 'ul', items: [['첫째'], ['둘째']] }]);
});

test('번호 목록', () => {
    const blocks = parseMarkdown('1. 첫째\n2. 둘째');
    assert.deepEqual(blocks, [{ type: 'ol', items: [['첫째'], ['둘째']] }]);
});

test('목록 항목 안의 인라인 서식도 해석된다', () => {
    const blocks = parseMarkdown('- **중요**: 확인');
    assert.deepEqual(blocks, [{
        type: 'ul',
        items: [[{ type: 'strong', children: ['중요'] }, ': 확인']],
    }]);
});

test('목록 줄이 섞여 있으면(전부 - 로 시작하지 않으면) 목록으로 보지 않고 문단으로 남는다', () => {
    const blocks = parseMarkdown('- 첫째\n둘째 줄은 목록이 아님');
    assert.deepEqual(blocks, [{
        type: 'p',
        children: ['- 첫째', { type: 'br' }, '둘째 줄은 목록이 아님'],
    }]);
});

test('목록과 문단이 빈 줄로 나뉘면 각각 다른 블록이다', () => {
    const blocks = parseMarkdown('문단\n\n- 목록1\n- 목록2\n\n다음 문단');
    assert.equal(blocks.length, 3);
    assert.equal(blocks[0].type, 'p');
    assert.equal(blocks[1].type, 'ul');
    assert.equal(blocks[2].type, 'p');
});
