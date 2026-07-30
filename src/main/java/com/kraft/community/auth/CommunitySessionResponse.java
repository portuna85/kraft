package com.kraft.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CommunitySessionResponse(
        boolean loggedIn,
        @Schema(nullable = true) Long userId,
        @Schema(nullable = true) String nickname,
        List<String> activeProviders
) {

    public static CommunitySessionResponse anonymous(List<String> activeProviders) {
        return new CommunitySessionResponse(false, null, null, activeProviders);
    }

    public static CommunitySessionResponse of(CommunityPrincipal principal, List<String> activeProviders) {
        return new CommunitySessionResponse(true, principal.getUserId(), principal.getNickname(), activeProviders);
    }
}
