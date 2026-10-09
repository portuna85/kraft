// @ts-check

/**
 * fetch `Response`를 해석해 `ApiError`로 분류하는 순수 로직. `http.js`가 모듈 로드 시점에
 * DOM(`document`)을 건드리는 것과 달리 이 파일은 순수하다 — `node --test`로 직접
 * 단위 테스트할 수 있게 일부러 분리했다.
 */

const LOGIN_REQUIRED = '로그인이 필요합니다. 다시 로그인해 주세요.';
const FORBIDDEN =
    '권한이 없거나 세션·보안 토큰이 만료되었습니다. 새로고침 후 다시 시도하거나 다시 로그인해 주세요.';
const GENERIC = '오류가 발생했습니다.';
const PARSE_FAILED = '서버 응답을 해석할 수 없습니다. 다시 시도해 주세요.';

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

/**
 * 응답을 해석한다. **순서가 곧 정확성**이라 아래 차례를 바꾸면 안 된다.
 *
 * @param {Response} response
 * @returns {Promise<any>}
 */
export async function parse(response) {
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
    // 분류되지 못한 채 호출부까지 원시 SyntaxError로 새어 나갔다.
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
