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
    const initialComments = JSON.parse(
        document.getElementById('comments-initial-data')?.textContent || '[]',
    );

    createApp(CommentsApp, {
        postId: mountPoint.dataset.postId,
        authenticated: mountPoint.dataset.authenticated === 'true',
        canWrite: mountPoint.dataset.canWrite === 'true',
        initialComments,
    }).mount(mountPoint);
}
