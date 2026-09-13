import { byId, formatFileSize, on, setHidden } from '../core/dom.js';
import { api } from '../core/http.js';
import { API, UPLOAD, UPLOAD_MESSAGES } from '../core/constants.js';
import * as flash from '../ui/flash.js';

/**
 * 글 등록·수정 화면이 함께 쓰는 이미지 첨부 필드.
 *
 * 예전에는 이 로직(검증·미리보기·해제·업로드 기억)이 postEdit와 postForm에 **90줄 넘게 그대로
 * 복사**되어 있었다. 다른 것은 엘리먼트 id와 문구뿐이었고, 한쪽만 고쳐 두 화면의 동작이
 * 갈라지기 쉬웠다(실제로 저장 실패 안내가 서로 달랐다).
 *
 * 업로드 URL을 File 객체 기준으로 기억하는 것이 이 모듈의 핵심이다. "업로드는 성공했는데 글
 * 저장이 실패한" 경우 사용자가 다시 누르면 같은 파일을 또 올리지 않고 이미 받은 URL로 재시도한다.
 */
export function createImageUploadField({
    inputId,
    previewId,
    previewImageId,
    previewNameId,
    clearButtonId,
    // 아래 둘은 편집 화면에만 있다. 등록 화면은 "기존 이미지"라는 상태 자체가 없다.
    onFileAccepted,
    onCleared,
}) {
    const input = byId(inputId);

    let previewUrl = null;
    let uploadedUrl = null;
    let uploadedForFile = null;

    const currentFile = () => input?.files?.[0] ?? null;

    function revokePreview() {
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }
    }

    function clear() {
        if (input) {
            input.value = '';
        }
        uploadedUrl = null;
        uploadedForFile = null;
        revokePreview();
        setHidden(byId(previewId), true);
        onCleared?.();
    }

    function onChange() {
        const file = currentFile();
        uploadedUrl = null;
        uploadedForFile = null;
        revokePreview();

        if (!file) {
            setHidden(byId(previewId), true);
            return;
        }

        const extension = (file.name.split('.').pop() ?? '').toLowerCase();
        if (UPLOAD.HEIF_EXTENSIONS.includes(extension)) {
            flash.showError(UPLOAD_MESSAGES.HEIF);
            clear();
            return;
        }
        if (!UPLOAD.ALLOWED_EXTENSIONS.includes(extension)) {
            flash.showError(UPLOAD_MESSAGES.NOT_ALLOWED);
            clear();
            return;
        }
        if (file.size > UPLOAD.MAX_BYTES) {
            flash.showError(UPLOAD_MESSAGES.TOO_LARGE);
            clear();
            return;
        }

        flash.hide();
        onFileAccepted?.();

        previewUrl = URL.createObjectURL(file);
        byId(previewImageId).src = previewUrl;
        byId(previewNameId).textContent = `${file.name} · ${formatFileSize(file.size)}`;
        setHidden(byId(previewId), false);
    }

    if (input) {
        on(input, 'change', onChange);
        on(byId(clearButtonId), 'click', clear);
        // 탭을 벗어나거나 닫을 때도 object URL을 해제한다.
        on(window, 'pagehide', revokePreview);
    }

    return {
        hasFile: () => Boolean(currentFile()),
        clear,
        revokePreview,

        /**
         * 선택한 파일이 있으면 업로드해 URL을 돌려준다. 파일이 없으면 null.
         * 같은 파일을 이미 올렸다면 다시 올리지 않고 기억해 둔 URL을 쓴다.
         */
        async resolveUrl() {
            const file = currentFile();
            if (!file) {
                return null;
            }
            if (uploadedUrl && uploadedForFile === file) {
                return uploadedUrl;
            }

            const formData = new FormData();
            formData.append('file', file);
            const response = await api.upload(API.POST_IMAGES, formData);

            uploadedUrl = response.url;
            uploadedForFile = file;
            return uploadedUrl;
        },
    };
}
