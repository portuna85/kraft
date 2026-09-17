// @ts-check
import { qs } from './dom.js';

/**
 * 서버와 이야기하는 유일한 통로.
 *
 * 예전에는 jQuery의 `$(document).ajaxSend` 전역 훅이 모든 요청에 CSRF 헤더를 넣어 주었다.
 * fetch에는 그런 훅이 없으므로 **모든 호출이 이 모듈을 지나가게** 해서 같은 보장을 만든다.
 * 한 군데라도 여기를 우회해 fetch를 직접 부르면 그 요청만 조용히 403이 된다.
 */

// 모듈 본문은 한 번만 실행되므로 메타 태그도 한 번만 읽는다.
const CSRF_TOKEN = /** @type {HTMLMetaElement | null} */ (qs('meta[name="_csrf"]'))?.content;
const CSRF_HEADER = /** @type {HTMLMetaElement | null} */ (qs('meta[name="_csrf_header"]'))?.content;

const LOGIN_REQUIRED = '로그인이 필요합니다. 다시 로그인해 주세요.';
const FORBIDDEN =
    '권한이 없거나 세션·보안 토큰이 만료되었습니다. 새로고침 후 다시 시도하거나 다시 로그인해 주세요.';
const NETWORK = '네트워크 오류가 발생했습니다. 다시 시도해 주세요.';
const GENERIC = '오류가 발생했습니다.';
const PARSE_FAILED = '서버 응답을 해석할 수 없습니다. 다시 시도해 주세요.';
const TIMEOUT = '요청 시간이 초과되었습니다. 다시 시도해 주세요.';

/** 대부분의 요청에 쓰는 기본 타임아웃. 업로드는 더 오래 걸릴 수 있어 따로 넉넉히 둔다. */
const DEFAULT_TIMEOUT_MS = 15_000;
const UPLOAD_TIMEOUT_MS = 60_000;

/**
 * 사용자에게 그대로 보여줄 수 있는 한국어 메시지를 담은 오류.
 * 호출부는 `catch (error) { show(error.message) }`로 쓴다.
 */
export class ApiError extends Error {
    /**
     * @param {string} message
     * @param {{status?: number, kind?: string, body?: unknown}} [options]
     */
    constructor(message, { status = 0, kind = 'http', body = null } = {}) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.kind = kind;
        this.body = body;
    }
}

/** @returns {Record<string, string>} */
function csrfHeaders() {
    return CSRF_HEADER && CSRF_TOKEN ? { [CSRF_HEADER]: CSRF_TOKEN } : {};
}

/**
 * 응답을 해석한다. **순서가 곧 정확성**이라 아래 차례를 바꾸면 안 된다.
 *
 * @param {Response} response
 * @returns {Promise<any>}
 */
async function parse(response) {
    // 1. 세션이 끊겨 로그인 페이지로 흘러간 경우. fetch가 리다이렉트를 따라가므로 최종 응답은
    //    200 + HTML이 된다. 상태 코드만 보면 성공으로 오해한다.
    //    (참고: 이 앱의 변경 요청은 CSRF 토큰이 세션에 있어 대개 403이 먼저 난다. 이 분기는
    //     설정이 바뀌거나 프록시를 거칠 때를 위한 방어다.)
    if (response.redirected && new URL(response.url).pathname.startsWith('/login')) {
        throw new ApiError(LOGIN_REQUIRED, { status: response.status, kind: 'auth' });
    }

    // 2. 본문은 한 번만 읽을 수 있다. .json() 뒤에 .text()를 부르면 던진다.
    const contentType = response.headers.get('content-type') ?? '';
    const text = await response.text();
    const isJson = contentType.includes('json');
    // Content-Type이 JSON이라고 해도 본문이 실제로 유효한 JSON이라는 보장은 없다(끊긴 응답,
    // 프록시가 끼워 넣은 오류 페이지 등). 이전에는 이 JSON.parse가 그대로 던져 ApiError로
    // 분류되지 못한 채 호출부까지 원시 SyntaxError로 새어 나갔다(개선 보고서 "프런트엔드
    // 오류 분류와 타입 검사 범위").
    let body = null;
    if (isJson && text) {
        try {
            body = JSON.parse(text);
        } catch {
            throw new ApiError(PARSE_FAILED, { status: response.status, kind: 'parse' });
        }
    }

    if (response.ok) {
        // 3a. 본문 없는 200. 비밀번호 변경·인증메일 재발송이 ResponseEntity<Void>라 여기 온다.
        //     "200인데 JSON이 아니면 인증 만료"로 단순화하면 이 정상 응답들이 오류가 된다.
        if (!text) {
            return null;
        }
        if (isJson) {
            return body;
        }
        // 3b. 2xx인데 HTML이면 로그인 페이지를 받은 것이다(1번의 보조 그물).
        throw new ApiError(LOGIN_REQUIRED, { status: response.status, kind: 'auth' });
    }

    // 4a. ProblemDetail의 detail을 상태 코드보다 먼저 본다. 소유권 거부(403)는 본문에 구체적인
    //     메시지가 있고, CSRF·세션 만료(403)는 본문이 없다. 순서를 뒤집으면 전자가 뭉개진다.
    if (body?.detail) {
        throw new ApiError(body.detail, { status: response.status, kind: 'problem', body });
    }
    if (response.status === 401) {
        throw new ApiError(LOGIN_REQUIRED, { status: 401, kind: 'auth' });
    }
    if (response.status === 403) {
        throw new ApiError(FORBIDDEN, { status: 403, kind: 'forbidden' });
    }
    throw new ApiError(GENERIC, { status: response.status });
}

/**
 * @typedef {Object} RequestOptions
 * @property {string} [method]
 * @property {unknown} [json]
 * @property {FormData} [formData]
 * @property {number} [timeoutMs]
 */

/**
 * @param {string} url
 * @param {RequestOptions} [options]
 * @returns {Promise<any>}
 */
async function request(url, { method = 'GET', json, formData, timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
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

    // 예전에는 타임아웃이 아예 없어 응답이 오지 않는 요청이 화면에서 영원히 멈췄다(개선
    // 보고서 "프런트엔드 오류 분류와 타입 검사 범위"). AbortController로 일정 시간 뒤 요청을
    // 스스로 취소한다.
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    let response;
    try {
        response = await fetch(url, {
            method,
            headers: { ...headers, ...(method === 'GET' ? {} : csrfHeaders()) },
            body,
            credentials: 'same-origin',
            redirect: 'follow',
            signal: controller.signal,
        });
    } catch (error) {
        if (/** @type {any} */ (error)?.name === 'AbortError') {
            throw new ApiError(TIMEOUT, { status: 0, kind: 'timeout' });
        }
        // fetch 자체가 거부되는 것은 네트워크 단절이다(상태 코드가 없다).
        throw new ApiError(NETWORK, { status: 0, kind: 'network' });
    } finally {
        clearTimeout(timer);
    }

    return parse(response);
}

export const api = {
    /** @param {string} url */
    get: (url) => request(url),
    /**
     * @param {string} url
     * @param {unknown} [json]
     */
    post: (url, json) => request(url, { method: 'POST', json }),
    /**
     * @param {string} url
     * @param {unknown} [json]
     */
    put: (url, json) => request(url, { method: 'PUT', json }),
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
    return error instanceof ApiError ? error.message : GENERIC;
}
