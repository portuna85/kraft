// @ts-check
import { onMounted, onUnmounted } from 'vue';

/**
 * 편집 중 브라우저 탭을 닫거나 다른 주소로 이동하면(뒤로 가기 포함) 입력한 내용이 그대로 사라지는 것을 막는다(글 작성·수정 화면 공용).
 *
 * @param {import('vue').Ref<boolean> | import('vue').ComputedRef<boolean>} isDirty
 * @returns {{ allowNavigation: () => void }} 저장에 성공해 스스로 이동할 때 `allowNavigation()`을 불러 확인창을 띄우지 않게 한다.
 */
export function useUnsavedGuard(isDirty) {
    let allowed = false;

    /** @param {BeforeUnloadEvent} event */
    function warnBeforeUnload(event) {
        if (!isDirty.value || allowed) {
            return;
        }
        // 커스텀 문구는 최신 브라우저가 대부분 무시하고 자체 문구를 보여주지만, preventDefault와
        // returnValue 설정 둘 다 있어야 구형 엔진까지 포함해 확인창이 뜬다.
        event.preventDefault();
        event.returnValue = '';
    }

    onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload));
    onUnmounted(() => window.removeEventListener('beforeunload', warnBeforeUnload));

    return {
        allowNavigation() {
            allowed = true;
        },
    };
}
