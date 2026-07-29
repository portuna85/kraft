package com.kraft.community.post;

import com.kraft.recommend.RecommendationItemView;
import java.util.List;

/**
 * 게시글에 첨부된 추천 세트의 읽기 전용 뷰 — 생성 당시 결과를 그대로 보여준다(불변).
 * 원본 추천 세트가 삭제되더라도(현재는 첨부된 세트의 삭제 자체를 막지만) 이 뷰의 형태는
 * 유지되도록 세트 정보를 스냅샷처럼 통째로 담는다.
 */
public record RecommendationAttachmentView(
        Long setId,
        String strategy,
        String algorithmVersion,
        int historyThroughRound,
        String exclusionPolicyVersion,
        List<RecommendationItemView> items
) {
}
