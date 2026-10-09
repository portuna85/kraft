package com.kraft.post.dto;

import com.kraft.post.service.PostImageService;

/**
 * width·height는 CLS 방지용 {@code <img width height>}를 채우는 데만 쓴다 — 서버가
 * 이미 검증 단계에서 읽은 실제 픽셀 크기를 클라이언트가 다시 디코딩하지 않고 그대로 돌려받는다.
 */
public record ImageUploadResponseDto(String url, int width, int height) {

    public ImageUploadResponseDto(PostImageService.StoredImage stored) {
        this(stored.url(), stored.width(), stored.height());
    }
}
