import { mountIsland, parsePageData } from '../shared/mountIsland.js';
import CommentsApp from './CommentsApp.vue';

/**
 * 이 파일과 .vue 컴포넌트는 jsconfig의 tsc가 아니라 vue-tsc(tsconfig.vue.json, npm run
 * typecheck:vue)가 검사한다 — tsc는 .vue import를 해석하지 못한다(평가 보고서 2026-09-25 F09).
 * 서버 DTO 모양은 ../shared/types.js에 모아 두었다.
 *
 * @typedef {import('../shared/types.js').CommentPageDto} CommentPageDto
 */

/**
 * 댓글 Vue 아일랜드의 진입점. 마운트 지점이 없는 페이지에서는 조용히 아무 일도 하지 않는다.
 * 이 번들은 마운트 지점이 있는 템플릿에서만 <script type="module">로 불러온다(main.js처럼
 * 전역으로 싣지 않는다).
 */
const mountPoint = document.getElementById('comments-app');

mountIsland({
    mountPoint,
    component: CommentsApp,
    props: () => {
        // 서버는 CommentPageDto({ comments, totalCount, hasMore })를 내려준다 — 최초 페이지는
        // 최대 PAGE_SIZE개만 담고, 전체 개수와 다음 페이지 존재 여부를 함께 실어 "더 보기"가
        // 이어받게 한다(개선 보고서 "댓글 전체 로딩"). JSON이 없거나 깨졌거나 모양이
        // 다르면(개선 보고서 F12) props 자체를 만들지 않는다 — mountIsland가 대신
        // kraftVueMountFailed로 안내한다.
        /** @type {CommentPageDto|null} */
        const initialPage = parsePageData('comments-initial-data',
            (parsed) => parsed && Array.isArray(parsed.comments)
                && typeof parsed.totalCount === 'number' && typeof parsed.hasMore === 'boolean');
        // mountPoint가 없으면 mountIsland가 이 함수를 부르지 않지만, 타입 검사가 그 사실을 모르므로 함께 본다.
        if (!initialPage || !mountPoint) {
            return null;
        }
        return {
            postId: mountPoint.dataset.postId,
            authenticated: mountPoint.dataset.authenticated === 'true',
            canWrite: mountPoint.dataset.canWrite === 'true',
            // 쓸 수 없을 때 그 이유(이메일 미인증·이용 제한). 서버가 작성 경로와 같은 규칙으로 만든다.
            writeBlockReason: mountPoint.dataset.writeBlockReason ?? '',
            initialComments: initialPage.comments,
            initialTotalCount: initialPage.totalCount,
            initialHasMore: initialPage.hasMore,
        };
    },
});
