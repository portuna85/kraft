// @ts-check
import { messageOf } from '@core/http.js';
import * as flash from '@ui/flash.js';

/**
 * @typedef {{ picture: string | null, pictureWidth: number | null, pictureHeight: number | null }} PictureFields
 */

/**
 * 글 등록·수정 제출의 공통 흐름(FE-12): 사진 업로드 → 요청 → 오류 안내.
 * 예전에는 PostSaveApp.vue와 PostEditApp.vue가 진행 문구·재시도 안내·세션 만료 문구까지 거의 같은
 * 코드를 따로 갖고 있었다. 두 화면의 차이는 이 함수에 넘기는 값으로만 남는다.
 *
 *  - 파일을 새로 골랐으면 업로드한다(재시도해도 같은 파일을 다시 올리지 않는 캐시는 useImageUpload.resolveUrl).
 *  - 고르지 않았으면 `fallbackPicture()`가 정한 값을 쓴다(등록: 사진 없음, 수정: 기존 사진 유지·삭제).
 *
 * 폼 값의 스냅샷은 호출한 쪽이 `submit`을 부르기 전에 떠 둔다 — 업로드를 기다리는 동안 입력을 잠그지만
 * 최종 요청은 항상 제출 시점의 값을 써야 한다.
 *
 * @param {object} options
 * @param {import('vue').Ref<boolean>} options.saving
 * @param {import('vue').Ref<string | null>} options.progressText
 * @param {any} options.picture useImageUpload가 돌려준 객체
 * @param {(error: unknown, fieldRefs?: any) => Promise<boolean>} options.applyFieldErrors
 * @param {any} options.fieldRefs 서버 오류가 가리키는 입력으로 포커스를 옮길 ref들
 * @param {'등록' | '저장'} options.verb 버튼·안내 문구에 쓰는 동사
 */
export function usePostSubmit({ saving, progressText, picture, applyFieldErrors, fieldRefs, verb }) {
    /** @param {string} message */
    function fail(message) {
        progressText.value = null;
        saving.value = false;
        flash.showError(message);
    }

    /**
     * @template T
     * @param {object} request
     * @param {() => PictureFields} request.fallbackPicture 파일을 새로 고르지 않았을 때 보낼 사진 값
     * @param {(pictureFields: PictureFields) => Promise<T>} request.send
     * @param {(result: T) => void} request.onSuccess 성공 뒤 정리·이동. 예외를 던지면 요청 실패와 같이 안내된다.
     */
    async function submit({ fallbackPicture, send, onSuccess }) {
        if (saving.value) {
            return;
        }
        saving.value = true;

        /** @type {PictureFields} */
        let pictureFields;
        try {
            if (picture.hasFile.value) {
                progressText.value = '이미지 업로드 중…';
                const url = await picture.resolveUrl();
                if (!url) {
                    // 파일을 골랐는데 URL이 없다 = 업로드 중 선택이 바뀌었다(FE-02). 그대로 보내면
                    // 사진이 빠지거나 기존 사진이 지워지므로 멈추고 다시 확인하게 한다.
                    fail(`선택한 이미지가 바뀌었습니다. 이미지를 확인한 뒤 다시 "${verb}"을 눌러 주세요.`);
                    return;
                }
                pictureFields = {
                    picture: url,
                    pictureWidth: picture.uploadedWidth.value,
                    pictureHeight: picture.uploadedHeight.value,
                };
            } else {
                pictureFields = fallbackPicture();
            }
        } catch (error) {
            fail(`이미지 업로드에 실패했습니다. ${messageOf(error)}`);
            return;
        }

        progressText.value = `게시글 ${verb} 중…`;
        try {
            onSuccess(await send(pictureFields));
        } catch (error) {
            progressText.value = null;
            saving.value = false;
            // 긴 글을 쓰는 동안 세션이 끊기면 이 시점에야 403/로그인 리다이렉트로 드러난다(A-FE-12).
            // 자동 임시 저장이 내용을 지키고 있으니 그 사실부터 알린다.
            const kind = /** @type {any} */ (error)?.kind;
            if (kind === 'forbidden' || kind === 'auth') {
                flash.showError('로그인이 만료되었습니다. 작성 중인 내용은 임시 저장되어 있으니, 다시 로그인한 뒤 이어서 쓸 수 있습니다.');
                return;
            }
            if (await applyFieldErrors(error, fieldRefs)) {
                return;
            }
            // 업로드까지는 끝났다는 사실을 알려야 사용자가 파일을 다시 고르지 않는다.
            const retryHint = pictureFields.picture
                ? ` 이미지는 이미 업로드되어 있으니 다시 "${verb}"을 누르면 같은 이미지로 재시도합니다.`
                : '';
            flash.showError(`게시글 ${verb}에 실패했습니다. ${messageOf(error)}${retryHint}`);
        }
    }

    return { submit };
}
