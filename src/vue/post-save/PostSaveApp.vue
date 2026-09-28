<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import * as flash from '@ui/flash.js';
import { useImageUpload } from '../shared/useImageUpload.js';
import { useUnsavedGuard } from '../shared/useUnsavedGuard.js';
import { useDraftAutosave } from '../shared/useDraftAutosave.js';
import { clearDraft, safeLocalStorage } from '../shared/draftStorage.js';
import { useFieldErrors } from '../shared/useFieldErrors.js';
import MarkdownToolbar from '../shared/MarkdownToolbar.vue';
import DraftRestoreBanner from '../shared/DraftRestoreBanner.vue';

/**
 * 게시글 등록 화면.
 *
 * 제목·내용의 필수 검사는 HTML `required`에 맡긴다 — 브라우저가 submit 이벤트 자체를 막으므로
 * JS로 한 번 더 검사하면 도달하지 않는 코드가 된다.
 *
 * 분류 선택지는 서버가 CategoryPolicy로 계산해 내려준다(관리자가 아니면 공지 없음). 화면에서
 * 거르지 않는 이유는 편집 화면과 같다 — 실제 경계는 저장 요청에서 서버가 잡는다.
 */
const props = defineProps({
    categoryOptions: { type: /** @type {import('vue').PropType<import('../shared/types.js').CategoryOption[]>} */ (Array), required: true },
    author: { type: String, required: true },
    // 자동 임시 저장 키를 계정별로 분리하는 데만 쓴다(전체 리뷰 2026-09-26 A-FE-03). 이
    // 화면은 로그인이 필수라 실제로는 항상 값이 있다. null이면(value == null이라 Vue가 타입
    // 검사를 건너뛴다) 아래에서 사용자 구분 없는 키로 물러서지 않고 그냥 초안 기능을 끈다.
    userId: { type: Number, default: null },
});

const draft = reactive({
    title: '',
    content: '',
    category: props.categoryOptions[0]?.value ?? 'FREE',
});
const progressText = ref(/** @type {string | null} */ (null));
const saving = ref(false);

const picture = useImageUpload();
const fileInput = ref(/** @type {HTMLInputElement | null} */ (null));

// 새 글 작성에는 예전에 이탈 방지가 아예 없었다(FE-18) — 다 쓴 글을 실수로 새로고침하거나
// 탭을 닫으면 아무 경고 없이 사라졌다. PostEditApp과 같은 규칙: 제목·내용·분류 중 하나라도
// 비어 있지 않거나 사진을 선택했으면 "작성 중"으로 본다.
const isDirty = computed(() =>
    draft.title !== '' || draft.content !== '' || picture.hasFile.value,
);
const unsavedGuard = useUnsavedGuard(isDirty);

// 자동 임시 저장(이탈 경고를 대체하지 않고 나란히 쓴다 — useDraftAutosave.js 참고). 사진은
// 직렬화할 수 없어 제목·분류·내용만 담는다.
//
// 키에 회원 id를 넣는다(전체 리뷰 2026-09-26 A-FE-03) — 예전 키(kraft:draft:post-save)는
// 사용자 구분이 없어, 공용 PC에서 A가 쓰다 만 초안이 그 브라우저로 로그인한 B의 글쓰기
// 화면에 그대로 떴다. userId가 없으면(이 화면은 로그인이 필수라 실제로는 일어나지 않는다)
// storage를 null로 둬 초안 기능 자체를 건너뛴다.
const titleInput = ref(/** @type {HTMLInputElement | null} */ (null));
/** @type {import('vue').Ref<InstanceType<typeof MarkdownToolbar> | null>} */
const contentInput = ref(null);
const { fieldErrors, apply: applyFieldErrors } = useFieldErrors();
const LEGACY_DRAFT_KEY = 'kraft:draft:post-save';
const draftKey = props.userId != null ? `kraft:draft:${props.userId}:post-save` : LEGACY_DRAFT_KEY;
const autosave = useDraftAutosave(draftKey, draft, {
    storage: props.userId != null ? undefined : null,
});

onMounted(() => {
    // 빈 초안(제목·내용 둘 다 없음)은 되찾을 게 없으니 배너를 띄우지 않는다.
    autosave.checkAvailable((stored) => !stored.title && !stored.content);
    // 옛(사용자 구분 없는) 키에 남아 있을 수 있는 초안은 지운다 — 다음 사용자에게 보이지
    // 않게 한다. 지금 쓸 키가 바로 그 옛 키이면(userId 없음) 건드리지 않는다.
    if (props.userId != null) {
        clearDraft(safeLocalStorage(), LEGACY_DRAFT_KEY);
    }
});

