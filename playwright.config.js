import { defineConfig, devices } from '@playwright/test';

const PORT = 8081;
const BASE_URL = `http://127.0.0.1:${PORT}`;

/**
 * Kraft의 브라우저 검증 설정.
 *
 * 앱은 e2e 프로파일(H2 인메모리 + 가짜 메일 발송기)로 띄운다 — Docker도 SMTP도 필요 없다.
 * 설정과 시드 코드는 src/e2e에 있고 운영 JAR에는 포함되지 않는다.
 *
 * bootRun이 아니라 미리 만든 JAR을 실행하는 이유:
 *  - gradlew / gradlew.bat 분기가 필요 없어 OS를 가리지 않는다
 *  - 운영 앱 클래스에 E2E 전용 시드·메일 기록기·H2만 더한 JAR을 검증한다
 *  - Playwright가 프로세스를 깔끔하게 종료할 수 있다(Gradle 데몬은 그렇지 않다)
 * 실행 전 ./gradlew bootE2eJar 로 JAR을 만들어 두어야 한다.
 */
export default defineConfig({
    testDir: './e2e',

    // 인메모리 H2 하나를 모든 테스트가 공유한다. 병렬로 돌리면 서로의 데이터를 본다.
    // 각 스펙이 고유한 제목으로 자기 데이터를 만들되, 전체 개수에는 절대 의존하지 않는다.
    fullyParallel: false,
    workers: 1,

    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
    reporter: process.env.CI ? [['html', { open: 'never' }], ['github']] : 'list',

    use: {
        baseURL: BASE_URL,
        locale: 'ko-KR',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
    },

    projects: [
        {
            name: 'setup',
            testMatch: /auth\.setup\.js/,
            use: { ...devices['Desktop Chrome'], channel: 'chromium' },
        },
        {
            // channel: 'chromium'은 별도의 headless shell 대신 전체 Chromium 빌드를 쓴다.
            // 내려받을 것이 하나 줄고, 실제 사용자가 쓰는 브라우저에 더 가깝다.
            name: 'chromium',
            use: { ...devices['Desktop Chrome'], channel: 'chromium' },
            dependencies: ['setup'],
        },
        {
            // 주의: Playwright의 WebKit은 최신 빌드라 이 프로젝트가 기준으로 삼는
            // Safari 15의 API 공백(requestSubmit 등)은 잡아내지 못한다. 레이아웃·동작
            // 차이를 보는 용도이며, 지원 범위 보증은 코드 리뷰가 책임진다.
            name: 'mobile-webkit',
            use: { ...devices['iPhone 13'] },
            dependencies: ['setup'],
        },
    ],

    webServer: {
        command: 'java -jar build/libs/kraft-0.0.1-SNAPSHOT-e2e.jar --spring.profiles.active=e2e',
        // /login은 permitAll이면서 Thymeleaf 렌더링과 CSRF 메타 생성을 모두 거친다.
        // "포트가 열렸다"가 아니라 "실제로 페이지를 준다"를 기다리게 된다.
        // (actuator는 커밋 1080614에서 의도적으로 제거했으므로 쓰지 않는다.)
        url: `${BASE_URL}/login`,
        // 매 실행마다 새 JAR과 깨끗한 시드 DB로 시작해 이전 서버의 코드·데이터가 섞이지 않게 한다.
        reuseExistingServer: false,
        timeout: 120_000,
        stdout: 'pipe',
        stderr: 'pipe',
    },
});
