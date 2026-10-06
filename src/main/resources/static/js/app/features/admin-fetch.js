import { on, buttonById } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { API } from '../core/constants.js';
import { showToast } from '../ui/toast.js';
import { confirmAction } from '../ui/confirm-dialog.js';

/**
 * 관리자 "추천 이력 수집" 화면의 "지금 수집" 버튼. 예약 실행과 같은 수집을 서버에서 한 번 돌린다.
 * 운영 DB의 당첨 이력을 바꾸는 동작이라 확인을 한 번 받는다.
 *
 * 수집은 회차마다 외부 사이트를 부르므로 오래 걸릴 수 있다 — 기본 15초 제한 대신 넉넉히 기다린다.
 * 그래도 응답이 끊기면(제한 시간·네트워크) 서버는 계속 돌고 있을 수 있어, 오류 뒤에도 새로고침해
 * 이력으로 결과를 확인하게 안내한다.
 */
const FETCH_TIMEOUT_MS = 120_000;

/** 서버가 돌려주는 status → 토스트 종류. */
const TOAST_TYPE = {
    DONE: 'success',
    CATCHUP_LIMIT: 'warning',
    FAILED: 'danger',
    BUSY: 'warning',
};

export function init() {
    const button = buttonById('btn-fetch-now');
    if (!button) {
        return;
    }

    on(button, 'click', async () => {
        if (button.disabled) {
            return;
        }
        const confirmed = await confirmAction({
            title: '지금 수집',
            message: '동행복권에서 최신 회차를 지금 받아 운영 DB의 당첨 이력에 반영합니다. 계속할까요?',
            confirmLabel: '수집',
        });
        if (!confirmed) {
            return;
        }

        button.disabled = true;
        const originalLabel = button.textContent;
        button.textContent = '수집 중…';
        try {
            const result = await api.post(API.ADMIN_RECOMMENDATION_FETCH, undefined, { timeoutMs: FETCH_TIMEOUT_MS });
            const failed = result?.status === 'FAILED';
            showToast(result?.message ?? '수집을 마쳤습니다.', TOAST_TYPE[result?.status] ?? 'success');
            if (failed || result?.status === 'BUSY') {
                // 사유를 읽어야 하므로 화면을 바꾸지 않는다. 오류 토스트는 직접 닫을 때까지 남는다.
                button.disabled = false;
                button.textContent = originalLabel;
                return;
            }
            // 이력·검증 회차를 새로 보여준다. 토스트(3초)가 읽힐 만큼 기다렸다가 새로고침한다.
            window.setTimeout(() => window.location.reload(), 2500);
        } catch (error) {
            showToast(`${messageOf(error)} 서버에서 계속 진행 중일 수 있으니 잠시 뒤 새로고침해 이력을 확인하세요.`, 'danger');
            button.disabled = false;
            button.textContent = originalLabel;
        }
    });
}
