import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { BALL_COLOR_BANDS, BALL_COLOR_LAST, ballColorClass, ballColorName } from './ballColor.js';

test('구간 경계: 1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록', () => {
    const expected = { 1: 'yellow', 10: 'yellow', 11: 'blue', 20: 'blue', 21: 'red', 30: 'red', 31: 'gray', 40: 'gray', 41: 'green', 45: 'green' };
    for (const [n, name] of Object.entries(expected)) {
        assert.equal(ballColorName(Number(n)), name, `${n}번`);
    }
});

test('색 변형 클래스는 공용 .lotto-ball--* 이름이다', () => {
    assert.equal(ballColorClass(7), 'lotto-ball--yellow');
    assert.equal(ballColorClass(45), 'lotto-ball--green');
});

test('정의된 구간은 오름차순이고 마지막 색이 따로 있다', () => {
    const bounds = BALL_COLOR_BANDS.map((band) => band.upTo);
    assert.deepEqual(bounds, [...bounds].sort((a, b) => a - b));
    assert.ok(BALL_COLOR_LAST);
});

// ---- 대비: 공은 aria-hidden이거나 숫자가 따로 읽혀 axe가 보지 않으므로 여기서 값 자체를 검사한다. ----

/** @param {string} hex */
function luminance(hex) {
    const channel = (offset) => {
        const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
        return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

/** @param {string} a @param {string} b */
function contrast(a, b) {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
}

const ballScss = readFileSync(new URL('../../styles/kraft/_lotto-ball.scss', import.meta.url), 'utf-8');
const tokensScss = readFileSync(new URL('../../styles/kraft/_tokens.scss', import.meta.url), 'utf-8');

/** 다크 팔레트의 카드 표면색(공 윤곽이 구분돼야 하는 가장 어두운 배경). */
const darkSurface = /@mixin dark-palette \{[\s\S]*?--kraft-surface:\s*(#[0-9a-fA-F]{6})/.exec(tokensScss)?.[1];

test('다크 카드 표면색을 읽을 수 있다', () => {
    assert.ok(darkSurface, '_tokens.scss의 dark-palette에서 --kraft-surface를 찾지 못했다');
});

for (const name of [...BALL_COLOR_BANDS.map((band) => band.name), BALL_COLOR_LAST]) {
    const bg = new RegExp(`--kraft-ball-${name}-bg:\\s*(#[0-9a-fA-F]{6})`).exec(ballScss)?.[1];
    const fg = new RegExp(`--kraft-ball-${name}-fg:\\s*(#[0-9a-fA-F]{6})`).exec(ballScss)?.[1];

    test(`${name} 공: 숫자(16px 굵게)는 배경 위에서 4.5:1 이상, 윤곽은 다크 카드 위에서 3:1 이상`, () => {
        assert.ok(bg && fg, `--kraft-ball-${name}-bg/-fg가 _lotto-ball.scss에 없다`);
        assert.ok(contrast(fg, bg) >= 4.5, `${name}: 글자 대비 ${contrast(fg, bg).toFixed(2)}`);
        assert.ok(contrast(bg, darkSurface) >= 3, `${name}: 다크 카드 위 윤곽 대비 ${contrast(bg, darkSurface).toFixed(2)}`);
    });
}
