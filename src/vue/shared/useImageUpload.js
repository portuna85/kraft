// @ts-check
import { computed, onUnmounted, ref } from 'vue';
import { api } from '@core/http.js';
import { API, UPLOAD, UPLOAD_MESSAGES } from '@core/constants.js';
import { formatFileSize } from '@core/dom.js';
import * as flash from '@ui/flash.js';

/**
 * @typedef {Object} UploadResponse
 * @property {string} url
 * @property {number} width
 * @property {number} height
 */

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
 *
 * 업로드 재사용(재시도해도 이미 올린 파일을 다시 올리지 않는 캐시)은 resolveUrl()에
 * 여기 한 번만 있다. PostSaveApp.vue·PostEditApp.vue의 onSubmit()이 진행 문구·재시도
 * 안내 문구를 비슷하게 반복하는 것은 남아 있는 중복이지만, 그 둘은 POST/PUT과 버전
 * 충돌 처리가 달라 억지로 합치지 않기로 했다(각 파일의 onSubmit 주석 참고).
 */
/** 이보다 긴 변을 가진 이미지만 줄인다(A-FE-06). 이미 작은 이미지를 다시 인코딩할 이유는 없다. */
const MAX_DIMENSION = 2048;
const RESIZE_QUALITY = 0.85;

/**
 * 캔버스로 다시 그려 축소한다. 세 가지 효과를 동시에 얻는다: 5MB 초과로 거절되는 사례를
 * 줄이고, 상세 화면이 원본 해상도를 그대로 서빙하지 않게 하고, 브라우저가 다시 인코딩하는
 * 과정에서 EXIF가 빠져 서버 쪽 제거(A-SEC-10)의 이중 방어가 된다.
 *
 * 원본과 같은 포맷으로 다시 인코딩한다(PNG→PNG, WEBP→WEBP) — 항상 JPEG로 바꾸면 투명
 * 배경이 있는 PNG의 알파가 사라지는데, 그걸 피하려고 알파 채널 유무를 따로 검사하는 대신
 * 포맷을 유지하는 쪽을 택했다. GIF(애니메이션 가능성)는 손대지 않는다.
 *
 * {@code createImageBitmap}의 {@code imageOrientation} 옵션을 지원하지 않는 구형 브라우저
 * (iOS 15 하한 근처)나 디코딩 실패 시에는 원본 파일을 그대로 돌려준다(점진적 향상) — 서버가
 * 어차피 5MB·픽셀 수 제한과 EXIF 제거를 최종적으로 처리한다.
 *
 * @param {File} original
 * @param {string} extension
 * @returns {Promise<File>}
 */
async function resizeIfNeeded(original, extension) {
    if (extension === 'gif' || typeof createImageBitmap !== 'function') {
        return original;
    }
    try {
        const bitmap = await createImageBitmap(original, { imageOrientation: 'from-image' });
        const scale = Math.min(1, MAX_DIMENSION / Math.max(bitmap.width, bitmap.height));
        if (scale >= 1) {
            bitmap.close?.();
            return original;
        }

        const width = Math.round(bitmap.width * scale);
        const height = Math.round(bitmap.height * scale);
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            bitmap.close?.();
            return original;
        }
        ctx.drawImage(bitmap, 0, 0, width, height);
        bitmap.close?.();

        const mimeType = extension === 'png' ? 'image/png' : extension === 'webp' ? 'image/webp' : 'image/jpeg';
        const blob = await new Promise((resolve) => canvas.toBlob(resolve, mimeType, RESIZE_QUALITY));
        if (!blob) {
            return original;
        }
        const resizedExtension = mimeType === 'image/png' ? 'png' : mimeType === 'image/webp' ? 'webp' : 'jpg';
        const resizedName = original.name.replace(/\.[^.]+$/, `.${resizedExtension}`);
        return new File([blob], resizedName, { type: mimeType });
    } catch {
        return original;
    }
}

