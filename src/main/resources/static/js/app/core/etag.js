// @ts-check

/**
 * 낙관적 잠금 버전을 HTTP 조건부 요청 헤더로 옮기는 규칙. 서버의 `EntityTags`와 같은 표기(강한 ETag, 따옴표로 감싼
 * 값)를 쓴다. DOM에 의존하지 않는 순수 함수라 `node --test`로 검사할 수 있다.
 */

/** 수정 요청이 "이 버전을 기준으로 고친다"고 밝히는 헤더 이름. */
export const IF_MATCH = 'If-Match';

/**
 * 서버가 내려준 `version`을 `If-Match` 값으로 만든다.
 *
 * @param {number | string} version
 * @returns {string} 예: `"3"`
 */
export function ifMatchValue(version) {
    return `"${version}"`;
}

/**
 * 수정 요청에 실을 조건부 헤더. `api.put(url, body, { headers: ifMatchHeaders(version) })`로 쓴다.
 *
 * @param {number | string} version
 * @returns {Record<string, string>}
 */
export function ifMatchHeaders(version) {
    return { [IF_MATCH]: ifMatchValue(version) };
}
