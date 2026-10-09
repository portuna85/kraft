import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// package.json이 "type": "module"이라 __dirname이 없다 — import.meta.url에서 구한다.
const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Vue 없이 동작하는 작은 공용 모듈. 모든 페이지의 main.js(plain JS 진입점)와 Vue 아일랜드가 함께 쓰므로 한 청크("core")에 묶는다 — Vue 런타임과 같은 청크에 두면 Vue를
 * 쓰지 않는 페이지도 Vue를 내려받는다. 새 공용 모듈이 별도 청크로 떨어지면 check-preload.mjs가 preload 누락으로 실패시키니, 그때 여기에 추가하거나 템플릿에 preload를 더한다.
 */
const CORE_MODULES = [
    '/core/http.js',
    '/core/httpResponse.js',
    '/core/etag.js',
    '/core/dom.js',
    '/core/constants.js',
    '/core/storage.js',
    '/core/datetime.js',
    '/core/bootstrap-ui.js',
    '/ui/flash.js',
    '/ui/toast.js',
];

/** Vue를 import하는 작은 공용 모듈 — 따로 두면 진입 스크립트를 파싱한 뒤에야 발견되는 2단 워터폴이 되므로 Vue 런타임과 같은 "runtime" 청크에 묶는다. */
const SMALL_VUE_MODULES = [
    '/shared/useFieldErrors.js',
    '/shared/usePasswordConfirm.js',
    '/shared/mountIsland.js',
];

/**
 * Vue 아일랜드 빌드 설정. "Gradle 빌드는 npm 없이 항상 동작한다"는 원칙(CSS를 build:css로 미리 컴파일해 커밋하는 것과 같다)에 따라 산출물을
 * src/main/resources/static/js/vue-dist/에 커밋하고, check:vue(package.json)가 재빌드 후 git diff로 drift만 검증한다. 소스맵을 커밋하지 않고 해시 없는 고정
 * 파일명을 쓰는 것은 산출물 diff를 리뷰 가능하게 두기 위해서다.
 */
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@core': resolve(__dirname, '../main/resources/static/js/app/core'),
            '@ui': resolve(__dirname, '../main/resources/static/js/app/ui'),
        },
    },
    // 모든 컴포넌트가 <script setup>만 쓰므로 Options API 지원 코드가 런타임 청크에 번들되지 않게 끄고, 프로덕션 개발자 도구 연결과 하이드레이션 불일치 상세 정보도 끈다
    // (서버가 HTML을 하이드레이션하지 않고 각 아일랜드를 처음부터 클라이언트에서 마운트한다).
    define: {
        __VUE_OPTIONS_API__: 'false',
        __VUE_PROD_DEVTOOLS__: 'false',
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false',
    },
    build: {
        outDir: resolve(__dirname, '../main/resources/static/js/vue-dist'),
        emptyOutDir: true,
        sourcemap: false,
        // 지원 하한(iOS 15)을 실제 빌드 설정에 연결한다. 이보다 새 문법은 변환하거나 경고하지만 런타임 API(Array.prototype.at() 등)는 폴리필하지 않으므로 소스에서 직접 걷어냈다.
        target: 'ios15',
        rollupOptions: {
            input: {
                // 화면별 마운트 진입점을 여기 추가한다(각 페이지는 필요한 번들만 로드한다).
                comments: resolve(__dirname, 'comments/mount.js'),
                recommend: resolve(__dirname, 'recommend/mount.js'),
                'post-edit': resolve(__dirname, 'post-edit/mount.js'),
                'post-save': resolve(__dirname, 'post-save/mount.js'),
                signup: resolve(__dirname, 'signup/mount.js'),
                'forgot-password': resolve(__dirname, 'forgot-password/mount.js'),
                'password-reset': resolve(__dirname, 'password-reset/mount.js'),
                // Modal·Toast만 담아 전역 bootstrap으로 노출하는 번들(Vue 아일랜드가 아니라 모든 페이지 공통, footer가 모듈 스크립트로 불러온다).
                bootstrap: resolve(__dirname, 'bootstrap/entry.js'),
                // 모든 페이지의 plain JS 진입점. 같은 빌드로 묶어 core/·ui/ 모듈이 아일랜드 번들과 청크를 공유하고 압축된다(features/*는 동적 import라 해당 페이지에서만 받는다).
                // 주석 달린 원본은 static/js/app에 있고 jar에는 들어가지 않는다(build.gradle.kts).
                main: resolve(__dirname, '../main/resources/static/js/app/main.js'),
            },
            output: {
                entryFileNames: '[name].js',
                // 글 작성·수정 화면이 함께 쓰는 공용 청크에 고정 이름을 준다 — Rollup이 붙이는 이름이 모듈 그래프에 따라 바뀌면 템플릿의 modulepreload가 조용히 어긋난다(check-preload.mjs가 어긋나면 빌드를 실패시킨다).
                chunkFileNames: (chunk) => (
                    chunk.moduleIds.some((id) => id.endsWith('/shared/DraftRestoreBanner.vue'))
                        ? 'chunks/post-shared.js'
                        : 'chunks/[name].js'
                ),
                assetFileNames: '[name][extname]',
                // 공유 Vue 런타임 + 거의 모든 페이지가 쓰는 작은 Vue 모듈(폼 오류 composable 등)을 "runtime"이라는 고정 이름 청크로 묶는다. 자동 분리에 맡기면 이름이
                // 모듈 그래프에 따라 바뀌어 템플릿에 하드코딩한 modulepreload가 조용히 어긋나고(check-preload.mjs가 실패시킨다), 따로 두면 1KB 청크마다 진입 스크립트를
                // 파싱한 뒤에야 발견되는 왕복이 된다. Vue를 쓰지 않는 작은 공용 모듈(core/·ui/)은 "core" 청크로 따로 묶는다 — runtime에 넣으면 main.js가 모든 페이지에서 Vue를 끌어온다.
                manualChunks(id) {
                    if (id.includes('/node_modules/vue/') || id.includes('/node_modules/@vue/')) {
                        return 'runtime';
                    }
                    if (SMALL_VUE_MODULES.some((suffix) => id.endsWith(suffix))) {
                        return 'runtime';
                    }
                    if (CORE_MODULES.some((suffix) => id.endsWith(suffix))) {
                        return 'core';
                    }
                    return undefined;
                },
            },
        },
    },
});
