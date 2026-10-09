import js from '@eslint/js';
import globals from 'globals';
import vuePlugin from 'eslint-plugin-vue';
import compat from 'eslint-plugin-compat';

/**
 * 정적 검사 설정. IDE에만 뜨고 기록되지 않던 경고(`Unresolved variable or type bootstrap`, `'var' is used instead of 'let' or 'const'` 등)를 규칙으로 고정해, 고쳐야 할 목록이 보이고 다시 쌓이지 않게 한다.
 */
export default [
    {
        // vue-dist는 Vite가 만든 산출물이라 사람이 손대지 않으므로 검사 대상에서 뺀다.
        ignores: [
            'node_modules/**',
            'build/**',
            'playwright-report/**',
            'test-results/**',
            'src/main/resources/static/js/vue-dist/**',
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
                // Bootstrap은 src/vue/bootstrap/entry.js가 올려주는 전역이다(footer.html 참고, Modal·Toast만). plain JS는 직접 import하지 않고 이 전역을 쓰며, 인스턴스는 한 곳에서만 만든다.
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
    // 이 앱이 주장하는 지원 하한(iOS 15, package.json의 browserslist)을 JS에도 강제한다. vite.config.js의 target: 'ios15'는 문법만 낮추고 새 런타임 API(CommentsApp.vue의 .at() 회피 등)는 걸러 주지 못한다.
    // languageOptions는 위 블록의 bootstrap 전역 선언과 합쳐지도록 여기서는 plugins·rules만 추가한다.
    {
        files: ['src/main/resources/static/js/**/*.js', 'src/vue/**/*.{js,vue}'],
        plugins: { compat },
        rules: { 'compat/compat': 'error' },
    },

    // Playwright 설정·스펙과 빌드 검사 스크립트(둘 다 Node에서 돈다).
    {
        files: ['e2e/**/*.js', 'playwright.config.js', 'eslint.config.js', 'scripts/**/*.mjs'],
        ...js.configs.recommended,
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'module',
            // 스펙 파일은 Node에서 돌지만 page.evaluate() 콜백 안은 브라우저 컨텍스트라 document·window를 쓴다. 둘 다 허용한다.
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
    // eslint-plugin-vue의 flat/recommended는 여러 config 조각(파서 설정·규칙)의 배열이다. 프로젝트 전역이 아니라 src/vue/**/*.vue에만 적용되도록 각 조각에 files를 씌운다.
    ...vuePlugin.configs['flat/recommended'].map((config) => ({
        ...config,
        files: ['src/vue/**/*.vue'],
    })),
    {
        files: ['src/vue/**/*.vue'],
        languageOptions: {
            globals: {
                ...globals.browser,
                // <script setup> 컴파일러 매크로. 컴파일 타임에 사라지는 문법이라 import 없이 쓰지만 no-undef 입장에서는 선언되지 않은 전역이다.
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
            // flat/recommended는 vue/* 규칙만 주므로, <script> 안의 순수 JS 로직(미정의·미사용 변수)을 보려면 core 규칙이 필요하다. .js 블록과 같은 규칙 세트를 맞춘다.
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
