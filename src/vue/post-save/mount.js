import { createApp } from 'vue';
import PostSaveApp from './PostSaveApp.vue';

/**
 * 게시글 등록 Vue 아일랜드의 진입점. post-edit/mount.js와 같은 규칙: 마운트 지점이 없는
 * 페이지(로그인 전·이메일 미인증 안내만 보이는 경우 포함)에서는 조용히 아무 일도 하지 않는다.
 */
const mountPoint = document.getElementById('post-save-app');

if (mountPoint) {
    const { categoryOptions, author } = JSON.parse(
        document.getElementById('post-save-initial-data')?.textContent || '{}',
    );

    createApp(PostSaveApp, { categoryOptions, author }).mount(mountPoint);
}
