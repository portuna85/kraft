package com.kraft.community.post;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import org.springframework.stereotype.Component;

/**
 * 게시글 접근 가능 여부 검증.
 *
 * <p>댓글·반응 서비스가 각자 인라인으로 갖고 있던 동일한 검증을 한곳으로 모은 것이다. 두
 * 사본은 바이트 단위로 같았고 주석이 서로를 참조하고 있었다 — 한쪽만 고치면 조용히 갈라지는
 * 자리였다.
 *
 * <p><b>이 검증이 왜 필요한가</b>(각 도입 근거를 합친 것):
 * <ul>
 *   <li>B-P0-5(반응): 존재 검증 없이 곧바로 insert해 FK 위반으로 500이 나거나, 숨김·삭제된
 *       글에도 좋아요·북마크를 남길 수 있었다.
 *   <li>B-P0-7(댓글 조회): postId만으로 곧바로 댓글을 조회해 숨김·삭제된 글의 댓글이
 *       permitAll 엔드포인트로 계속 읽혔다.
 *   <li>KB-01(댓글 작성): 존재만 확인해 숨김·삭제된 글의 postId로도 댓글을 남기고
 *       commentCount를 증가시킬 수 있었다.
 * </ul>
 *
 * <p><b>404 계약</b>: 없는 글과 비공개 글을 같은 응답으로 묶는다. 둘을 구분해 알려 주면
 * 숨김·삭제된 게시글의 존재 여부가 새어 나간다.
 */
@Component
public class CommunityPostAccessValidator {

    private final CommunityPostRepository communityPostRepository;

    public CommunityPostAccessValidator(CommunityPostRepository communityPostRepository) {
        this.communityPostRepository = communityPostRepository;
    }

    /** 공개(PUBLISHED) 상태인 게시글을 돌려주고, 아니면 404로 끊는다. */
    public CommunityPost requireVisible(Long postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.COMMUNITY_POST_NOT_FOUND,
                        "게시글을 찾을 수 없습니다."));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(ApiErrorCode.COMMUNITY_POST_NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        return post;
    }
}
