/**
 * 마크다운 툴바(12단계)의 순수 로직. `textarea`의 현재 값·선택 영역을 받아 새 값과 새
 * 선택 영역을 돌려준다 — DOM은 `MarkdownToolbar.vue`가 다룬다.
 */

/**
 * 서식 버튼 묶음의 roving tabindex 이동(13단계, WAI-ARIA 툴바 패턴). 좌우 화살표는
 * 순환하고 Home/End는 양 끝으로 보낸다. 다른 키는 현재 인덱스를 그대로 돌려준다.
 *
 * @param {number} currentIndex
 * @param {'ArrowLeft' | 'ArrowRight' | 'Home' | 'End' | string} key
 * @param {number} count
 * @returns {number}
 */
export function nextToolbarIndex(currentIndex, key, count) {
    if (count <= 0) {
        return currentIndex;
    }
    switch (key) {
        case 'ArrowRight':
            return (currentIndex + 1) % count;
        case 'ArrowLeft':
            return (currentIndex - 1 + count) % count;
        case 'Home':
            return 0;
        case 'End':
            return count - 1;
        default:
            return currentIndex;
    }
}

// ContentPolicy.POST_CONTENT_MAX_LENGTH(서버)·textarea의 maxlength(PostSaveApp.vue·
// PostEditApp.vue)와 맞춘 값이다. 세 곳 중 하나만 바뀌면 어긋난다.
const MAX_LENGTH = 10_000;

/**
 * @param {string} value
 * @param {number} selStart
 * @param {number} selEnd
 * @param {'bold' | 'italic' | 'code' | 'ul' | 'ol' | 'link'} kind
 * @returns {{ value: string, selStart: number, selEnd: number }}
 */
export function applyMarkup(value, selStart, selEnd, kind) {
    const start = Math.min(selStart, selEnd);
    const end = Math.max(selStart, selEnd);

    let result;
    switch (kind) {
        case 'bold':
            result = wrapSelection(value, start, end, '**');
            break;
        case 'italic':
            result = wrapSelection(value, start, end, '*');
            break;
        case 'code':
            result = wrapSelection(value, start, end, '`');
            break;
        case 'ul':
            result = prefixLines(value, start, end, () => '- ');
            break;
        case 'ol':
            result = prefixLines(value, start, end, (index) => `${index + 1}. `);
            break;
        case 'link':
            result = wrapAsLink(value, start, end);
            break;
        default:
            return { value, selStart, selEnd };
    }

    // 길이 제한을 넘기면 아무것도 하지 않는다 — 어차피 저장 시점에 서버가 다시 막지만,
    // 여기서 조용히 넘기는 대신 애초에 넘는 변경을 만들지 않는다.
    if (result.value.length > MAX_LENGTH) {
        return { value, selStart, selEnd };
    }
    return result;
}

/** 선택한 글자를 marker로 감싼다. 선택이 없으면 marker 사이에 커서를 둔다. */
function wrapSelection(value, start, end, marker) {
    const before = value.slice(0, start);
    const selected = value.slice(start, end);
    const after = value.slice(end);
    const newValue = `${before}${marker}${selected}${marker}${after}`;

    if (selected) {
        // 감싼 글자를 그대로 선택 상태로 남겨 — 다시 눌러 서식을 확인하기 쉽게.
        return { value: newValue, selStart: start + marker.length, selEnd: end + marker.length };
    }
    const cursor = start + marker.length;
    return { value: newValue, selStart: cursor, selEnd: cursor };
}

/** 선택 영역이 걸친 줄마다 접두사를 붙인다(글머리·번호 목록). */
function prefixLines(value, start, end, prefixOf) {
    const lineStart = value.lastIndexOf('\n', Math.max(start - 1, 0)) + 1;
    let lineEnd = value.indexOf('\n', end);
    if (lineEnd === -1) {
        lineEnd = value.length;
    }

    const before = value.slice(0, lineStart);
    const segment = value.slice(lineStart, lineEnd);
    const after = value.slice(lineEnd);

    const newSegment = segment.split('\n').map((line, index) => prefixOf(index) + line).join('\n');
    const newValue = before + newSegment + after;

    // 정확히 어느 글자가 새로 생겼는지 줄마다 따지는 대신, 바뀐 구간 전체를 선택 상태로
    // 남긴다 — 목록으로 바뀐 결과를 바로 눈으로 확인할 수 있다.
    return { value: newValue, selStart: lineStart, selEnd: lineStart + newSegment.length };
}

/**
 * 선택한 글자를 링크 라벨로 삼는다. 선택이 있었으면 새로 생긴 주소 자리(https://)를
 * 선택해 바로 입력할 수 있게 하고, 없었으면 라벨 자리를 선택해 바로 덮어쓸 수 있게 한다.
 */
function wrapAsLink(value, start, end) {
    const before = value.slice(0, start);
    const selected = value.slice(start, end);
    const after = value.slice(end);
    const hadSelection = Boolean(selected);
    const label = selected || '링크 설명';
    const url = 'https://';
    const newValue = `${before}[${label}](${url})${after}`;

    const labelStart = before.length + 1;
    if (hadSelection) {
        const urlStart = labelStart + label.length + 2; // "](" 두 글자만큼 더 간다.
        return { value: newValue, selStart: urlStart, selEnd: urlStart + url.length };
    }
    return { value: newValue, selStart: labelStart, selEnd: labelStart + label.length };
}
