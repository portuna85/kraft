package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * B-05: ASVS 5.0 L2 / OWASP API Top 10 통제-증거 매트릭스. "완료" 표시가 아니라 각 통제가
 * 가리키는 실행 가능한 테스트를 고정한다 — {@link Evidence.OfTest}는 참조된 테스트 클래스가
 * 실제로 존재하고(Class.forName 실패 시 이 테스트가 실행 실패) 참조된 메서드가 실제로
 * 존재하는지(리네임 시 이 테스트가 실행 실패) 검증한다. 대상 테스트 클래스가 대부분
 * package-private(프로젝트 관례)이라 컴파일 타임 Class&lt;?&gt; 참조 대신 FQCN 문자열 +
 * 리플렉션으로 존재를 확인한다. {@link Evidence.Gap}은 아직 실행 가능한 증거가 없는 통제를
 * 숨기지 않고 명시적으로 인정한 것 — 목록에서 조용히 빠지면 안 되므로 ENTRIES 자체가 검토
 * 대상이다.
 */
@DisplayName("ASVS/OWASP API 통제-증거 매트릭스")
class AsvsApiControlMatrixTest {

    sealed interface Evidence {
        record OfTest(String testClassName, String methodName) implements Evidence {}

        record OfConfig(String pointer) implements Evidence {}

        record Gap(String reason) implements Evidence {}
    }

    record Control(String id, String area, String asvsOrOwaspRef, Evidence evidence) {}

