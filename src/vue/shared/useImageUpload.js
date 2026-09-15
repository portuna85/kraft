import { computed, onUnmounted, ref } from 'vue';
import { api } from '@core/http.js';
import { API, UPLOAD, UPLOAD_MESSAGES } from '@core/constants.js';
import { formatFileSize } from '@core/dom.js';
import * as flash from '@ui/flash.js';

/**
 * 게시글 등록·편집 화면이 함께 쓰는 이미지 첨부 상태.
 *
 * 예전에는 등록 화면이 static/js/app/features/image-upload.js(DOM ref 기반 팩토리)를, 편집
 * 화면이 이 composable을 써서 같은 규칙이 두 벌로 있었다. 등록 화면도 Vue 아일랜드가 되면서
 * 그쪽을 지우고 여기 하나로 모았다.
 *
 * 기존 이미지(initialUrl)는 편집 화면에만 있다. 등록 화면은 인자 없이 호출하며 그때
 * showExistingPreview는 항상 false다.
 *
 * 검증 상수(UPLOAD/UPLOAD_MESSAGES)는 core/constants.js에서 그대로 가져온다. 서버의
 * UploadPolicySyncTest가 이 상수들의 형태를 정규식으로 파싱해 서버 정책과 동기화하므로,
 * 값을 복제하거나 다른 곳으로 옮기면 그 테스트가 깨진다.
 *
 * "손대지 않음 / 새 파일 선택 / 기존 삭제"라는 세 상태는 예전엔 주석으로만 설명됐다
 * (image-upload.js·post-edit.js 참고). 여기서는 hasFile·removedExisting 두 ref의 조합으로
 * showExistingPreview가 파생되어 상태 자체가 코드로 드러난다.
 */
export function useImageUpload({ initialUrl = null } = {}) {
    const file = ref(null);
    const previewUrl = ref(null);
    const removedExisting = ref(false);
    const uploadedUrl = ref(null);
    let uploadedForFile = null;

    const hasFile = computed(() => file.value !== null);
    // 새 파일을 아직 고르지 않았고, 명시적으로 지우지도 않았을 때만 기존 이미지를 보여준다.
    const showExistingPreview = computed(() => !removedExisting.value && !hasFile.value && !!initialUrl);
    const fileLabel = computed(() => (file.value ? `${file.value.name} · ${formatFileSize(file.value.size)}` : ''));

    function revokePreview() {
        if (previewUrl.value) {
            URL.revokeObjectURL(previewUrl.value);
            previewUrl.value = null;
        }
    }

    function clear() {
        file.value = null;
        uploadedUrl.value = null;
        uploadedForFile = null;
        revokePreview();
    }

    function removeExisting() {
        removedExisting.value = true;
    }

    /** 파일 입력의 change 이벤트에서 선택된 File(또는 선택 해제 시 null)을 넘겨받는다. */
    function onFileSelected(selectedFile) {
        uploadedUrl.value = null;
        uploadedForFile = null;
        revokePreview();

        if (!selectedFile) {
            file.value = null;
            return;
        }

        const extension = (selectedFile.name.split('.').pop() ?? '').toLowerCase();
        if (UPLOAD.HEIF_EXTENSIONS.includes(extension)) {
            flash.showError(UPLOAD_MESSAGES.HEIF);
            file.value = null;
            return;
        }
        if (!UPLOAD.ALLOWED_EXTENSIONS.includes(extension)) {
            flash.showError(UPLOAD_MESSAGES.NOT_ALLOWED);
            file.value = null;
            return;
        }
        if (selectedFile.size > UPLOAD.MAX_BYTES) {
            flash.showError(UPLOAD_MESSAGES.TOO_LARGE);
            file.value = null;
            return;
        }

        flash.hide();
        // 새 파일을 고르면 기존 이미지는 교체 대상이므로, 전에 눌러 둔 "삭제"는 무시한다
        // (파일 우선 규칙).
        removedExisting.value = false;
        file.value = selectedFile;
        previewUrl.value = URL.createObjectURL(selectedFile);
    }

    /**
     * 선택한 파일이 있으면 업로드해 URL을 돌려준다. 파일이 없으면 null. 같은 파일을 이미
     * 올렸다면 다시 올리지 않고 기억해 둔 URL을 쓴다(저장 실패 후 재시도 시 중복 업로드 방지).
     */
    async function resolveUrl() {
        if (!file.value) {
            return null;
        }
        if (uploadedUrl.value && uploadedForFile === file.value) {
            return uploadedUrl.value;
        }

        const formData = new FormData();
        formData.append('file', file.value);
        const response = await api.upload(API.POST_IMAGES, formData);

        uploadedUrl.value = response.url;
        uploadedForFile = file.value;
        return uploadedUrl.value;
    }

    onUnmounted(revokePreview);

    return {
        file,
        previewUrl,
        hasFile,
        showExistingPreview,
        fileLabel,
        removedExisting,
        onFileSelected,
        clear,
        removeExisting,
        resolveUrl,
        revokePreview,
    };
}
