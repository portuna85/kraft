// usePostSubmit의 제출 흐름(사진 업로드 → 요청 → 오류 안내) 테스트. `npm run test:unit`.
//
// 이 모듈은 Vite 별칭(@core/@ui)을 import하므로 resolve 훅으로 같은 경로에 연결한 뒤 동적 import 한다
// (useImageUpload.test.js와 같은 방식).
import { test, before, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
import path from 'node:path';

const appRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../main/resources/static/js/app');

/** @type {typeof import('./usePostSubmit.js').usePostSubmit} */
let usePostSubmit;
/** @type {typeof import('vue').ref} */
let ref;

/** flash.js가 그리는 요소를 흉내 낸다 — showError가 남긴 문구를 읽기 위해서다. */
const elements = {};
function resetElements() {
    elements.flash = { hidden: true, classList: { toggle() {}, add() {}, remove() {} }, setAttribute() {}, focus() {}, scrollIntoView() {} };
    elements['flash-text'] = { textContent: '' };
}
const flashText = () => elements['flash-text'].textContent;

before(async () => {
    await import('vue');
    globalThis.document ??= /** @type {any} */ ({});
    Object.assign(globalThis.document, {
        querySelector: () => null,
        querySelectorAll: () => [],
        getElementById: (id) => elements[id] ?? null,
    });
    registerHooks({
        resolve(specifier, context, nextResolve) {
            if (specifier.startsWith('@core/') || specifier.startsWith('@ui/')) {
                const [alias, rest] = [specifier.slice(1, specifier.indexOf('/')), specifier.slice(specifier.indexOf('/') + 1)];
                return nextResolve(pathToFileURL(path.join(appRoot, alias, rest)).href, context);
            }
            return nextResolve(specifier, context);
        },
    });
    ({ ref } = await import('vue'));
    ({ usePostSubmit } = await import('./usePostSubmit.js'));
});

beforeEach(resetElements);

const NO_PICTURE = () => ({ picture: null, pictureWidth: null, pictureHeight: null });

function setup({ hasFile = false, resolveUrl = async () => 'https://img/1.png', applyFieldErrors = async () => false } = {}) {
    const saving = ref(false);
    const progressText = ref(/** @type {string | null} */ (null));
    const picture = {
        hasFile: ref(hasFile),
        resolveUrl,
        uploadedWidth: ref(640),
        uploadedHeight: ref(480),
    };
    const { submit } = usePostSubmit({ saving, progressText, picture, applyFieldErrors, fieldRefs: {}, verb: '등록' });
    return { saving, progressText, submit };
}

test('사진 없이 보내면 fallbackPicture 값으로 요청하고 성공 처리를 부른다', async () => {
    const { submit, saving } = setup();
    const sent = [];
    let result = null;

    await submit({
        fallbackPicture: NO_PICTURE,
        send: async (fields) => { sent.push(fields); return 42; },
        onSuccess: (value) => { result = value; },
    });

    assert.deepEqual(sent, [{ picture: null, pictureWidth: null, pictureHeight: null }]);
    assert.equal(result, 42);
    // 성공하면 페이지를 떠나므로 저장 중 상태를 풀지 않는다(이중 제출 방지).
    assert.equal(saving.value, true);
});

test('새로 고른 파일은 업로드한 URL과 크기로 요청한다', async () => {
    const { submit } = setup({ hasFile: true });
    let body = null;

    await submit({
        fallbackPicture: NO_PICTURE,
        send: async (fields) => { body = fields; },
        onSuccess() {},
    });

    assert.deepEqual(body, { picture: 'https://img/1.png', pictureWidth: 640, pictureHeight: 480 });
});

test('파일을 골랐는데 URL이 없으면(선택이 바뀜) 요청하지 않고 다시 확인하라고 알린다', async () => {
    const { submit, saving, progressText } = setup({ hasFile: true, resolveUrl: async () => null });
    let sent = false;

    await submit({ fallbackPicture: NO_PICTURE, send: async () => { sent = true; }, onSuccess() {} });

    assert.equal(sent, false);
    assert.equal(saving.value, false);
    assert.equal(progressText.value, null);
    assert.match(flashText(), /선택한 이미지가 바뀌었습니다.*"등록"/);
});

test('업로드가 실패하면 요청하지 않고 사유를 알린다', async () => {
    const { submit, saving } = setup({ hasFile: true, resolveUrl: async () => { throw new Error('용량 초과'); } });
    let sent = false;

    await submit({ fallbackPicture: NO_PICTURE, send: async () => { sent = true; }, onSuccess() {} });

    assert.equal(sent, false);
    assert.equal(saving.value, false);
    assert.match(flashText(), /이미지 업로드에 실패했습니다/);
});

test('세션이 만료되면 임시 저장 안내를 보인다', async () => {
    const { submit, saving } = setup();

    await submit({
        fallbackPicture: NO_PICTURE,
        send: async () => { throw Object.assign(new Error('forbidden'), { kind: 'forbidden' }); },
        onSuccess() {},
    });

    assert.equal(saving.value, false);
    assert.match(flashText(), /로그인이 만료되었습니다.*임시 저장/);
});

test('필드 오류로 처리되면 별도 배너를 띄우지 않는다', async () => {
    const { submit } = setup({ applyFieldErrors: async () => true });

    await submit({
        fallbackPicture: NO_PICTURE,
        send: async () => { throw new Error('제목은 필수입니다'); },
        onSuccess() {},
    });

    assert.equal(flashText(), '');
});

test('업로드까지 끝난 뒤 요청이 실패하면 같은 이미지로 재시도할 수 있다고 안내한다', async () => {
    const { submit } = setup({ hasFile: true });

    await submit({
        fallbackPicture: NO_PICTURE,
        send: async () => { throw new Error('서버 오류'); },
        onSuccess() {},
    });

    assert.match(flashText(), /게시글 등록에 실패했습니다.*이미지는 이미 업로드되어 있으니.*"등록"/);
});

test('이미 저장 중이면 아무것도 하지 않는다', async () => {
    const { submit, saving } = setup();
    saving.value = true;
    let sent = false;

    await submit({ fallbackPicture: NO_PICTURE, send: async () => { sent = true; }, onSuccess() {} });

    assert.equal(sent, false);
});
