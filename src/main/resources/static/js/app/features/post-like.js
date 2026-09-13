import { byId, on, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { showToast } from '../ui/toast.js';

/**
 * 게시글 상세의 추천 버튼. 서버가 돌려주는 최종 상태(liked/likeCount)로만 화면을 갱신한다 —
 * 클릭 즉시 낙관적으로 뒤집지 않는 이유는, 이미 다른 탭에서 취소했거나 요청이 실패했을 때
 * 버튼 상태가 실제와 어긋나는 것을 피하기 위해서다.
 */
export function init() {
    const button = byId('btn-like');
    if (!button) {
        return;
    }

    on(button, 'click', async () => {
        if (button.disabled) {
            return;
        }

        // 서버에 "뒤집어라"가 아니라 "이 상태로 만들어라"를 보낸다. 같은 요청이 재시도로 두 번
        // 도달해도 결과가 같다(예전 토글 방식은 재시도가 사용자의 의도를 되돌렸다).
        const desired = !button.classList.contains('is-active');
        button.disabled = true;

        try {
            const result = await api.put(`/api/v1/posts/${valueOf(byId('id'))}/like`, { liked: desired });
            byId('like-count').textContent = result.likeCount;
            button.classList.toggle('is-active', result.liked);
            button.setAttribute('aria-pressed', result.liked ? 'true' : 'false');
        } catch (error) {
            showToast(messageOf(error), 'danger');
        } finally {
            button.disabled = false;
        }
    });
}
