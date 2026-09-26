/**
 * 가벼운 마크다운(12단계). 저장 형식은 지금과 똑같은 평문이고, 여기서 하는 일은 "화면에
 * 어떻게 보여줄까"뿐이다 — 파싱 결과는 HTML 문자열이 아니라 태그 이름·자식으로 이루어진
 * 블록 트리(AST)다. `MarkdownBody.vue`가 이 트리를 Vue의 `h()`로 그리므로, 텍스트는 항상
 * 문자열 자식으로 남아 Vue가 그대로 이스케이프한다 — `v-html`도, HTML 문자열 생성도 없다.
 * HTML 태그(`<script>` 등)는 이 파서가 전혀 해석하지 않아 그대로 글자로 남는다.
 *
 * 지원 문법:
 *  - 문단: 빈 줄로 나눈다. 문단 안의 줄바꿈은 `br`로 남겨(기존 평문 글의 pre-wrap 모양 유지).
 *  - 글머리 목록: 줄이 전부 `- ` 또는 `* `로 시작.
 *  - 번호 목록: 줄이 전부 `1. ` 형태로 시작.
 *  - 인라인: `**굵게**`, `*기울임*`(둘 다 안쪽 양끝이 공백이 아니고, 바깥쪽이 글자·숫자에
 *    바로 붙어 있지 않을 때만 — "5*3*2"가 기울임이 되지 않게 막는다), `` `코드` ``,
 *    `[글자](주소)`(http/https만 링크로 만들고 나머지 스킴은 평문으로 남긴다).
 */

const LIST_ITEM_PATTERN = /^[-*]\s+/;
const ORDERED_ITEM_PATTERN = /^\d+\.\s+/;

/**
 * @typedef {{ type: 'p', children: InlineNode[] }
 *   | { type: 'ul' | 'ol', items: InlineNode[][] }} BlockNode
 */

/**
 * @param {string} source
 * @returns {BlockNode[]}
 */
export function parseMarkdown(source) {
    const normalized = String(source ?? '').replace(/\r\n/g, '\n');
    const lines = normalized.split('\n');

    const blocks = [];
    let current = [];
    const flush = () => {
        if (current.length > 0) {
            blocks.push(current);
            current = [];
        }
    };
    for (const line of lines) {
        if (line.trim() === '') {
            flush();
        } else {
            current.push(line);
        }
    }
    flush();

    return blocks.map(blockToNode);
}

/** @returns {BlockNode} */
function blockToNode(lines) {
    if (lines.every((line) => LIST_ITEM_PATTERN.test(line))) {
        return { type: 'ul', items: lines.map((line) => parseInline(line.replace(LIST_ITEM_PATTERN, ''))) };
    }
    if (lines.every((line) => ORDERED_ITEM_PATTERN.test(line))) {
        return { type: 'ol', items: lines.map((line) => parseInline(line.replace(ORDERED_ITEM_PATTERN, ''))) };
    }
    return { type: 'p', children: parseInline(lines.join('\n')) };
}

/**
 * @typedef {string
 *   | { type: 'br' }
 *   | { type: 'code', text: string }
 *   | { type: 'strong' | 'em', children: InlineNode[] }
 *   | { type: 'a', href: string, children: InlineNode[] }} InlineNode
 */

/** @returns {InlineNode[]} */
function parseInline(text) {
    /** @type {InlineNode[]} */
    const nodes = [];
    let buffer = '';
    const flushBuffer = () => {
        if (buffer) {
            nodes.push(buffer);
            buffer = '';
        }
    };

    let i = 0;
    while (i < text.length) {
        const ch = text[i];

        if (ch === '\n') {
            flushBuffer();
            nodes.push({ type: 'br' });
            i += 1;
            continue;
        }

        if (ch === '`') {
            const end = text.indexOf('`', i + 1);
            if (end !== -1 && end > i + 1) {
                flushBuffer();
                nodes.push({ type: 'code', text: text.slice(i + 1, end) });
                i = end + 1;
                continue;
            }
        }

        if (text.startsWith('**', i)) {
            const end = findDelimiterEnd(text, i, '**', false);
            if (end !== -1) {
                flushBuffer();
                nodes.push({ type: 'strong', children: parseInline(text.slice(i + 2, end)) });
                i = end + 2;
                continue;
            }
        }

        if (ch === '*') {
            // 단일 *만 숫자 경계를 추가로 막는다("5*3*2" 같은 곱셈 표기 오인 방지). **는
            // 두 글자라 곱셈과 헷갈릴 일이 없어 문장에 바로 붙는 일반적인 굵게 표기
            // ("**굵게**입니다")까지 막지 않는다.
            const end = findDelimiterEnd(text, i, '*', true);
            if (end !== -1) {
                flushBuffer();
                nodes.push({ type: 'em', children: parseInline(text.slice(i + 1, end)) });
                i = end + 1;
                continue;
            }
        }

        if (ch === '[') {
            const link = tryParseLink(text, i);
            if (link) {
                flushBuffer();
                nodes.push(link.node);
                i = link.nextIndex;
                continue;
            }
        }

        buffer += ch;
        i += 1;
    }
    flushBuffer();
    return nodes;
}

/**
 * `**`·`*` 강조 구간의 닫는 위치를 찾는다. 조건을 만족하지 않으면 -1(문자 그대로 취급).
 *  - 안쪽(여는 기호와 닫는 기호 사이)이 비어 있거나 양끝이 공백이면 안 된다.
 *  - `restrictDigitBoundary`(단일 `*`에만 적용)가 있으면 여는 기호 바로 앞, 닫는 기호
 *    바로 뒤가 숫자면 안 된다 — "5*3*2" 같은 곱셈 표기를 기울임으로 오인하지 않게 막는다.
 */
function findDelimiterEnd(text, start, delimiter, restrictDigitBoundary) {
    if (restrictDigitBoundary && isDigit(start > 0 ? text[start - 1] : null)) {
        return -1;
    }
    const end = text.indexOf(delimiter, start + delimiter.length);
    if (end === -1) {
        return -1;
    }
    const inner = text.slice(start + delimiter.length, end);
    if (!inner || /^\s/.test(inner) || /\s$/.test(inner)) {
        return -1;
    }
    if (restrictDigitBoundary && isDigit(text[end + delimiter.length])) {
        return -1;
    }
    return end;
}

function isDigit(ch) {
    return ch != null && ch >= '0' && ch <= '9';
}

/**
 * `[글자](주소)` 형태를 시도한다. http/https가 아니면 링크로 만들지 않고 null을 돌려준다.
 * @returns {{ node: InlineNode, nextIndex: number } | null}
 */
function tryParseLink(text, start) {
    const closeBracket = text.indexOf(']', start + 1);
    if (closeBracket === -1 || text[closeBracket + 1] !== '(') {
        return null;
    }
    const closeParen = text.indexOf(')', closeBracket + 2);
    if (closeParen === -1) {
        return null;
    }
    const label = text.slice(start + 1, closeBracket);
    const url = text.slice(closeBracket + 2, closeParen).trim();
    if (!/^https?:\/\//i.test(url)) {
        return null;
    }
    return {
        node: { type: 'a', href: url, children: parseInline(label) },
        nextIndex: closeParen + 1,
    };
}