    private static final List<Control> ENTRIES = List.of(
            new Control("AC-01", "커뮤니티 게시글/댓글 객체 수준 인가", "ASVS 4.2.1 / OWASP API1",
                    new Evidence.OfTest("com.kraft.community.CommunityPostCommentApiTest",
                            "nonOwner_cannotUpdateOrDeletePost")),
            new Control("AC-02", "커뮤니티 댓글 객체 수준 인가", "ASVS 4.2.1 / OWASP API1",
                    new Evidence.OfTest("com.kraft.community.CommunityPostCommentApiTest", "nonOwner_cannotDeleteComment")),
            new Control("AC-03", "저장번호 객체 수준 인가(다른 토큰 소유 항목 차단)", "ASVS 4.2.1 / OWASP API1",
                    new Evidence.OfTest("com.kraft.saved.SavedNumbersServiceTest",
                            "delete_idBelongsToDifferentTokenHash_throwsNotFoundApiException")),
            new Control("AC-04", "admin 기능 수준 인가(비인증 접근 차단)", "ASVS 4.1.1 / OWASP API5",
                    new Evidence.OfTest("com.kraft.common.config.SecurityChainOrderingTest",
                            "unauthenticatedAdminPath_redirectsToAdminLogin")),
            new Control("AC-05", "ops 기능 수준 인가(토큰 필수)", "ASVS 4.1.1 / OWASP API5",
                    new Evidence.OfTest("com.kraft.OpsApiIntegrationTest", "opsSummaryRequiresTokenAndReturnsLatestRound")),
            new Control("AC-06", "Actuator 신뢰 프록시 CIDR 외 접근 차단", "ASVS 4.1.1 / OWASP API5",
                    new Evidence.OfTest("com.kraft.common.config.WebSecurityConfigTest",
                            "untrustedRemoteAddr_isForbiddenFromPrometheus")),
            new Control("RL-01", "저장번호 클라이언트당 생성 한도", "ASVS 11.1.4 / OWASP API4",
                    new Evidence.OfTest("com.kraft.saved.SavedNumbersServiceTest", "save_overLimit_throwsConflictApiException")),
            new Control("RL-02", "공개 API IP 기준 rate limit", "ASVS 11.1.4 / OWASP API4",
                    new Evidence.OfTest("com.kraft.common.web.PublicRateLimitFilterTest",
                            "rateLimitExceeded_returnsRetryAfterHeader")),
            new Control("RL-03", "커뮤니티 쓰기 사용자 기준 rate limit", "ASVS 11.1.4 / OWASP API4",
                    new Evidence.OfTest("com.kraft.community.auth.CommunityWriteRateLimitFilterTest",
                            "writeRateLimitExceeded_returnsApiErrorResponseJson")),
            new Control("RL-04", "요청 본문/헤더 크기 제한", "ASVS 11.1.4 / OWASP API4",
                    new Evidence.Gap("Spring Boot 기본값(server.tomcat.max-*)에 의존 — 명시 설정도 "
                            + "회귀 테스트도 없음. 대량 페이로드 DoS 내성을 검증하려면 별도 설정+테스트 필요.")),
            new Control("AUTH-01", "브루트포스 방어(IP+계정 이중 잠금)", "ASVS 2.2.1 / OWASP API2",
                    new Evidence.OfTest("com.kraft.admin.AdminLoginAttemptServiceTest",
                            "isLockedOut_distributedFailuresAcrossManyIps_triggersAccountLockout")),
            new Control("AUTH-02", "잠금 상태 요청 즉시 거부", "ASVS 2.2.1 / OWASP API2",
                    new Evidence.OfTest("com.kraft.admin.AdminLockoutFilterTest",
                            "doFilterInternal_lockedOut_rejectsAndIncrementsCounter")),
            new Control("CSRF-01", "커뮤니티 쓰기 CSRF 토큰 필수", "ASVS 4.2.2 / OWASP API8",
                    new Evidence.OfTest("com.kraft.community.CommunityPostCommentApiTest",
                            "writeRequestWithoutCsrfToken_isForbidden")),
            new Control("CSRF-02", "커뮤니티 CSRF 쿠키 발급", "ASVS 4.2.2 / OWASP API8",
                    new Evidence.OfTest("com.kraft.community.auth.CommunityCsrfCookieTest",
                            "sessionCheck_issuesXsrfCookie")),
            new Control("SESS-01", "운영 세션 쿠키 속성 강화(secure/httpOnly/sameSite)", "ASVS 3.4.1 / OWASP API8",
                    new Evidence.OfTest("com.kraft.common.config.ProdSessionCookieConfigTest",
                            "applicationProdYml_hardensSessionCookieAttributes")),
            new Control("SESS-02", "세션 고정 공격 방어(로그인 시 세션 ID 교체)", "ASVS 3.2.1 / OWASP API2",
                    new Evidence.Gap("Spring Security 기본 changeSessionId 동작에 의존 — 실제 로그인 흐름에서 "
                            + "세션 ID가 바뀌는지 확인하는 테스트 없음.")),
            new Control("LOGOUT-01", "admin 로그아웃 종단 흐름", "ASVS 3.6.1",
                    new Evidence.Gap("AdminSecurityConfig가 invalidateHttpSession(true)를 설정하지만, "
                            + "/admin/logout 실제 호출 후 세션 무효화·쿠키 삭제를 검증하는 e2e/MockMvc 테스트 없음.")),
            new Control("OAUTH-01", "OAuth 속성 정규화·검증(과도한 길이·누락 필드 거부)", "ASVS 3.7.1 / OWASP API2",
                    new Evidence.OfTest("com.kraft.community.auth.CommunityOAuthAttributesTest",
                            "rejectsOverlongGoogleAttributes")),
            new Control("OAUTH-02", "동시 최초 로그인 시 계정 중복 생성 방지", "ASVS 11.1.4",
                    new Evidence.OfTest("com.kraft.community.user.CommunityUserRepositoryConcurrencyTest",
                            "concurrentSignupWithSameProviderId_createsExactlyOneRow")),
            new Control("OAUTH-03", "다른 provider(Google→Naver 등)로 같은 이메일 재로그인 시 계정 연결·병합 정책", "ASVS 3.7.1",
                    new Evidence.Gap("cross-provider 계정 연결 자체가 기능으로 존재하는지 불명확 — 의도된 "
                            + "동작(별도 계정 vs 병합)을 확정하고 그에 맞는 테스트 필요.")),
            new Control("SSRF-01", "외부 로또 fetch/revalidate 콜백 URL이 요청 입력에서 파생되지 않음", "ASVS 5.2.7 / OWASP API7",
                    new Evidence.OfConfig("ExternalLottoProperties.urlTemplate/RevalidateProperties.webUrl은 "
                            + "운영자가 환경변수로만 설정하는 상수이지 사용자 요청에서 파생되지 않는다 — "
                            + "구조적으로 SSRF 표면이 없다(호스트 allowlist 코드는 없음, 필요하지도 않음).")),
            new Control("CACHE-01", "민감 응답(세션/저장번호/게시글 쓰기) Cache-Control: no-store", "ASVS 8.1.1 / OWASP API3",
                    new Evidence.Gap("CommunitySessionController/CommunityPostController/SavedNumbersController에 "
                            + "no-store 설정 코드는 있으나, 응답 헤더가 실제로 no-store인지 단언하는 테스트가 없다.")),
            new Control("LOG-01", "로그에 비밀값/PII 마스킹", "ASVS 7.1.1 / OWASP API3",
                    new Evidence.Gap("logback-spring.xml에 마스킹 컨버터 없음, 전용 테스트도 없음 — 현재 로그에 "
                            + "실제로 민감정보가 찍히는 경로가 있는지부터 감사 필요.")));

    @Test
    @DisplayName("모든 통제 항목이 GAP 없이 실행 가능한 증거를 가리키는지는 아니지만, 매트릭스 자체가 최소 20개 항목을 유지한다")
    void matrixCoversMinimumControlSurface() {
        assertThat(ENTRIES).hasSizeGreaterThanOrEqualTo(20);
        assertThat(ENTRIES.stream().map(Control::id)).doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testBackedEntries")
    @DisplayName("증거로 지목된 테스트 클래스·메서드가 실제로 존재한다")
    void referencedTestMethodExists(Control control) throws ClassNotFoundException {
        Evidence.OfTest evidence = (Evidence.OfTest) control.evidence();
        Class<?> testClass = Class.forName(evidence.testClassName());
        boolean methodExists = Arrays.stream(testClass.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(evidence.methodName()));

        assertThat(methodExists)
                .as("%s: %s#%s 메서드가 리네임되거나 삭제됐다 — 매트릭스 항목을 갱신하라",
                        control.id(), evidence.testClassName(), evidence.methodName())
                .isTrue();
    }

    static List<Control> testBackedEntries() {
        return ENTRIES.stream().filter(c -> c.evidence() instanceof Evidence.OfTest).toList();
    }
}
