package com.kraft.post.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kraft.post.domain.PostImageRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 업로드 이미지를 공개 URL로 내려줘도 되는지 판정한다. 숨김·소프트 삭제된 글에 붙은 이미지는 글이 보이지 않는 동안 내려주지 않는다 — '본문 숨김'이
 * 첨부 파일 주소만 알면 열리는 상태로 남지 않게 한다. 글에 붙지 않은 이미지(작성 중 미리보기)와 대장에 없는 파일은 막지 않는다.
 * <p>
 * 이미지는 글 한 번 볼 때마다 여러 장 요청되므로 판정을 짧게 보관한다. 보관 시간만큼 숨김·복구가 늦게 반영된다.
 */
@Component
public class PostImageVisibility {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long MAX_ENTRIES = 10_000;

    private final PostImageRepository postImageRepository;
    private final Cache<String, Boolean> blocked = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_ENTRIES)
            .build();

    public PostImageVisibility(PostImageRepository postImageRepository) {
        this.postImageRepository = postImageRepository;
    }

    /** @return 숨김·삭제된 글의 이미지라 내려주면 안 되면 true */
    public boolean isBlocked(String fileName) {
        return blocked.get(fileName, postImageRepository::isAttachedToHiddenPost);
    }
}
