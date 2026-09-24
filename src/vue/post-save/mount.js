import { mountIsland, parsePageData } from '../shared/mountIsland.js';
import PostSaveApp from './PostSaveApp.vue';

/**
 * 게시글 등록 Vue 아일랜드의 진입점. post-edit/mount.js와 같은 규칙: 마운트 지점이 없는
 * 페이지(로그인 전·이메일 미인증 안내만 보이는 경우 포함)에서는 조용히 아무 일도 하지 않는다.
 */
const mountPoint = document.getElementById('post-save-app');

mountIsland({
    mountPoint,
    component: PostSaveApp,
    props: () => {
        // 같은 이유(F12) — PostSaveApp이 곧바로 categoryOptions[0]을 참조한다.
        const initial = parsePageData('post-save-initial-data',
            (parsed) => parsed && Array.isArray(parsed.categoryOptions) && typeof parsed.author === 'string');
        if (!initial) {
            return null;
        }
        return { categoryOptions: initial.categoryOptions, author: initial.author };
    },
});
