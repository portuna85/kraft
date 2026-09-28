// @ts-check
import { onMounted, onUnmounted } from 'vue';
import { api } from '@core/http.js';
import { API } from '@core/constants.js';

/**
 * 긴 글을 쓰는 동안 세션이 조용히 만료되지 않도록 주기적으로 가벼운 GET을 보낸다(A-FE-12).
 * Spring Session은 인증된 요청이 오면 그 세션의 마지막 접근 시각을 갱신하므로, 이 호출
 * 자체가 만료 시각(server.servlet.session.timeout)을 뒤로 미룬다.
 *
 * 이미 세션이 끊겼으면 이 요청도 401/403을 받을 뿐이다 — 실패해도 화면에 아무것도 보여주지
 * 않는다. 실제 저장 시점의 403 처리(PostSaveApp·PostEditApp의 onSubmit)가 사용자에게 보이는
 * 유일한 신호로 남는다 — 여기서 또 알리면 아직 저장하지도 않았는데 놀랄 수 있다.
 */
const PING_INTERVAL_MS = 10 * 60 * 1000;

export function useSessionKeepAlive() {
    /** @type {ReturnType<typeof setInterval> | null} */
    let timer = null;

    onMounted(() => {
        timer = setInterval(() => {
            api.get(`${API.USERS_ME}/ping`).catch(() => {});
        }, PING_INTERVAL_MS);
    });

    onUnmounted(() => {
        if (timer !== null) {
            clearInterval(timer);
        }
    });
}