watch(draft, () => autosave.schedule(), { deep: true });

function restoreDraft() {
    autosave.restore((stored) => {
        draft.title = stored.title ?? '';
        draft.content = stored.content ?? '';
        if (stored.category) {
            draft.category = stored.category;
        }
    });
    titleInput.value?.focus();
}

function discardDraft() {
    autosave.discard();
    titleInput.value?.focus();
}

function onFileChange(event) {
    picture.onFileSelected(event.target.files?.[0] ?? null);
}

function clearPicture() {
    picture.clear();
    // 파일 입력의 값을 비워야 같은 파일을 다시 골라도 change 이벤트가 또 일어난다.
    if (fileInput.value) {
        fileInput.value.value = '';
    }
}

// PostEditApp.vue의 onSubmit과 진행 문구·재시도 안내 문구가 비슷하게 반복된다. 업로드
// 재사용(재시도해도 같은 파일을 다시 올리지 않는 캐시)의 핵심 로직은 useImageUpload.js의
// resolveUrl()에 이미 공유되어 있어, 여기 남은 것은 POST 요청 모양과 진행 문구 순서뿐이다.
// PostEditApp은 PUT·버전 충돌 처리가 추가로 있어 모양이 달라, 억지로 하나로 합치면 서로
// 다른 두 흐름을 무리하게 끼워 맞추게 된다(개선 보고서 검토, 2026-09-26) — 세 번째 호출부가
// 생기거나 두 흐름이 더 벌어지기 전까지는 의도적으로 중복을 유지한다.
async function onSubmit() {
    if (saving.value) {
        return;
    }
    saving.value = true;

    // 업로드를 기다리는 동안 입력을 잠그지만(:disabled="saving"), 편집 화면과 동일하게 제출
    // 시점 값을 한 번 더 스냅샷으로 고정해 둔다 — 최종 요청은 항상 이 스냅샷을 쓴다(개선
    // 보고서 F03).
    const snapshot = { title: draft.title, content: draft.content, category: draft.category };

    let pictureUrl = null;
    try {
        if (picture.hasFile.value) {
            progressText.value = '이미지 업로드 중…';
            pictureUrl = await picture.resolveUrl();
        }
    } catch (error) {
        progressText.value = null;
        saving.value = false;
        flash.showError(`이미지 업로드에 실패했습니다. ${messageOf(error)}`);
        return;
    }

    progressText.value = '게시글 등록 중…';
    try {
        const newPostId = await api.post(API.POSTS, {
            title: snapshot.title,
            content: snapshot.content,
            picture: pictureUrl,
            pictureWidth: pictureUrl ? picture.uploadedWidth.value : null,
            pictureHeight: pictureUrl ? picture.uploadedHeight.value : null,
            category: snapshot.category,
        });
        picture.revokePreview();
        flash.set('POST_SAVED');
        unsavedGuard.allowNavigation();
        autosave.discard();
        // 목록 첫 페이지가 아니라 방금 쓴 글로 이동한다(전체 리뷰 2026-09-26 A-FE-02) —
        // 등록 API가 새 글 id를 그대로 돌려준다.
        window.location.href = `/posts/update/${newPostId}`;
    } catch (error) {
        progressText.value = null;
        saving.value = false;
        // 긴 글을 쓰는 동안 세션이 끊기면 이 시점에야 403/로그인 리다이렉트로 드러난다
        // (A-FE-12). 자동 임시 저장이 내용을 지키고 있으니 그 사실부터 알린다 — 일반 오류
        // 문구("권한이 없거나...")만으로는 다음에 뭘 해야 할지 분명하지 않았다.
        if (error?.kind === 'forbidden' || error?.kind === 'auth') {
            flash.showError('로그인이 만료되었습니다. 작성 중인 내용은 임시 저장되어 있으니, 다시 로그인한 뒤 이어서 쓸 수 있습니다.');
            return;
        }
        const handledByField = await applyFieldErrors(error, { title: titleInput, content: contentInput });
        if (handledByField) {
            return;
        }
        // 업로드까지는 끝났다는 사실을 알려야 사용자가 파일을 다시 고르지 않는다.
        const retryHint = pictureUrl
            ? ' 이미지는 이미 업로드되어 있으니 다시 "등록"을 누르면 같은 이미지로 재시도합니다.'
            : '';
        flash.showError(`게시글 등록에 실패했습니다. ${messageOf(error)}${retryHint}`);
    }
}
</script>

