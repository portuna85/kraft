// @ts-check
import { onMounted, onUnmounted } from 'vue';
import { api } from '@core/http.js';
import { API } from '@core/constants.js';

/**
 * 긴 글을 쓰는 동안 세션이 조용히 만료되지 않도록 주기적으로 가벼운 GET을 보낸다. Spring Session은 인증된 요청이 오면 마지막 접근 시각을 갱신하므로 이 호출 자체가 만료 시각(server.servlet.session.timeout)을 미룬다.
 *
 * 이미 세션이 끊겼으면 이 요청도 401/403을 받을 뿐이며, 실패해도 화면에 아무것도 보여주지 않는다 — 저장 시점의 403 처리(PostSaveApp·PostEditApp의 onSubmit)가 사용자에게 보이는 유일한 신호이고, 여기서 또 알리면 아직 저장하지도 않았는데 놀랄 수 있다.
 */
const PING_INTERVAL_MS = 10 * 60 * 1000;
/** 한 화면에서 세션을 붙들어 두는 최대 시간. 방치된 공용 PC의 세션이 끝없이 이어지지 않게 한다. */
const MAX_KEEP_ALIVE_MS = 4 * 60 * 60 * 1000;
const ACTIVITY_EVENTS = ['keydown', 'input', 'pointerdown'];

/**
 * 사용자가 실제로 쓰고 있을 때만 핑한다 — 마지막 핑 이후 입력이 있었고 탭이 보이는 경우.
 * 탭이 숨겨졌거나 방치된 글쓰기 화면이 서버의 유휴 타임아웃을 무력화하지 못하게 한다.
 */
export function useSessionKeepAlive() {
    /** @type {ReturnType<typeof setInterval> | null} */
    let timer = null;
    let active = false;
    let startedAt = 0;

    /** @param {Event} event */
    function markActive(event) {
        // 스크립트가 만든 합성 이벤트(자동 크기 조절 등)는 사용자가 쓰고 있다는 증거가 아니다.
        if (event.isTrusted) {
            active = true;
        }
    }

    function ping() {
        if (Date.now() - startedAt > MAX_KEEP_ALIVE_MS) {
            stop();
            return;
        }
        if (!active || document.visibilityState !== 'visible') {
            return;
        }
        active = false;
        api.get(`${API.USERS_ME}/ping`).catch(() => {});
    }

    function stop() {
        if (timer !== null) {
            clearInterval(timer);
            timer = null;
        }
        ACTIVITY_EVENTS.forEach((name) => document.removeEventListener(name, markActive, true));
    }

    onMounted(() => {
        startedAt = Date.now();
        ACTIVITY_EVENTS.forEach((name) => document.addEventListener(name, markActive, true));
        timer = setInterval(ping, PING_INTERVAL_MS);
    });

    onUnmounted(stop);
}
