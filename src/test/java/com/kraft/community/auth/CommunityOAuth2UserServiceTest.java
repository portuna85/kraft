package com.kraft.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityOAuth2UserServiceTest {
    @Mock CommunityUserRepository users;
    private CommunityOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CommunityOAuth2UserService(users, Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Test
    void upsert_createsNewAccountWhenDeletedAccountNoLongerExists() {
        given(users.findByProviderAndProviderId("google", "sub-1")).willReturn(Optional.empty());
        given(users.save(org.mockito.ArgumentMatchers.any(CommunityUser.class))).willAnswer(invocation -> invocation.getArgument(0));

        CommunityUser result = service.upsert(attributes("sub-1", "new-user"));

        assertThat(result.getNickname()).isEqualTo("new-user");
    }

    @Test
    void upsert_reusesExistingActiveAccount() {
        CommunityUser existing = new CommunityUser("google", "sub-1", "existing", null, java.time.OffsetDateTime.now());
        given(users.findByProviderAndProviderId("google", "sub-1")).willReturn(Optional.of(existing));
        assertThat(service.upsert(attributes("sub-1", "new-user"))).isSameAs(existing);
    }

    private CommunityOAuthAttributes attributes(String providerId, String nickname) {
        return CommunityOAuthAttributes.of("google", Map.of("sub", providerId, "name", nickname, "picture", "https://img"));
    }
}
