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
 * 게시글 등록·편집 화면이 함께 쓰는 이미지 첨부 상태. 기존 이미지(initialUrl)는 편집 화면에만 있고 등록 화면은 인자 없이 호출한다(그때 showExistingPreview는 항상 false).
 *
 * 검증 상수(UPLOAD/UPLOAD_MESSAGES)는 core/constants.js에서 그대로 가져온다 — 서버의 UploadPolicySyncTest가 형태를 정규식으로 파싱해 서버 정책과 동기화하므로 복제하거나 옮기면 깨진다.
 * "손대지 않음 / 새 파일 선택 / 기존 삭제" 세 상태는 hasFile·removedExisting 두 ref의 조합으로 showExistingPreview가 파생된다. 업로드 재사용(재시도 시 같은 파일을 다시 올리지 않는 캐시)은 resolveUrl()에만 있다.
 */
/** 이보다 긴 변을 가진 이미지만 줄인다. 이미 작은 이미지를 다시 인코딩할 이유는 없다. */
const MAX_DIMENSION = 2048;
const RESIZE_QUALITY = 0.85;

/**
 * 캔버스로 다시 그려 축소한다: 5MB 초과 거절을 줄이고, 상세 화면이 원본 해상도를 서빙하지 않게 하며, 재인코딩으로 EXIF가 빠져 서버 제거의 이중 방어가 된다.
 * 원본과 같은 포맷으로 다시 인코딩한다(PNG→PNG, WEBP→WEBP) — JPEG로 통일하면 투명 PNG의 알파가 사라진다. GIF(애니메이션 가능성)는 손대지 않는다.
 * {@code createImageBitmap}의 {@code imageOrientation}을 지원하지 않는 구형 브라우저(iOS 15 하한 근처)나 디코딩 실패 시에는 원본을 그대로 돌려준다
 * (점진적 향상 — 서버가 5MB·픽셀 수 제한과 EXIF 제거를 최종 처리한다).
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
    // 축소가 비동기라 빠르게 다른 파일을 고르면 먼저 시작한 축소가 나중에 끝나 방금 고른 파일을 덮을 수 있다 — 호출마다 새 토큰을 발급해 마지막 선택일 때만 반영한다.
    let currentSelectionToken = null;
    /** @type {import('vue').Ref<string|null>} */
    const previewUrl = ref(null);
    const removedExisting = ref(false);
    /** @type {import('vue').Ref<string|null>} */
    const uploadedUrl = ref(null);
    // 업로드 응답의 실제 픽셀 크기(resolveUrl이 채우며 uploadedUrl과 짝이라 uploadedForFile 캐시 판정을 재사용한다).
    /** @type {import('vue').Ref<number|null>} */
    const uploadedWidth = ref(null);
    /** @type {import('vue').Ref<number|null>} */
    const uploadedHeight = ref(null);
    const uploading = ref(false);
    // 파일을 골랐지만 축소가 끝나지 않아 file이 비어 있는 구간 — 이때 제출하면 사진 없이 저장되므로 화면이 제출 버튼을 막을 수 있게 노출한다.
    const processing = ref(false);
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
     * 파일 입력의 change 이벤트에서 선택된 File(해제 시 null)을 넘겨받는다. 형식 검증을 통과하면 축소를 시도한 뒤 그 결과로 크기를 검사한다 — 축소 덕에 5MB를 넘던 원본도 통과할 수 있다.
     * @param {File|null} selectedFile
     */
    async function onFileSelected(selectedFile) {
        // 어떤 경로로 끝나든 이전 선택의 진행 중 축소는 무효다 — 먼저 시작한 축소가 끝나면서 file을 되살리지 못하게 토큰도 비운다.
        currentSelectionToken = null;
        processing.value = false;
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
        processing.value = true;
        const resized = await resizeIfNeeded(selectedFile, extension);
        if (currentSelectionToken !== selectionToken) {
            // 축소가 끝나기 전에 다른 파일을 또 골랐다 — 최신 선택이 이기고 processing은 그 호출이 관리한다.
            return;
        }
        processing.value = false;

        if (resized.size > UPLOAD.MAX_BYTES) {
            flash.showError(UPLOAD_MESSAGES.TOO_LARGE);
            file.value = null;
            return;
        }

        flash.hide();
        // 새 파일을 고르면 기존 이미지는 교체 대상이라 전에 눌러 둔 "삭제"는 무시한다(파일 우선).
        removedExisting.value = false;
        file.value = resized;
        previewUrl.value = URL.createObjectURL(resized);
    }

    /**
     * 선택한 파일이 있으면 업로드해 URL을 돌려준다(없으면 null). 같은 파일을 이미 올렸다면 기억한 URL을 쓴다(저장 실패 후 재시도 시 중복 업로드 방지).
     * <p>
     * 업로드 시작 시점의 파일을 {@code fileAtStart}로 고정한다 — {@code await} 도중 선택이 바뀌면 응답이 다른 파일의 URL로 기억되거나 취소된 파일의 URL이 반영될 수 있어,
     * 응답이 왔을 때 선택이 이미 바뀌었으면 캐시에 쓰지 않고 응답을 버린다.
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
            // 응답을 기다리는 동안 선택이 바뀌었다 — 이 응답은 화면의 선택과 무관하므로 캐시하지 않고, 호출한 쪽이 현재 선택 기준으로 다시 올린다.
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
        processing,
        uploadedWidth,
        uploadedHeight,
        onFileSelected,
        clear,
        removeExisting,
        resolveUrl,
        revokePreview,
    };
}
