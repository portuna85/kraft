package com.kraft.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityOAuth2UserService 단위 테스트")
class CommunityOAuth2UserServiceTest {

    @Mock
    private CommunityUserRepository communityUserRepository;

    private CommunityOAuth2UserService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityOAuth2UserService(communityUserRepository, clock, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("기존 행이 없으면 새 사용자를 만든다")
    void upsert_createsNewUserWhenNoneExists() {
        given(communityUserRepository.findByProviderAndProviderId("google", "sub-1")).willReturn(Optional.empty());
        given(communityUserRepository.save(org.mockito.ArgumentMatchers.any(CommunityUser.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CommunityUser result = service.upsert(attributes("google", "sub-1", "새사용자", "https://img/new"));

        assertThat(result.isWithdrawn()).isFalse();
        assertThat(result.getNickname()).isEqualTo("새사용자");
    }

    @Test
    @DisplayName("탈퇴하지 않은 기존 사용자는 그대로 로그인된다(닉네임 변경 없음)")
    void upsert_returnsExistingActiveUserUnchanged() {
        CommunityUser existing = new CommunityUser("google", "sub-1", "기존닉네임", "https://img/old", OffsetDateTime.now());
        given(communityUserRepository.findByProviderAndProviderId("google", "sub-1"))
                .willReturn(Optional.of(existing));

        CommunityUser result = service.upsert(attributes("google", "sub-1", "최신닉네임", "https://img/new"));

        assertThat(result).isSameAs(existing);
        assertThat(result.getNickname()).isEqualTo("기존닉네임");
    }

    @Test
    @DisplayName("KB-04: 탈퇴한 계정으로 재로그인하면 새 행을 만들지 않고 기존 행을 재활성화한다")
    void upsert_reactivatesWithdrawnUserInsteadOfCreatingNewRow() {
        CommunityUser withdrawn = new CommunityUser(
                "google", "sub-1", "탈퇴한 사용자#1", null, OffsetDateTime.now());
        // withdraw()는 community.user 패키지 전용(package-private)이라 이 테스트(community.auth)에서는
        // 직접 호출할 수 없다 — CommunityWithdrawalServiceTest가 그 메서드 자체는 이미 검증하므로,
        // 여기서는 "이미 탈퇴된 상태"를 리플렉션으로 만들어 재로그인 분기만 검증한다.
        setField(withdrawn, "withdrawnAt", OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        given(communityUserRepository.findByProviderAndProviderId("google", "sub-1"))
                .willReturn(Optional.of(withdrawn));
        given(communityUserRepository.save(withdrawn)).willReturn(withdrawn);

        CommunityUser result = service.upsert(attributes("google", "sub-1", "복귀한사용자", "https://img/back"));

        ArgumentCaptor<CommunityUser> savedCaptor = ArgumentCaptor.forClass(CommunityUser.class);
        org.mockito.Mockito.verify(communityUserRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).isSameAs(withdrawn);
        assertThat(result.isWithdrawn()).isFalse();
        assertThat(result.getNickname()).isEqualTo("복귀한사용자");
        assertThat(result.getProfileImageUrl()).isEqualTo("https://img/back");
    }

    private CommunityOAuthAttributes attributes(String provider, String providerId, String nickname,
                                                 String profileImageUrl) {
        if ("google".equals(provider)) {
            return CommunityOAuthAttributes.of("google",
                    Map.of("sub", providerId, "name", nickname, "picture", profileImageUrl));
        }
        throw new IllegalArgumentException("unsupported provider in test helper: " + provider);
    }

    private static void setField(CommunityUser user, String fieldName, Object value) {
        try {
            var field = CommunityUser.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(user, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
