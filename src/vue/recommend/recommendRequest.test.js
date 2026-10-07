import test from 'node:test';
import assert from 'node:assert/strict';
import {
    COUNT_OPTIONS,
    DEFAULT_COUNT,
    DEFAULT_STRATEGY,
    GENERATION_LIMIT_CODE,
    STRATEGIES,
    buildRequest,
    describeFailure,
    formatAll,
    formatCombination,
    letterOf,
} from './recommendRequest.js';

test('기본값은 예전 화면이 고정으로 보내던 조건(공동 당첨 위험 완화, 5개)이다', () => {
    assert.equal(DEFAULT_STRATEGY, 'reduce_shared_winner_risk');
    assert.equal(DEFAULT_COUNT, 5);
    assert.deepEqual(buildRequest({ strategy: DEFAULT_STRATEGY, count: DEFAULT_COUNT }), {
        strategy: 'reduce_shared_winner_risk',
        count: 5,
        lockedNumbers: [],
        excludedNumbers: [],
    });
});

test('요청은 고른 전략과 개수를 그대로 싣는다', () => {
    const body = buildRequest({ strategy: 'balanced', count: 3 });
    assert.equal(body.strategy, 'balanced');
    assert.equal(body.count, 3);
});

test('추천 방식은 API가 받는 전략 3가지를 모두 갖고, 첫 항목이 기본값이다', () => {
    assert.deepEqual(
        STRATEGIES.map((option) => option.value),
        ['reduce_shared_winner_risk', 'balanced', 'random'],
    );
    assert.equal(STRATEGIES[0].value, DEFAULT_STRATEGY);
    for (const option of STRATEGIES) {
        assert.ok(option.title && option.description, `${option.value}에 문구가 있어야 한다`);
    }
});

test('개수 선택지는 API 범위 1~10과 같다', () => {
    assert.deepEqual([...COUNT_OPTIONS], [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    assert.ok(COUNT_OPTIONS.includes(DEFAULT_COUNT));
});

test('생성 한도 오류에는 사용자가 할 수 있는 다음 행동을 덧붙인다', () => {
    const message = describeFailure(GENERATION_LIMIT_CODE, '조건을 만족하는 조합을 모두 만들지 못했습니다.');
    assert.match(message, /^조건을 만족하는 조합을 모두 만들지 못했습니다\./);
    assert.match(message, /개수를 줄이거나 조건을 완화해 보세요/);
});

test('그 밖의 오류 문구는 서버 문구 그대로다', () => {
    assert.equal(describeFailure('RATE_LIMITED', '요청이 너무 많습니다.'), '요청이 너무 많습니다.');
    assert.equal(describeFailure(undefined, '일시적인 오류'), '일시적인 오류');
});

test('결과 행 라벨은 A부터 J까지', () => {
    assert.equal(letterOf(0), 'A');
    assert.equal(letterOf(4), 'E');
    assert.equal(letterOf(9), 'J');
});

test('복사 형식: 한 조합은 쉼표로, 전체는 한 줄에 한 조합(라벨 없이)', () => {
    assert.equal(formatCombination([3, 12, 19, 28, 34, 43]), '3, 12, 19, 28, 34, 43');
    assert.equal(
        formatAll([{ numbers: [1, 2, 3, 4, 5, 6] }, { numbers: [7, 8, 9, 10, 11, 12] }]),
        '1, 2, 3, 4, 5, 6\n7, 8, 9, 10, 11, 12',
    );
    assert.equal(formatAll([]), '');
});
