import js from '@eslint/js';
import globals from 'globals';

/**
 * 정적 검사 설정.
 *
 * IDE에만 뜨고 어디에도 기록되지 않던 경고들(`Unresolved variable or type bootstrap`,
 * `'var' is used instead of 'let' or 'const'` 등)을 규칙으로 고정해, 고쳐야 할 목록이
 * 눈에 보이고 다시 쌓이지 않게 한다.
 */
export default [
    {
        ignores: ['node_modules/**', 'build/**', 'playwright-report/**', 'test-results/**'],
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
            globals: { ...globals.node, ...globals.browser },
        },
        rules: {
            ...js.configs.recommended.rules,
            'no-var': 'error',
            'prefer-const': 'error',
            eqeqeq: ['error', 'smart'],
        },
    },
];
