// useImageUpload의 축소 중 상태(processing)와 선택 경합 테스트. `npm run test:unit`.
//
// 이 모듈은 Vite 별칭(@core/@ui)을 import하므로 node가 그대로는 풀지 못한다. 테스트에서만
// resolve 훅으로 같은 경로에 연결하고, 훅을 등록한 뒤에 동적 import 한다(정적 import는
// 호이스팅되어 훅보다 먼저 실행된다).
import { test, before, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
import path from 'node:path';

const appRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../main/resources/static/js/app');

/** @type {typeof import('./useImageUpload.js').useImageUpload} */
let useImageUpload;

before(async () => {
    // vue(runtime-dom)는 document가 있으면 진짜 DOM이라고 가정하고 createElement를 부른다.
    // 그래서 vue를 먼저 로드(캐시)한 뒤에 스텁을 둔다.
    await import('vue');
    // core/http.js가 로드 시점에 CSRF meta를 document에서 읽는다 — 빈 문서면 충분하다.
    globalThis.document ??= /** @type {any} */ ({ querySelector: () => null, querySelectorAll: () => [], getElementById: () => null });
    registerHooks({
        resolve(specifier, context, nextResolve) {
            if (specifier.startsWith('@core/') || specifier.startsWith('@ui/')) {
                const [alias, rest] = [specifier.slice(1, specifier.indexOf('/')), specifier.slice(specifier.indexOf('/') + 1)];
                return nextResolve(pathToFileURL(path.join(appRoot, alias, rest)).href, context);
            }
            return nextResolve(specifier, context);
        },
    });
    ({ useImageUpload } = await import('./useImageUpload.js'));
});

const originalCreateImageBitmap = globalThis.createImageBitmap;
afterEach(() => {
    globalThis.createImageBitmap = originalCreateImageBitmap;
});

/**
 * 호출자가 직접 풀어 줄 때까지 끝나지 않는 createImageBitmap. 작은 이미지(scale >= 1)를
 * 돌려주므로 풀리면 원본 파일이 그대로 결과가 된다.
 */
function controllableBitmap() {
    /** @type {Array<() => void>} */
    const releases = [];
    globalThis.createImageBitmap = () => new Promise((resolve) => {
        releases.push(() => resolve({ width: 10, height: 10, close() {} }));
    });
    return releases;
}

const png = (name) => new File([new Uint8Array(8)], name, { type: 'image/png' });

test('축소가 끝나기 전에는 processing이 true이고 file은 아직 비어 있다', async () => {
    const releases = controllableBitmap();
    const picture = useImageUpload();

    const pending = picture.onFileSelected(png('a.png'));

    assert.equal(picture.processing.value, true);
    assert.equal(picture.hasFile.value, false);

    releases[0]();
    await pending;

    assert.equal(picture.processing.value, false);
    assert.equal(picture.hasFile.value, true);
});

test('빠르게 다른 파일을 고르면 먼저 시작한 축소가 끝나도 processing은 최신 선택이 끝날 때까지 유지된다', async () => {
    const releases = controllableBitmap();
    const picture = useImageUpload();

    const first = picture.onFileSelected(png('first.png'));
    const second = picture.onFileSelected(png('second.png'));

    releases[0]();
    await first;
    assert.equal(picture.processing.value, true, '첫 번째 축소가 끝나도 두 번째가 남아 있다');
    assert.equal(picture.hasFile.value, false);

    releases[1]();
    await second;
    assert.equal(picture.processing.value, false);
    assert.equal(picture.file.value?.name, 'second.png');
});

test('축소 중에 선택을 해제하면 늦게 끝난 축소가 file을 되살리지 못한다', async () => {
    const releases = controllableBitmap();
    const picture = useImageUpload();

    const pending = picture.onFileSelected(png('a.png'));
    await picture.onFileSelected(null);
    assert.equal(picture.processing.value, false);

    releases[0]();
    await pending;

    assert.equal(picture.hasFile.value, false);
    assert.equal(picture.processing.value, false);
});