/** @param {{ initialUrl?: string|null }} [options] */
export function useImageUpload({ initialUrl = null } = {}) {
    /** @type {import('vue').Ref<File|null>} */
    const file = ref(null);
    // onFileSelected가 비동기(축소 처리)라 빠르게 다른 파일을 다시 고르면 먼저 시작한
    // 축소가 나중에 끝나 방금 고른 파일을 덮어쓸 수 있다 — 매 호출마다 새 토큰을 발급해
    // 자신이 마지막 선택일 때만 결과를 반영한다.
    let currentSelectionToken = null;
    /** @type {import('vue').Ref<string|null>} */
    const previewUrl = ref(null);
    const removedExisting = ref(false);
    /** @type {import('vue').Ref<string|null>} */
    const uploadedUrl = ref(null);
    // 업로드 응답의 실제 픽셀 크기(A-FE-09) — resolveUrl()이 채운다. uploadedUrl과 항상 짝을
    // 이루므로 uploadedForFile 캐시 판정도 그대로 재사용한다(따로 무효화할 필요가 없다).
    /** @type {import('vue').Ref<number|null>} */
    const uploadedWidth = ref(null);
    /** @type {import('vue').Ref<number|null>} */
    const uploadedHeight = ref(null);
    const uploading = ref(false);
    /** @type {File|null} */
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
        uploadedWidth.value = null;
        uploadedHeight.value = null;
        uploadedForFile = null;
        revokePreview();
    }

    function removeExisting() {
        removedExisting.value = true;
    }

    /**
     * 파일 입력의 change 이벤트에서 선택된 File(또는 선택 해제 시 null)을 넘겨받는다.
     * 형식 검증을 통과하면 축소를 시도한 뒤(A-FE-06) 그 결과로 크기를 검사한다 — 축소
     * 덕분에 5MB를 넘던 원본도 통과할 수 있다.
     * @param {File|null} selectedFile
     */
    async function onFileSelected(selectedFile) {
        uploadedUrl.value = null;
        uploadedWidth.value = null;
        uploadedHeight.value = null;
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

        const selectionToken = Symbol('selection');
        currentSelectionToken = selectionToken;
        const resized = await resizeIfNeeded(selectedFile, extension);
        if (currentSelectionToken !== selectionToken) {
            // 축소가 끝나기 전에 사용자가 다른 파일을 또 골랐다 — 그 최신 선택이 이긴다.
            return;
        }

        if (resized.size > UPLOAD.MAX_BYTES) {
            flash.showError(UPLOAD_MESSAGES.TOO_LARGE);
            file.value = null;
            return;
        }

        flash.hide();
        // 새 파일을 고르면 기존 이미지는 교체 대상이므로, 전에 눌러 둔 "삭제"는 무시한다
        // (파일 우선 규칙).
        removedExisting.value = false;
        file.value = resized;
        previewUrl.value = URL.createObjectURL(resized);
    }

    /**
     * 선택한 파일이 있으면 업로드해 URL을 돌려준다. 파일이 없으면 null. 같은 파일을 이미
     * 올렸다면 다시 올리지 않고 기억해 둔 URL을 쓴다(저장 실패 후 재시도 시 중복 업로드 방지).
     * <p>
     * 업로드 시작 시점의 파일을 {@code fileAtStart}로 고정해 둔다 — {@code await} 도중 사용자가
     * 파일을 바꾸거나 선택을 해제하면 그 사이 {@code file.value}가 달라지는데, 응답이 온 뒤
     * 그 달라진 값을 기준으로 캐시를 쓰면 A의 응답이 B의 URL로 잘못 기억되거나 이미 취소된
     * 파일의 URL이 화면에 반영될 수 있었다(개선 보고서 "업로드 중 파일 교체로 URL 캐시가
     * 다른 파일에 연결될 수 있다"). 응답이 왔을 때 선택이 이미 바뀌었으면 캐시에 쓰지 않고
     * 그 응답을 버린다.
     */
    async function resolveUrl() {
        if (!file.value) {
            return null;
        }
        if (uploadedUrl.value && uploadedForFile === file.value) {
            return uploadedUrl.value;
        }

        const fileAtStart = file.value;
        const formData = new FormData();
        formData.append('file', fileAtStart);

        uploading.value = true;
        /** @type {UploadResponse} */
        let response;
        try {
            response = await api.upload(API.POST_IMAGES, formData);
        } finally {
            uploading.value = false;
        }

        if (file.value !== fileAtStart) {
            // 응답을 기다리는 동안 선택이 바뀌었다. 이 응답은 이제 화면의 선택과 무관하므로
            // 캐시에 반영하지 않는다 — 호출한 쪽은 다음 resolveUrl() 호출에서 현재 선택
            // 기준으로 다시 업로드하게 된다.
            return null;
        }

        uploadedUrl.value = response.url;
        uploadedWidth.value = response.width;
        uploadedHeight.value = response.height;
        uploadedForFile = fileAtStart;
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
        uploading,
        uploadedWidth,
        uploadedHeight,
        onFileSelected,
        clear,
        removeExisting,
        resolveUrl,
        revokePreview,
    };
}
