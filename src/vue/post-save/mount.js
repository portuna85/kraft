import { createApp } from 'vue';
import PostSaveApp from './PostSaveApp.vue';

/**
 * 게시글 등록 Vue 아일랜드의 진입점. post-edit/mount.js와 같은 규칙: 마운트 지점이 없는
 * 페이지(로그인 전·이메일 미인증 안내만 보이는 경우 포함)에서는 조용히 아무 일도 하지 않는다.
 */
const mountPoint = document.getElementById('post-save-app');

if (mountPoint) {
    // 같은 이유(F12) — PostSaveApp이 곧바로 categoryOptions[0]을 참조한다.
    let initial = null;
    try {
        const parsed = JSON.parse(document.getElementById('post-save-initial-data')?.textContent || 'null');
        if (parsed && Array.isArray(parsed.categoryOptions) && typeof parsed.author === 'string') {
            initial = parsed;
        }
    } catch {
        initial = null;
    }

    if (initial) {
        createApp(PostSaveApp, { categoryOptions: initial.categoryOptions, author: initial.author }).mount(mountPoint);
    } else {
        window.kraftVueMountFailed?.(mountPoint.id);
    }
}
