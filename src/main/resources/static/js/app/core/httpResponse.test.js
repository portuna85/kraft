// httpResponse.parse()의 순수 로직 테스트(A-QA-08). `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, parse } from './httpResponse.js';

/**
 * @param {string | null} body
 * @param {{status?: number, contentType?: string, redirected?: boolean, url?: string}} [options]
 */
function fakeResponse(body, { status = 200, contentType = 'application/json', redirected = false, url = 'https://example.test/api/v1/posts' } = {}) {
    const headers = new Headers();
    if (contentType) {
        headers.set('content-type', contentType);
    }
    const response = new Response(body, { status, headers });
    // Response 생성자는 redirected·url을 받지 않는다 — fetch가 실제로 리다이렉트를 따라갔을 때만
    // 설정하는 읽기 전용 속성이라, 테스트에서는 값을 흉내 내기 위해 직접 정의한다.
    Object.defineProperty(response, 'redirected', { value: redirected });
    Object.defineProperty(response, 'url', { value: url });
    return response;
}

async function assertApiError(promise, { message, status, kind }) {
    await assert.rejects(promise, (error) => {
        assert.ok(error instanceof ApiError);
        if (message !== undefined) {
            assert.equal(error.message, message);
        }
        assert.equal(error.status, status);
        assert.equal(error.kind, kind);
        return true;
    });
}

test('리다이렉트로 로그인 페이지에 도달하면 상태 코드와 무관하게 인증 오류다', async () => {
    const response = fakeResponse('<html>login</html>', {
        contentType: 'text/html',
        redirected: true,
        url: 'https://example.test/login',
    });

    await assertApiError(parse(response), { status: 200, kind: 'auth' });
});

test('본문이 없는 200은 null을 돌려준다(ResponseEntity<Void>)', async () => {
    const result = await parse(fakeResponse(null, { status: 200 }));

    assert.equal(result, null);
});

test('2xx + JSON 본문은 그대로 돌려준다', async () => {
    const result = await parse(fakeResponse(JSON.stringify({ id: 1 }), { status: 200 }));

    assert.deepEqual(result, { id: 1 });
});

test('2xx인데 HTML이면 로그인 페이지로 본다', async () => {
    await assertApiError(
        parse(fakeResponse('<html></html>', { status: 200, contentType: 'text/html' })),
        { status: 200, kind: 'auth' },
    );
});

test('Content-Type이 JSON이어도 본문이 깨져 있으면 parse 오류로 분류한다', async () => {
    await assertApiError(
        parse(fakeResponse('{not json', { status: 200 })),
        { kind: 'parse', status: 200 },
    );
});

test('ProblemDetail의 detail은 상태 코드보다 먼저 본다', async () => {
    const body = JSON.stringify({ detail: '이미 신고한 글입니다.' });

    await assertApiError(parse(fakeResponse(body, { status: 403 })), {
        message: '이미 신고한 글입니다.',
        status: 403,
        kind: 'problem',
    });
});

test('detail 없는 401은 로그인 필요 안내다', async () => {
    await assertApiError(parse(fakeResponse(null, { status: 401, contentType: '' })), {
        status: 401,
        kind: 'auth',
    });
});

test('detail 없는 403은 권한 없음 안내다', async () => {
    await assertApiError(parse(fakeResponse(null, { status: 403, contentType: '' })), {
        status: 403,
        kind: 'forbidden',
    });
});

test('그 외 오류 상태 코드는 일반 오류로 분류한다', async () => {
    await assertApiError(parse(fakeResponse(null, { status: 500, contentType: '' })), {
        status: 500,
        kind: 'http',
    });
});
