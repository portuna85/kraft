// @ts-check
import { qs } from './dom.js';
import { ApiError, parse } from './httpResponse.js';

/**
 * 서버와 이야기하는 유일한 통로.
 *
 * 예전에는 jQuery의 `$(document).ajaxSend` 전역 훅이 모든 요청에 CSRF 헤더를 넣어 주었다.
 * fetch에는 그런 훅이 없으므로 **모든 호출이 이 모듈을 지나가게** 해서 같은 보장을 만든다.
 * 한 군데라도 여기를 우회해 fetch를 직접 부르면 그 요청만 조용히 403이 된다.
 *
 * 응답 해석(`parse`)과 `ApiError`는 DOM에 의존하지 않는 순수 로직이라 `httpResponse.js`로
 * 분리돼 있다 — 이 파일은 모듈 로드 시점에 `document`를 읽으므로 `node --test`로
 * 직접 임포트할 수 없다.
 */

// 모듈 본문은 한 번만 실행되므로 메타 태그도 한 번만 읽는다.
const CSRF_TOKEN = /** @type {HTMLMetaElement | null} */ (qs('meta[name="_csrf"]'))?.content;
const CSRF_HEADER = /** @type {HTMLMetaElement | null} */ (qs('meta[name="_csrf_header"]'))?.content;

const NETWORK = '네트워크 오류가 발생했습니다. 다시 시도해 주세요.';
const TIMEOUT = '요청 시간이 초과되었습니다. 다시 시도해 주세요.';

/** 대부분의 요청에 쓰는 기본 타임아웃. 업로드는 더 오래 걸릴 수 있어 따로 넉넉히 둔다. */
const DEFAULT_TIMEOUT_MS = 15_000;
const UPLOAD_TIMEOUT_MS = 60_000;

export { ApiError };

/** @returns {Record<string, string>} */
function csrfHeaders() {
    return CSRF_HEADER && CSRF_TOKEN ? { [CSRF_HEADER]: CSRF_TOKEN } : {};
}

/**
 * @typedef {Object} RequestOptions
 * @property {string} [method]
 * @property {unknown} [json]
 * @property {FormData} [formData]
 * @property {number} [timeoutMs]
 * @property {Record<string, string>} [headers] 요청마다 덧붙이는 헤더(예: 수정 요청의 `If-Match`)
 */

/**
 * @param {string} url
 * @param {RequestOptions} [options]
 * @returns {Promise<any>}
 */
async function request(url, { method = 'GET', json, formData, timeoutMs = DEFAULT_TIMEOUT_MS, headers: extraHeaders } = {}) {
    /** @type {Record<string, string>} */
    const headers = { Accept: 'application/json' };
    let body;

    if (json !== undefined) {
        headers['Content-Type'] = 'application/json; charset=utf-8';
        body = JSON.stringify(json);
    } else if (formData) {
        // Content-Type을 직접 넣으면 안 된다. 브라우저가 multipart 경계(boundary)와 함께
        // 정해야 하며, 손으로 넣으면 서버가 "no multipart boundary"로 거절한다.
        body = formData;
    }

    // 예전에는 타임아웃이 아예 없어 응답이 오지 않는 요청이 화면에서 영원히 멈췄다. AbortController로 일정 시간 뒤 요청을
    // 스스로 취소한다. 헤더만 오고 본문이 멈추는 경우도 있으므로, 타이머는 parse()의 본문
    // 읽기가 끝날 때까지 살려 둔다 — signal은 fetch뿐 아니라 아직 소비하지 않은 응답 본문
    // 스트림에도 적용되므로, abort 시 parse() 내부의 response.text()도 함께 중단된다.
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
        const response = await fetch(url, {
            method,
            headers: { ...headers, ...extraHeaders, ...(method === 'GET' ? {} : csrfHeaders()) },
            body,
            credentials: 'same-origin',
            redirect: 'follow',
            signal: controller.signal,
        });
        return await parse(response);
    } catch (error) {
        if (error instanceof ApiError) {
            throw error;
        }
        if (/** @type {any} */ (error)?.name === 'AbortError') {
            throw new ApiError(TIMEOUT, { status: 0, kind: 'timeout' });
        }
        // fetch 자체가 거부되거나 본문 스트림이 끊기는 것은 네트워크 단절이다(상태 코드가 없다).
        throw new ApiError(NETWORK, { status: 0, kind: 'network' });
    } finally {
        clearTimeout(timer);
    }
}

export const api = {
    /** @param {string} url */
    get: (url) => request(url),
    /**
     * @param {string} url
     * @param {unknown} [json]
     * @param {{ timeoutMs?: number }} [options] 오래 걸리는 요청(예: 관리자 수동 수집)만 제한 시간을 늘린다.
     */
    post: (url, json, options = {}) => request(url, { method: 'POST', json, ...options }),
    /**
     * @param {string} url
     * @param {unknown} [json]
     * @param {{ headers?: Record<string, string> }} [options] 수정 요청의 `If-Match` 같은 조건부 헤더
     */
    put: (url, json, options = {}) => request(url, { method: 'PUT', json, ...options }),
    // 본문 있는 DELETE는 드물지만 표준이 금지하지 않는다. 회원 탈퇴가 현재 비밀번호를 함께
    // 보낸다 — 되돌릴 수 없는 작업이라 서버가 한 번 더 확인한다.
    /**
     * @param {string} url
     * @param {unknown} [json]
     */
    del: (url, json) => request(url, { method: 'DELETE', json }),
    /**
     * multipart 업로드. 헤더를 받지 않는 별도 메서드로 두어 Content-Type 실수를 막는다.
     * 이미지 업로드는 느린 회선에서 기본 타임아웃보다 오래 걸릴 수 있어 더 넉넉히 잡는다.
     *
     * @param {string} url
     * @param {FormData} formData
     */
    upload: (url, formData) => request(url, { method: 'POST', formData, timeoutMs: UPLOAD_TIMEOUT_MS }),
};

/**
 * 알 수 없는 예외까지 포함해 보여줄 문구를 고른다.
 *
 * @param {unknown} error
 * @returns {string}
 */
export function messageOf(error) {
    return error instanceof ApiError ? error.message : '오류가 발생했습니다.';
}
