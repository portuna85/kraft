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

/**
 * Google/Naver 사용자 정보를 정규화(CommunityOAuthAttributes)한 뒤 (provider, providerId)
 * 기준으로 CommunityUser에 upsert한다. 동시 최초가입 경합은 unique 제약 위반을 잡아
 * 기존 행으로 재조회해 수렴시킨다(Blitz의 "동시 가입 경합 → 재조회 수렴" 방식과 동일).
 */
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
                throw new OAuth2AuthenticationException("OAuth2 사용자 정보를 가져오지 못했습니다.");
            }
            CommunityOAuthAttributes attributes =
                    CommunityOAuthAttributes.of(registrationId, oAuth2User.getAttributes());

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
                .description("커뮤니티 OAuth2 로그인 시도 결과")
                .tag("provider", provider)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    // KB-08: 예전에 여기 @Transactional이 있었으나, loadUser()가 같은 인스턴스의 upsert()를
    // this.upsert(...)로 직접 호출해 프록시를 거치지 않으므로 실제로는 전혀 적용되지 않는
    // 어노테이션이었다(AOP 트랜잭션은 프록시 경유 호출에서만 동작). 이 메서드의 각 분기
    // (reactivateIfWithdrawn의 save 1회, createNewUser의 save 1회)는 그 자체로 이미 원자적인
    // 단일 쓰기라 트랜잭션 경계가 없어도 지금은 관찰 가능한 차이가 없다 — 오해를 부르는
    // 죽은 어노테이션을 지운다. 여러 쓰기를 묶어야 하는 로직이 추가되면 그때 별도 빈으로
    // 분리해 프록시를 거치게 하거나(CommunityReactionWriter 패턴 참고) 자기 주입을 쓴다.
    CommunityUser upsert(CommunityOAuthAttributes attributes) {
        return communityUserRepository.findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                .map(existing -> reactivateIfWithdrawn(existing, attributes))
                .orElseGet(() -> createNewUser(attributes));
    }

    // KB-04: 탈퇴한 계정이 같은 OAuth 계정으로 재로그인하면 새 행을 만들지 않고
    // (provider, provider_id) unique 제약상 만들 수도 없다 — 기존 행을 재활성화한다.
    // 탈퇴 시 익명화된 옛 닉네임은 복구하지 않고, 이번 로그인에서 provider가 내려준
    // 최신 닉네임·프로필 이미지로 새로 채운다.
    private CommunityUser reactivateIfWithdrawn(CommunityUser user, CommunityOAuthAttributes attributes) {
        if (!user.isWithdrawn()) {
            return user;
        }
        user.reactivate(attributes.nickname(), attributes.profileImageUrl());
        return communityUserRepository.save(user);
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
            return communityUserRepository
                    .findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                    .orElseThrow(() -> concurrentSignup);
        }
    }
}
