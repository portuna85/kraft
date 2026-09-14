import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// package.json이 "type": "module"이라 __dirname이 없다 — import.meta.url에서 구한다.
const __dirname = dirname(fileURLToPath(import.meta.url));

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
        },
    },
    build: {
        outDir: resolve(__dirname, '../main/resources/static/js/vue-dist'),
        emptyOutDir: true,
        sourcemap: false,
        rollupOptions: {
            input: {
                // 화면별 마운트 진입점을 여기 추가한다. 각 페이지는 필요한 번들만 로드한다
                // (main.js처럼 전역으로 싣지 않는다).
                comments: resolve(__dirname, 'comments/mount.js'),
            },
            output: {
                entryFileNames: '[name].js',
                chunkFileNames: 'chunks/[name].js',
                assetFileNames: '[name][extname]',
            },
        },
    },
});
