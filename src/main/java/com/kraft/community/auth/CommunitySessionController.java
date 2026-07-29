package com.kraft.community.auth;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프런트가 로그인 상태를 조회하는 개인화 엔드포인트. 항상
 * private, no-store — 공용 캐시(Caddy/PublicApiCacheControlFilter)에 절대 들어가지 않는다.
 * 익명 요청은 200 + loggedIn=false로 응답한다(permitAll, 세션 미생성).
 */
@RestController
@RequestMapping("/api/v1/community/session")
public class CommunitySessionController {

    private static final List<String> KNOWN_PROVIDERS = List.of("google", "naver");

    private final ClientRegistrationRepository clientRegistrationRepository;

    public CommunitySessionController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping
    public ResponseEntity<CommunitySessionResponse> session(@AuthenticationPrincipal CommunityPrincipal principal) {
        List<String> activeProviders = activeProviders();
        CommunitySessionResponse body = principal == null
                ? CommunitySessionResponse.anonymous(activeProviders)
                : CommunitySessionResponse.of(principal, activeProviders);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private List<String> activeProviders() {
        return KNOWN_PROVIDERS.stream()
                .filter(id -> clientRegistrationRepository.findByRegistrationId(id) != null)
                .toList();
    }
}
