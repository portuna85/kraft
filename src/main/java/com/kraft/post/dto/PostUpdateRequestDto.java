package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하로 입력하세요.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.POST_CONTENT_MAX_LENGTH, message = "내용은 {max}자 이하로 입력하세요.")
        String content,

        @Size(max = 500, message = "이미지 경로가 올바르지 않습니다.")
        String picture,

        // PostSaveRequestDto와 같은 이유(A-FE-09) — CLS 방지용 힌트일 뿐 서버가 picture와의
        // 정합성을 검증하지 않는다.
        @Positive(message = "이미지 폭은 양수여야 합니다.")
        Integer pictureWidth,

        @Positive(message = "이미지 높이는 양수여야 합니다.")
        Integer pictureHeight,

        Category category,

        /**
         * 편집을 시작할 때 화면이 받아간 게시글 버전. 이제 기준 버전은 {@code If-Match} 헤더로 보낸다
         * ({@code EntityTags}) — 이 필드는 <b>전환기 폴백</b>이다. 배포 전에 열어 둔 탭의 옛 화면이 아직
         * 헤더 없이 본문 버전만 보내기 때문이다. 새 화면은 둘 다 보내 서버를 이전 버전으로 되돌려도 동작한다.
         * 헤더도 이 값도 없으면 428이다(평가 보고서 2026-09-25 F11의 "버전 없는 요청은 덮어쓰지 않는다"를 그대로
         * 지킨다). 다음 릴리스에서 이 필드를 지운다.
         */
        Long version
) {
}
