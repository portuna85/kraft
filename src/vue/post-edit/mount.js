import { mountIsland, parsePageData } from '../shared/mountIsland.js';
import PostEditApp from './PostEditApp.vue';

/**
 * 게시글 읽기/편집 Vue 아일랜드의 진입점. comments/mount.js와 같은 규칙: 마운트 지점이 없는
 * 페이지에서는 조용히 아무 일도 하지 않는다.
 */
const mountPoint = document.getElementById('post-app');

mountIsland({
    mountPoint,
    component: PostEditApp,
    props: () => {
        // JSON이 없거나 깨졌거나 모양이 다르면(개선 보고서 F12) PostEditApp이 곧바로
        // post.title·categoryOptions[0]을 참조하므로 그건 안전한 기본 상태가 아니다 — 여기서
        // 먼저 걸러 props 자체를 만들지 않는다.
        const initial = parsePageData('post-initial-data',
            (parsed) => parsed?.post && typeof parsed.post.title === 'string' && Array.isArray(parsed.categoryOptions));
        if (!initial) {
            return null;
        }
        return {
            post: initial.post,
            categoryOptions: initial.categoryOptions,
            authenticated: initial.authenticated,
        };
    },
});
