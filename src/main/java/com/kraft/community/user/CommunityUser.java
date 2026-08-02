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

}
