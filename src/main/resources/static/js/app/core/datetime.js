const OFFSET_SUFFIX = /(Z|[+-]\d{2}:?\d{2})$/i;

const SEOUL_PARTS = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
});

/**
 * 서버가 내려준 시각 문자열을 "2026.09.26 18:05"로 보여 준다(FE-11). 목록 "더 보기"와 댓글이 각자 포맷해서
 * 시간대가 섞여 보이던 것을 한 곳으로 모았다.
 *
 * 서버의 `LocalDateTime`은 시간대 표시가 없는 "2026-09-26T18:05:00"으로 직렬화된다 — 이미 서버(한국) 시각이라
 * 글자를 그대로 쓴다. `new Date()`로 읽으면 보는 사람의 시간대로 해석되어 해외에서 어긋난다. 반대로 `Z`나
 * `+09:00`처럼 시간대가 붙어 있으면 실제 시점이므로 보는 사람의 시간대와 무관하게 Asia/Seoul로 바꿔 표시한다.
 *
 * @param {string | null | undefined} iso
 * @returns {string}
 */
export function formatDateTime(iso) {
    if (!iso) {
        return '';
    }
    if (!OFFSET_SUFFIX.test(iso)) {
        const [date, time] = iso.slice(0, 16).split('T');
        return `${date.replaceAll('-', '.')} ${time ?? ''}`.trim();
    }
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) {
        return '';
    }
    const part = Object.fromEntries(SEOUL_PARTS.formatToParts(parsed).map((p) => [p.type, p.value]));
    return `${part.year}.${part.month}.${part.day} ${part.hour}:${part.minute}`;
}
