import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// package.json이 "type": "module"이라 __dirname이 없다 — import.meta.url에서 구한다.
const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * 대부분의 페이지가 쓰는 작은 공용 모듈. 모두 runtime 청크에 묶인다(FE-04, manualChunks 참고).
 * 새 공용 모듈이 생겨 별도 청크로 떨어지면 check-preload.mjs가 그 청크가 preload되지 않았다고
 * 실패시키므로, 그때 여기에 추가하거나 템플릿에 preload를 더한다.
 */
const SMALL_SHARED_MODULES = [
    '/core/http.js',
    '/core/httpResponse.js',
    '/core/dom.js',
    '/core/constants.js',
    '/core/bootstrap-ui.js',
    '/ui/flash.js',
    '/ui/toast.js',
    '/shared/useFieldErrors.js',
    '/shared/usePasswordConfirm.js',
    '/shared/mountIsland.js',
];

/**
 * Vue 아일랜드 빌드 설정.
 *
 * 이 저장소는 "Gradle 빌드는 npm 없이 항상 동작한다"는 원칙을 갖고 있다(CSS를 build:css로
 * 미리 컴파일해 커밋하는 방식과 동일). Vue도 같은 원칙을 따른다 — 여기서 만든 산출물을
 * src/main/resources/static/js/vue-dist/에 커밋하고, Gradle·JAR·CI는 Node.js를 몰라도 된다.
 * check:vue(package.json)가 재빌드 후 git diff로 drift만 검증한다.
 *
 * 소스맵을 커밋하지 않고(sourcemap: false) 해시 없는 고정 파일명(entryFileNames)을 쓰는 것은
 * 산출물 diff를 리뷰 가능한 수준으로 유지하기 위해서다 — CSS 산출물과 같은 이유.
 */
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@core': resolve(__dirname, '../main/resources/static/js/app/core'),
            '@ui': resolve(__dirname, '../main/resources/static/js/app/ui'),
        },
    },
    // 모든 컴포넌트가 <script setup>만 쓰고 Options API(data()/methods/mixins 등)를 쓰지
    // 않는다(FE-06) — 그런데 plugin-vue는 이 플래그가 없으면 __VUE_OPTIONS_API__ 기본값을
    // true로 두어, 런타임 청크에 한 번도 안 쓰는 Options API 지원 코드가 그대로 번들된다.
    // 프로덕션 개발자 도구 연결·하이드레이션 불일치 상세 정보도 함께 끈다(둘 다 운영 빌드에서
    // 쓰지 않는다 — 서버가 HTML을 하이드레이션하지 않고 각 아일랜드를 처음부터 클라이언트에서
    // 마운트한다).
    define: {
        __VUE_OPTIONS_API__: 'false',
        __VUE_PROD_DEVTOOLS__: 'false',
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false',
    },
    build: {
        outDir: resolve(__dirname, '../main/resources/static/js/vue-dist'),
        emptyOutDir: true,
        sourcemap: false,
        // 이 프로젝트가 주장하는 지원 하한(iOS 15)을 실제 빌드 설정에 연결한다 — 이전에는
        // 이 값이 없어 "iOS 15 지원"이 코드 어디에도 강제되지 않았다(개선 보고서 F14).
        // esbuild가 이 기준보다 새 문법을 만나면 변환하거나(가능한 경우) 경고한다. 다만
        // 이 값은 문법 변환만 다루고 런타임 API(Array.prototype.at() 등)는 폴리필하지
        // 않는다 — 그런 API는 소스에서 직접 걷어냈다.
        target: 'ios15',
        rollupOptions: {
            input: {
                // 화면별 마운트 진입점을 여기 추가한다. 각 페이지는 필요한 번들만 로드한다
                // (main.js처럼 전역으로 싣지 않는다).
                comments: resolve(__dirname, 'comments/mount.js'),
                recommend: resolve(__dirname, 'recommend/mount.js'),
                'post-edit': resolve(__dirname, 'post-edit/mount.js'),
                'post-save': resolve(__dirname, 'post-save/mount.js'),
                signup: resolve(__dirname, 'signup/mount.js'),
                'forgot-password': resolve(__dirname, 'forgot-password/mount.js'),
                'password-reset': resolve(__dirname, 'password-reset/mount.js'),
                // Modal·Toast만 담아 전역 bootstrap으로 노출하는 번들(FE-06). 템플릿 footer가
                // 모듈 스크립트로 불러온다 — Vue 아일랜드가 아니라 모든 페이지 공통이다.
                bootstrap: resolve(__dirname, 'bootstrap/entry.js'),
            },
            output: {
                entryFileNames: '[name].js',
                // 글 작성·수정 화면이 함께 쓰는 공용 청크는 Rollup이 첫 모듈 이름(DraftRestoreBanner)을
                // 우연히 붙인다 — 이름이 모듈 그래프에 따라 바뀌면 템플릿의 modulepreload가 조용히
                // 어긋나므로(FE-33) 고정 이름을 준다. check-preload.mjs가 어긋나면 빌드를 실패시킨다.
                chunkFileNames: (chunk) => (
                    chunk.moduleIds.some((id) => id.endsWith('/shared/DraftRestoreBanner.vue'))
                        ? 'chunks/post-shared.js'
                        : 'chunks/[name].js'
                ),
                assetFileNames: '[name][extname]',
                // 모든 화면이 공유하는 Vue 런타임 + core/http.js를 "runtime"이라는 고정 이름의
                // 청크로 명시적으로 묶는다. Rollup의 자동 청크 분리에 맡기면 이 공유 청크의
                // 이름이 모듈 그래프가 바뀔 때마다 달라질 수 있는데(예: 지금은 http.js로
                // 불린다), 템플릿의 modulepreload가 그 이름을 하드코딩하고 있어 이름이
                // 바뀌면 조용히 어긋난다(개선 보고서 F07). 이름을 고정해 그 경로 자체를
                // 없애고, scripts/check-preload.mjs가 그래도 어긋나면 빌드를 실패시킨다.
                //
                // FE-04: 거의 모든 페이지가 쓰는 작은 모듈(상수·flash·toast·폼 오류 composable 등)도
                // 같은 청크에 넣는다. 따로 두면 각각이 진입 스크립트를 파싱한 뒤에야 발견되는
                // 2단 워터폴이 되고, 1KB짜리 청크 하나하나가 왕복 한 번이다. 합쳐서 늘어나는 양은
                // 약 5KB로, runtime 하나를 미리 받는(modulepreload) 이득이 더 크다.
                manualChunks(id) {
                    if (id.includes('/node_modules/vue/') || id.includes('/node_modules/@vue/')) {
                        return 'runtime';
                    }
                    if (SMALL_SHARED_MODULES.some((suffix) => id.endsWith(suffix))) {
                        return 'runtime';
                    }
                    return undefined;
                },
            },
        },
    },
});
