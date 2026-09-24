import { mountIsland, parsePageData } from '../shared/mountIsland.js';
import CommentsApp from './CommentsApp.vue';

/**
 * 이 파일에는 // @ts-check를 켜지 않는다. CommentsApp.vue를 import하는 순간 tsc가
 * "Cannot find module './CommentsApp.vue'"로 막힌다(.vue 모듈 선언이 없다 — 실제로 켜서
 * 확인했다). .vue SFC 타입 검사는 vue-tsc 등 별도 도구가 필요한 비용 평가 대상이라 이번
 * 범위에서 제외한다(개선 보고서 F07). 아래 JSDoc @typedef는 // @ts-check 없이도 에디터
 * 자동완성에는 쓰이므로 문서화 목적으로 남겨 둔다.
 */

/**
 * 서버 CommentViewDto와 필드를 맞춘다(src/main/java/com/kraft/comment/dto/CommentViewDto.java).
 *
 * @typedef {Object} CommentViewDto
 * @property {number} id
 * @property {number} postId
 * @property {string} content
 * @property {string} author
 * @property {string} createdAt
 * @property {boolean} canManage
 */

/**
 * 서버 CommentPageDto와 필드를 맞춘다(src/main/java/com/kraft/comment/dto/CommentPageDto.java).
 * 댓글 목록 API(GET .../comments/page)와 이 페이지의 bootstrap JSON(#comments-initial-data)이
 * 공유하는 모양이다.
 *
 * @typedef {Object} CommentPageDto
 * @property {CommentViewDto[]} comments
 * @property {number} totalCount
 * @property {boolean} hasMore
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
        if (!initialPage) {
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
