package com.kraft.community.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "community_users")
public class CommunityUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 190)
    private String providerId;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    protected CommunityUser() {
    }

    public CommunityUser(String provider, String providerId, String nickname, String profileImageUrl,
                          OffsetDateTime createdAt) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /** KB-04: 닉네임을 익명화하고 프로필 이미지를 지운 뒤 탈퇴 시각을 기록한다. */
    void withdraw(String pseudonymizedNickname, OffsetDateTime withdrawnAt) {
        this.nickname = pseudonymizedNickname;
        this.profileImageUrl = null;
        this.withdrawnAt = withdrawnAt;
    }

    // community.auth의 CommunityOAuth2UserService가 재로그인 시점에 직접 호출하므로 public —
    // withdraw()는 같은 패키지(CommunityWithdrawalService)에서만 쓰여 package-private으로 남긴다.
    /** KB-04: 같은 OAuth 계정으로 재로그인 시 탈퇴 이전 행을 재사용해 다시 활성화한다. */
    public void reactivate(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.withdrawnAt = null;
    }
}
