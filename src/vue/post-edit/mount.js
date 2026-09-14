import { createApp } from 'vue';
import PostEditApp from './PostEditApp.vue';

/**
 * 게시글 읽기/편집 Vue 아일랜드의 진입점. comments/mount.js와 같은 규칙: 마운트 지점이 없는
 * 페이지에서는 조용히 아무 일도 하지 않는다.
 */
const mountPoint = document.getElementById('post-app');

if (mountPoint) {
    const { post, categoryOptions, authenticated } = JSON.parse(
        document.getElementById('post-initial-data')?.textContent || '{}',
    );

    createApp(PostEditApp, { post, categoryOptions, authenticated }).mount(mountPoint);
}
