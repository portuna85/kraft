// @ts-check
import { nextTick, reactive, watch } from 'vue';

/**
 * 서버 검증 실패(A-BE-07의 `errors[]`)를 입력칸 옆 오류로 옮긴다.
 *
 * 예전에는 "title: 제목은 필수입니다." 같은 한 문장을 통째로 배너에 띄워, 어느 칸이
 * 문제인지 화면에서 알 수 없었고 스크린 리더가 해당 입력으로 이동할 방법도 없었다. 이제
 * `ApiError.body.errors`(있으면)를 필드별로 나눠 `aria-invalid`·`aria-describedby`에 연결하고,
 * 첫 오류 입력으로 포커스를 옮긴다.
 *
 * 이메일 중복처럼 필드 하나로 좁혀지지 않는 서버 판정(도메인 검증 실패)에는 `errors`가 없다 —
 * 그런 경우 {@link apply}는 아무것도 하지 않고 `false`를 돌려주므로, 호출부는 기존처럼
 * 배너·토스트로 안내를 이어간다.
 */
export function useFieldErrors() {
    /** @type {Record<string, string>} */
    const fieldErrors = reactive({});

    function clear() {
        Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key]);
    }

    /**
     * @param {unknown} error `api.*`가 던진 값 그대로 넘긴다.
     * @param {Record<string, import('vue').Ref<{focus: () => void}|null>>} [fieldRefs] 포커스를 옮길 입력·컴포넌트들.
     * @returns {Promise<boolean>} 필드별 오류로 처리했으면 true — 이 경우 호출부는 별도 배너를 띄우지 않는다.
     */
    async function apply(error, fieldRefs = {}) {
        clear();
        const errors = /** @type {{field?: string, message?: string}[] | undefined} */ (
            /** @type {any} */ (error)?.body?.errors
        );
        if (!Array.isArray(errors) || errors.length === 0) {
            return false;
        }

        errors.forEach(({ field, message }) => {
            if (field && message) {
                fieldErrors[field] = message;
            }
        });

        await nextTick();
        const firstField = errors.find((e) => e.field && fieldRefs[e.field]?.value)?.field;
        if (firstField) {
            fieldRefs[firstField]?.value?.focus();
        }
        return true;
    }

    /**
     * 폼 값(reactive)의 각 필드를 지켜보다가 사용자가 고치면 그 필드의 서버 오류만 지운다.
     * 예전에는 다음 `apply`·`clear`까지 오류 표시가 남아, 이미 고친 칸이 계속 붉게 보였다.
     *
     * @param {Record<string, unknown>} source
     */
    function clearOnEdit(source) {
        Object.keys(source).forEach((key) => {
            watch(() => source[key], () => {
                delete fieldErrors[key];
            });
        });
    }

    return { fieldErrors, apply, clear, clearOnEdit };
}