<template>
  <form
    id="post-save-form"
    @submit.prevent="onSubmit"
  >
    <!-- 자동 임시 저장된 초안이 있으면 물어보고 선택하게 한다(자동 복원 아님). -->
    <DraftRestoreBanner
      :available="autosave.available.value"
      @restore="restoreDraft"
      @discard="discardDraft"
    />

    <div class="mb-3">
      <label for="title">제목</label>
      <input
        id="title"
        ref="titleInput"
        v-model="draft.title"
        type="text"
        class="form-control"
        :class="{ 'is-invalid': fieldErrors.title }"
        :aria-invalid="fieldErrors.title ? 'true' : undefined"
        placeholder="제목을 입력하세요"
        maxlength="255"
        aria-describedby="title-error"
        required
        :disabled="saving"
      >
      <div
        id="title-error"
        class="invalid-feedback"
      >
        {{ fieldErrors.title }}
      </div>
    </div>
    <!-- 닉네임을 보여준다. 값은 서버가 principal에서 꺼내 내려준 것이고, 저장 요청에는
         담지 않는다 — 작성자는 서버가 로그인 계정으로 정한다. 입력할 것이 없는 값이라
         채울 단계(mb-3 입력 필드)가 아니라 제목 아래 안내문구로만 보여준다. -->
    <p class="post-form__byline text-muted">
      작성자: {{ author }} · 로그인 계정으로 자동 지정됩니다.
    </p>
    <div class="mb-3">
      <label for="category">분류</label>
      <select
        id="category"
        v-model="draft.category"
        class="form-select"
        :disabled="saving"
      >
        <option
          v-for="option in categoryOptions"
          :key="option.value"
          :value="option.value"
        >
          {{ option.title }}
        </option>
      </select>
    </div>
    <div class="mb-3">
      <label for="content">내용</label>
      <MarkdownToolbar
        id="content"
        ref="contentInput"
        v-model="draft.content"
        placeholder="내용을 입력하세요"
        :maxlength="10000"
        :disabled="saving"
        :aria-invalid="fieldErrors.content ? 'true' : undefined"
        aria-describedby="content-error"
      />
      <div
        v-if="fieldErrors.content"
        id="content-error"
        class="invalid-feedback d-block"
      >
        {{ fieldErrors.content }}
      </div>
    </div>
    <div class="mb-3">
      <label for="picture">사진</label>
      <input
        id="picture"
        ref="fileInput"
        type="file"
        class="form-control"
        accept="image/jpeg,image/png,image/gif,image/webp"
        aria-describedby="picture-help"
        :disabled="saving || picture.uploading.value"
        @change="onFileChange"
      >
      <small
        id="picture-help"
        class="form-text text-muted"
      >JPG, JPEG, PNG, GIF, WEBP · 최대 5MB · 1개</small>
    </div>

    <!-- 선택한 파일의 이름·크기·로컬 미리보기. -->
    <div
      v-show="picture.hasFile.value"
      id="picture-preview"
      class="picture-preview"
    >
      <img
        id="picture-preview-image"
        class="picture-preview__image"
        :src="picture.previewUrl.value ?? undefined"
        alt="선택한 이미지 미리보기"
      >
      <div class="picture-preview__meta">
        <p
          id="picture-preview-name"
          class="picture-preview__name"
        >
          {{ picture.fileLabel.value }}
        </p>
        <button
          id="btn-picture-clear"
          type="button"
          class="btn btn-sm btn-outline-secondary"
          :disabled="saving || picture.uploading.value"
          @click="clearPicture"
        >
          선택 해제
        </button>
      </div>
    </div>

    <!-- 업로드→저장 진행 상태. 평소에는 비어 있고 진행 중에만 보인다. -->
    <p
      v-show="progressText"
      id="post-save-progress"
      class="form-progress"
      aria-live="polite"
    >
      {{ progressText }}
    </p>

    <div class="btn-group-gap">
      <a
        href="/"
        class="btn btn-secondary"
      >취소</a>
      <button
        id="btn-save"
        type="submit"
        class="btn btn-primary"
        :disabled="saving"
      >
        {{ saving ? '등록 중…' : '등록' }}
      </button>
    </div>
  </form>
</template>
