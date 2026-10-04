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

    /**
     * 시각 회귀 스펙(visual*.spec.js)은 기준 이미지가 만든 환경의 폰트 렌더링에 묶인다(OPS-05).
     * 기준선은 CI와 같은 Docker 이미지(mcr.microsoft.com/playwright, Linux)에서 만든
     * `-chromium-linux.png`이고, 일반 e2e 샤드는 이 스펙을 건너뛴다(CI=1). 별도 `visual` 잡이
     * VISUAL=1로 이 스펙만 돌린다.
     *
     * 예전에는 기준선을 Windows 로컬에서 만들어 `-win32.png`로 두고 CI는 건너뛰었는데, 아무도
     * 로컬에서 갱신하지 않아 기준선이 이미 어긋난 채로 무엇도 잡지 못했다.
     *
     * 화면을 일부러 바꿨다면 기준선을 CI와 같은 환경에서 다시 만든다:
     *   npm run visual:update   (Docker 필요, build/libs/kraft-e2e.jar 필요 → ./gradlew bootE2eJar)
     *   npm run visual:check    (갱신 없이 비교만)
     * 로컬(Windows 등)에서 chromium 프로젝트로 직접 돌리면 `-linux` 기준선과 폰트가 달라 실패한다.
     * mobile-webkit의 `-win32` 기준선은 로컬 전용 도구라 그대로 둔다.
     */
    testIgnore: process.env.CI && !process.env.VISUAL ? [/visual.*\.spec\.js/] : [],

    // 러너 하나 안에서는 H2를 공유하므로 순차 실행한다. CI의 shard는 각각 독립된
    // 서버·DB를 가지며 파일 단위로 분할한다(fullyParallel을 켜지 않는다).
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
        /*
         * mobile-webkit: Playwright의 WebKit은 최신 빌드라 이 프로젝트가 기준으로 삼는
         * Safari 15의 API 공백(requestSubmit 등)은 잡아내지 못한다. 레이아웃·동작 차이를 보는
         * 로컬 전용 도구이며, 지원 범위 보증은 코드 리뷰가 책임진다.
         *
         * CI에서는 아예 등록하지 않는다. 계정·탈퇴 같은 모달 흐름에서 실제 Safari와 달리
         * 요소가 "visible"로 안정화되지 않는 알려진 불안정성이 있고, 이것이 실패해도 코드
         * 결함이 아니라 이 WebKit 빌드의 한계라는 사실을 매번 사람이 다시 판단해야 했다.
         * .github/workflows/build.yml도 `--project=chromium`으로 이미 이렇게 돌지만, 그
         * 플래그 하나에만 의존하지 않도록 여기서도 명시한다 — CI=1로 로컬에서 전체 프로젝트를
         * 돌려도(`npx playwright test`) 같은 결과가 나와야 한다.
         */
        ...(process.env.CI ? [] : [{
            name: 'mobile-webkit',
            use: { ...devices['iPhone 13'] },
            dependencies: ['setup'],
        }]),
    ],

    webServer: {
        command: 'java -jar build/libs/kraft-e2e.jar --spring.profiles.active=e2e',
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
