package com.kraft.identity;

import com.kraft.common.web.DeviceTokenSupport;
import com.kraft.community.auth.CommunityPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로가 /api/v1/community/session/** 이므로 CommunitySecurityConfig의 기존 매처가
 * 패키지 위치와 무관하게 그대로 커버한다(세션 인증 + CSRF 이미 적용됨).
 */
@RestController
@RequestMapping("/api/v1/community/session")
public class IdentityMergeController {

    private final IdentityMergeService identityMergeService;
    private final DeviceTokenSupport deviceTokenSupport;

    public IdentityMergeController(IdentityMergeService identityMergeService, DeviceTokenSupport deviceTokenSupport) {
        this.identityMergeService = identityMergeService;
        this.deviceTokenSupport = deviceTokenSupport;
    }

    @PostMapping("/claim-device")
    public ResponseEntity<IdentityMergeResult> claimDevice(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @RequestHeader(name = "X-Device-Token", required = true) String deviceToken) {
        String deviceTokenHash = deviceTokenSupport.requireHashedToken(deviceToken);
        IdentityMergeResult result = identityMergeService.claim(deviceTokenHash, principal.getUserId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
