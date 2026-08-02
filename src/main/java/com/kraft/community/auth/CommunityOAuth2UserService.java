package com.kraft.community.auth;

import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Creates or reuses active OAuth community accounts. Deleted accounts are never reactivated. */
@Service
public class CommunityOAuth2UserService extends DefaultOAuth2UserService {

    private final CommunityUserRepository communityUserRepository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public CommunityOAuth2UserService(CommunityUserRepository communityUserRepository, Clock clock,
                                       MeterRegistry meterRegistry) {
        this.communityUserRepository = communityUserRepository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        try {
            OAuth2User oAuth2User = super.loadUser(userRequest);
            if (oAuth2User == null) {
                throw new OAuth2AuthenticationException("OAuth user information is unavailable.");
            }
            CommunityOAuthAttributes attributes = CommunityOAuthAttributes.of(registrationId, oAuth2User.getAttributes());
            CommunityUser user = upsert(attributes);
            loginCounter(registrationId, "success").increment();
            return new CommunityPrincipal(user.getId(), user.getNickname());
        } catch (RuntimeException failure) {
            loginCounter(registrationId, "failure").increment();
            throw failure;
        }
    }

    private Counter loginCounter(String provider, String outcome) {
        return Counter.builder("kraft_community_oauth_login_total")
                .description("Community OAuth login attempts")
                .tag("provider", provider)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    CommunityUser upsert(CommunityOAuthAttributes attributes) {
        return communityUserRepository.findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                .orElseGet(() -> createNewUser(attributes));
    }

    private CommunityUser createNewUser(CommunityOAuthAttributes attributes) {
        try {
            return communityUserRepository.save(new CommunityUser(
                    attributes.provider(),
                    attributes.providerId(),
                    attributes.nickname(),
                    attributes.profileImageUrl(),
                    OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException concurrentSignup) {
            return communityUserRepository.findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                    .orElseThrow(() -> concurrentSignup);
        }
    }
}
