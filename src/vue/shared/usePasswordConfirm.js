// @ts-check
import { nextTick, ref, watch } from 'vue';

/**
 * 비밀번호 확인란의 불일치 검사. 가입·비밀번호 재설정 화면이 같은 규칙을 따로 두 벌 들고
 * 있었다(F05) — 값이 다시 같아지는 순간 바로 오류를 지우고(개선 보고서 F13), 제출 시 불일치면
 * 필드 옆에서 알리며 포커스를 옮긴다. 서버에 물어볼 필요가 없는 입력 오류라 여기서 막는다.
 *
 * @param {() => string} password
 * @param {() => string} confirm
 */
export function usePasswordConfirm(password, confirm) {
    const confirmError = ref('');
    /** @type {import('vue').Ref<HTMLInputElement|null>} */
    const confirmInput = ref(null);

    watch([password, confirm], () => {
        if (confirmError.value && password() === confirm()) {
            confirmError.value = '';
        }
    });

    /**
     * 일치하면 true. 불일치면 오류를 채우고 확인란에 포커스를 옮긴 뒤 false를 돌려준다 —
     * 호출부는 이 반환값으로 제출을 계속할지 결정한다.
     */
    async function validateMatch() {
        confirmError.value = '';
        if (password() !== confirm()) {
            confirmError.value = '비밀번호가 일치하지 않습니다.';
            await nextTick();
            confirmInput.value?.focus();
            return false;
        }
        return true;
    }

    return { confirmError, confirmInput, validateMatch };
}
