import js from '@eslint/js';
import globals from 'globals';
import vuePlugin from 'eslint-plugin-vue';

/**
 * 정적 검사 설정.
 *
 * IDE에만 뜨고 어디에도 기록되지 않던 경고들(`Unresolved variable or type bootstrap`,
 * `'var' is used instead of 'let' or 'const'` 등)을 규칙으로 고정해, 고쳐야 할 목록이
 * 눈에 보이고 다시 쌓이지 않게 한다.
 */
export default [
    {
        // vue-dist는 Vite가 만든 산출물, vendor는 그대로 복사해 온 외부 라이브러리라
        // (개선 보고서 F08) 둘 다 사람이 손대지 않는다 — 검사 대상에서 뺀다.
        ignores: [
            'node_modules/**',
            'build/**',
            'playwright-report/**',
            'test-results/**',
            'src/main/resources/static/js/vue-dist/**',
            'src/main/resources/static/js/vendor/**',
        ],
    },

    // 브라우저에서 도는 앱 코드.
    {
        files: ['src/main/resources/static/js/**/*.js'],
        ...js.configs.recommended,
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'module',
            globals: {
                ...globals.browser,
                // Bootstrap은 CDN의 classic script가 올려주는 전역이다. ESM으로 import하지 않는
                // 이유는 SRI가 깨지고 모듈 평가 시점에 네트워크 왕복이 생기기 때문이다.
                // 여기 선언해 두면 "Unresolved variable or type bootstrap"이 사라진다.
                bootstrap: 'readonly',
            },
        },
        rules: {
            ...js.configs.recommended.rules,
            'no-var': 'error',
            'prefer-const': 'error',
            eqeqeq: ['error', 'smart'],
            'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
        },
    },

    // Playwright 설정·스펙(Node에서 돈다).
    {
        files: ['e2e/**/*.js', 'playwright.config.js', 'eslint.config.js'],
        ...js.configs.recommended,
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'module',
            // 스펙 파일은 Node에서 돌지만 page.evaluate() 콜백 안은 브라우저 컨텍스트라
            // document·window를 정당하게 쓴다. 둘 다 허용한다.
            globals: { ...globals.node, ...globals.browser, bootstrap: 'readonly' },
        },
        rules: {
            ...js.configs.recommended.rules,
            'no-var': 'error',
            'prefer-const': 'error',
            eqeqeq: ['error', 'smart'],
        },
    },

    // Vue 아일랜드 소스. vite.config.js는 Node에서, 나머지(.vue/composable)는 브라우저에서 돈다.
    // vue-dist는 Vite가 만든 산출물(사람이 손대지 않음)이라 정적 검사 대상에서 제외한다.
    {
        files: ['src/vue/**/*.js'],
        ignores: ['src/vue/vite.config.js'],
        ...js.configs.recommended,
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'module',
            globals: { ...globals.browser },
        },
        rules: {
            ...js.configs.recommended.rules,
            'no-var': 'error',
            'prefer-const': 'error',
            eqeqeq: ['error', 'smart'],
            'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
        },
    },
    {
        files: ['src/vue/vite.config.js'],
        ...js.configs.recommended,
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'module',
            globals: { ...globals.node },
        },
    },
    // eslint-plugin-vue의 flat/recommended는 여러 config 조각(파서 설정·규칙)으로 이뤄진
    // 배열이다. 프로젝트 전역이 아니라 src/vue/**/*.vue에만 적용되도록 각 조각에 files를 씌운다.
    ...vuePlugin.configs['flat/recommended'].map((config) => ({
        ...config,
        files: ['src/vue/**/*.vue'],
    })),
    {
        files: ['src/vue/**/*.vue'],
        languageOptions: {
            globals: {
                ...globals.browser,
                // <script setup> 컴파일러 매크로. 실제 함수가 아니라 컴파일 타임에 사라지는
                // 문법이라 import 없이 쓰지만, no-undef 입장에서는 선언되지 않은 전역이다.
                defineProps: 'readonly',
                defineEmits: 'readonly',
                defineExpose: 'readonly',
                defineOptions: 'readonly',
                defineSlots: 'readonly',
                defineModel: 'readonly',
                withDefaults: 'readonly',
            },
        },
        rules: {
            // eslint-plugin-vue의 flat/recommended는 vue/* 규칙만 준다 — <script> 안의 순수 JS
            // 로직(미정의 변수, 미사용 변수)은 core 규칙이 없으면 검사되지 않는다. .js 블록과
            // 같은 규칙 세트를 맞춘다(개선 보고서 F08).
            ...js.configs.recommended.rules,
            'no-var': 'error',
            'prefer-const': 'error',
            eqeqeq: ['error', 'smart'],
            'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
            // 컴포넌트가 하나뿐인 마운트 대상(App.vue류)까지 다단어 이름을 강제할 필요는 없다.
            'vue/multi-word-component-names': 'off',
        },
    },
];
