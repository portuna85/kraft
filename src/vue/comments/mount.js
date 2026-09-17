import { createApp } from 'vue';
import CommentsApp from './CommentsApp.vue';

/**
 * 댓글 Vue 아일랜드의 진입점.
 *
 * main.js의 각 feature init()과 같은 규칙을 따른다: 마운트 지점이 없는 페이지에서도 조용히
 * 아무 일도 하지 않아야 한다. 이 번들은 마운트 지점이 있는 템플릿에서만 <script type="module">로
 * 불러온다(main.js처럼 전역으로 싣지 않는다).
 */
const mountPoint = document.getElementById('comments-app');

if (mountPoint) {
    // 서버는 CommentPageDto({ comments, totalCount, hasMore })를 내려준다 — 최초 페이지는
    // 최대 PAGE_SIZE개만 담고, 전체 개수와 다음 페이지 존재 여부를 함께 실어 "더 보기"가
    // 이어받게 한다(개선 보고서 "댓글 전체 로딩").
    const initialPage = JSON.parse(
        document.getElementById('comments-initial-data')?.textContent || '{"comments":[],"totalCount":0,"hasMore":false}',
    );

    createApp(CommentsApp, {
        postId: mountPoint.dataset.postId,
        authenticated: mountPoint.dataset.authenticated === 'true',
        canWrite: mountPoint.dataset.canWrite === 'true',
        // 쓸 수 없을 때 그 이유(이메일 미인증·이용 제한). 서버가 작성 경로와 같은 규칙으로 만든다.
        writeBlockReason: mountPoint.dataset.writeBlockReason ?? '',
        initialComments: initialPage.comments,
        initialTotalCount: initialPage.totalCount,
        initialHasMore: initialPage.hasMore,
    }).mount(mountPoint);
}
