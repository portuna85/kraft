<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import * as flash from '@ui/flash.js';
import { showToast } from '@ui/toast.js';
import { useImageUpload } from '../shared/useImageUpload.js';
import { useUnsavedGuard } from '../shared/useUnsavedGuard.js';
import { useDraftAutosave } from '../shared/useDraftAutosave.js';
import MarkdownBody from '../shared/MarkdownBody.vue';
import MarkdownToolbar from '../shared/MarkdownToolbar.vue';

/**
 * 게시글 읽기·편집·추천 상태를 관리한다. 추천은 서버가 반환한 상태만 반영한다.
 */
const props = defineProps({
    post: { type: /** @type {import('vue').PropType<import('../shared/types.js').PostViewDto>} */ (Object), required: true },
    categoryOptions: { type: /** @type {import('vue').PropType<import('../shared/types.js').CategoryOption[]>} */ (Array), required: true },
    authenticated: { type: Boolean, required: true },
});

// 로그인 후 이 글로 돌아오게 한다(FE-16) — navbar의 로그인 링크와 같은 규칙
// (NavModelAdvice.currentPath)이다. 예전에는 href="/login"만 써서 로그인 뒤 홈으로 떨어졌다.
const loginHref = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;

const mode = ref('view'); // 'view' | 'edit'
const original = reactive({
    title: props.post.title,
    content: props.post.content,
    category: props.post.category,
});
const draft = reactive({ ...original });
const version = ref(props.post.version);
const progressText = ref(/** @type {string | null} */ (null));
const saving = ref(false);
const liked = ref(props.post.likedByMe);
const likeCount = ref(props.post.likeCount);
const liking = ref(false);

const picture = useImageUpload({ initialUrl: props.post.picture });

const titleInput = ref(/** @type {HTMLInputElement | null} */ (null));
const editButton = ref(/** @type {HTMLButtonElement | null} */ (null));
const fileInput = ref(/** @type {HTMLInputElement | null} */ (null));

// 글자크기 조절: 3단계(작게/보통/크게), 세션을 넘어 유지하도록 localStorage에 기억한다.
// localStorage 접근이 막힌 환경(프라이빗 모드 등)에서도 화면은 기본값으로 그대로 동작해야
// 하므로 읽기·쓰기 모두 조용히 실패를 삼킨다.
const FONT_SCALE_STORAGE_KEY = 'kraft:post-font-scale';
const FONT_SCALES = ['0.875rem', '1rem', '1.125rem'];
const DEFAULT_FONT_SCALE_INDEX = 1;

function readStoredFontScaleIndex() {
    try {
        const raw = window.localStorage.getItem(FONT_SCALE_STORAGE_KEY);
        if (raw === null) {
            return DEFAULT_FONT_SCALE_INDEX;
        }
        const stored = Number(raw);
        return Number.isInteger(stored) && stored >= 0 && stored < FONT_SCALES.length
            ? stored
            : DEFAULT_FONT_SCALE_INDEX;
    } catch {
        return DEFAULT_FONT_SCALE_INDEX;
    }
}

const fontScaleIndex = ref(readStoredFontScaleIndex());
const postBodyStyle = computed(() => ({ '--kraft-post-font-size': FONT_SCALES[fontScaleIndex.value] }));

function setFontScaleIndex(index) {
    fontScaleIndex.value = index;
    try {
        window.localStorage.setItem(FONT_SCALE_STORAGE_KEY, String(index));
    } catch {
        // 저장 실패는 이번 열람에서만 크기가 적용되는 정도로 넘어간다.
    }
}

async function shareLink() {
    try {
        await navigator.clipboard.writeText(window.location.href);
        showToast('링크를 복사했습니다.', 'success');
    } catch {
        showToast('링크 복사에 실패했습니다. 주소창의 URL을 직접 복사해 주세요.', 'danger');
    }
}

async function setLike() {
    if (liking.value) {
        return;
    }
    liking.value = true;
    try {
        const result = await api.put(`${API.POSTS}/${props.post.id}/like`, { liked: !liked.value });
        liked.value = result.liked;
        likeCount.value = result.likeCount;
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        liking.value = false;
    }
}

// 과거에 분류 필드가 여기서 빠져 있던 적이 있다(F12 회귀). 필드를 하나씩 나열하는 대신
// original/draft의 키를 순회해서, 필드가 늘어나도 비교에서 빠지는 일이 구조적으로 없게 한다.
const isDirty = computed(() =>
    Object.keys(original).some((key) => draft[key] !== original[key])
    || picture.removedExisting.value
    || picture.hasFile.value,
);

function categoryTitle(value) {
    return props.categoryOptions.find((option) => option.value === value)?.title ?? value;
}

/**
 * 편집 중 브라우저 탭을 닫거나 다른 주소로 이동하면(뒤로 가기 포함) 입력한 내용이 그대로
 * 사라진다(F05) — "취소" 버튼은 confirm()으로 막지만, 그 경로 밖의 이탈은 아무 안내도 없었다.
 * 저장에 성공해 스스로 이동할 때는 이 확인을 띄우지 않는다(unsavedGuard.allowNavigation()).
 */
const unsavedGuard = useUnsavedGuard(isDirty);

// 자동 임시 저장(이탈 경고를 대체하지 않고 나란히 쓴다 — useDraftAutosave.js 참고). 사진은
// 직렬화할 수 없어 제목·분류·내용만 담는다. 글마다 따로 기억하도록 키에 id를 넣는다.
const autosave = useDraftAutosave(`kraft:draft:post-edit:${props.post.id}`, draft);

// 편집 모드일 때만 저장한다 — 조회 모드에서는 draft가 항상 original과 같아 저장할 이유가
// 없고, 마운트 시점에 곧바로 저장소를 건드리지도 않는다.
watch(draft, () => {
    if (mode.value === 'edit') {
        autosave.schedule();
    }
}, { deep: true });

function restoreDraft() {
    autosave.restore((stored) => {
        draft.title = stored.title ?? original.title;
        draft.content = stored.content ?? original.content;
        if (stored.category) {
            draft.category = stored.category;
        }
    });
    titleInput.value?.focus();
}

async function startEdit() {
    mode.value = 'edit';
    // 서버 원본과 같은 초안은 되찾을 게 없으니 배너를 띄우지 않는다.
    autosave.checkAvailable((stored) =>
        stored.title === original.title && stored.content === original.content && stored.category === original.category,
    );
    await nextTick();
    titleInput.value?.focus();
}

async function cancelEdit() {
    if (isDirty.value && !window.confirm('변경한 내용을 버리시겠습니까?')) {
        return;
    }
    draft.title = original.title;
    draft.content = original.content;
    draft.category = original.category;
    clearPicture();
    picture.removedExisting.value = false;
    mode.value = 'view';
    autosave.discard();
    await nextTick();
    editButton.value?.focus();
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

// PostSaveApp.vue의 onSubmit과 진행 문구·재시도 안내 문구가 비슷하게 반복된다 — 의도적으로
// 유지하는 이유는 그 파일의 같은 주석 참고(업로드 재사용은 useImageUpload.js에 이미 공유,
// 여기 남은 차이는 PUT·버전 충돌 처리).
async function onSubmit() {
    if (saving.value) {
        return;
    }
    saving.value = true;

    // 업로드를 기다리는 동안 입력을 잠그지만(:disabled="saving"), PostSaveApp과 동일하게
    // 제출 시점 값을 한 번 더 스냅샷으로 고정해 둔다 — 최종 요청은 항상 이 스냅샷을 쓴다(F02).
    const snapshot = { title: draft.title, content: draft.content, category: draft.category, version: version.value };

    let pictureUrl;
    try {
        if (picture.hasFile.value) {
            progressText.value = '이미지 업로드 중…';
            pictureUrl = await picture.resolveUrl();
        } else if (picture.removedExisting.value) {
            pictureUrl = null;
        } else {
            pictureUrl = props.post.picture || null;
        }
    } catch (error) {
        progressText.value = null;
        saving.value = false;
        flash.showError(`이미지 업로드에 실패했습니다. ${messageOf(error)}`);
        return;
    }

    progressText.value = '게시글 저장 중…';
    try {
        await api.put(`${API.POSTS}/${props.post.id}`, {
            title: snapshot.title,
            content: snapshot.content,
            picture: pictureUrl,
            category: snapshot.category,
            // 편집을 시작할 때 받아간 버전. 그 사이 다른 곳에서 저장됐으면 서버가 409로 거절한다.
            version: snapshot.version,
        });
        picture.revokePreview();
        flash.set('POST_UPDATED');
        unsavedGuard.allowNavigation();
        autosave.discard();
        window.location.href = '/';
    } catch (error) {
        progressText.value = null;
        saving.value = false;
        const retryHint = pictureUrl
            ? ' 이미지는 이미 업로드되어 있으니 다시 "저장"을 누르면 같은 이미지로 재시도합니다.'
            : '';
        flash.showError(`게시글 저장에 실패했습니다. ${messageOf(error)}${retryHint}`);
    }
}
</script>

<template>
  <article
    v-show="mode === 'view'"
    id="post-view"
  >
    <span class="post-category">{{ categoryTitle(post.category) }}</span>
    <h1
      id="post-title-text"
      class="post-title"
    >
      {{ post.title }}
    </h1>
    <p class="post-byline">
      <span>{{ post.author }}</span> · 글 번호 <span>{{ post.id }}</span>
      · 조회 <span>{{ post.viewCount }}</span>
    </p>

    <div
      class="post-font-controls"
      role="group"
      aria-label="글자 크기 조절"
    >
      <button
        v-for="(item, index) in [
          { label: '가-', name: '글자 작게' },
          { label: '가', name: '글자 보통' },
          { label: '가+', name: '글자 크게' },
        ]"
        :key="item.label"
        type="button"
        class="btn btn-sm btn-outline-secondary post-font-controls__btn"
        :class="{ 'is-active': fontScaleIndex === index }"
        :aria-pressed="fontScaleIndex === index"
        :aria-label="item.name"
        @click="setFontScaleIndex(index)"
      >
        {{ item.label }}
      </button>
    </div>

    <div
      id="post-content-text"
      class="post-body post-body--md"
      :style="postBodyStyle"
    >
      <MarkdownBody :source="post.content" />
    </div>

    <div
      v-if="post.picture"
      class="post-image"
    >
      <img
        :src="post.picture"
        alt="게시글 첨부 이미지"
        loading="lazy"
        decoding="async"
      >
    </div>

    <div class="btn-group-gap post-actions">
      <button
        id="btn-share"
        type="button"
        class="btn btn-sm btn-outline-secondary"
        @click="shareLink"
      >
        공유
      </button>
    </div>

    <div
      v-if="authenticated"
      class="btn-group-gap post-actions"
    >
      <button
        id="btn-like"
        type="button"
        class="btn btn-outline-primary"
        :class="{ 'is-active': liked }"
        :aria-pressed="liked"
        :disabled="liking"
        @click="setLike"
      >
        추천 <span id="like-count">{{ likeCount }}</span>
      </button>
    </div>
    <p
      v-else
      v-once
      class="post-actions"
    >
      추천 <span>{{ post.likeCount }}</span>개 ·
      <a :href="loginHref">로그인 후 추천할 수 있습니다.</a>
    </p>

    <!-- 신고는 남의 글에만 보인다. 자기 글은 서버도 거절한다(직접 지우면 된다). -->
    <div
      v-if="authenticated && !post.canManagePost"
      class="btn-group-gap post-actions"
    >
      <button
        id="btn-report-post"
        type="button"
        class="btn btn-sm btn-outline-secondary"
        data-report-kind="post"
      >
        신고
      </button>
    </div>

    <div
      v-if="post.canManagePost"
      class="btn-group-gap post-actions"
    >
      <button
        id="btn-edit"
        ref="editButton"
        type="button"
        class="btn btn-outline-primary"
        @click="startEdit"
      >
        수정
      </button>
      <button
        id="btn-delete-post"
        type="button"
        class="btn btn-outline-danger"
        data-target-kind="post"
        :data-target-name="post.title"
      >
        삭제
      </button>
    </div>
  </article>

  <form
    v-if="post.canManagePost"
    v-show="mode === 'edit'"
    id="post-edit"
    class="card-kraft post-edit"
    @submit.prevent="onSubmit"
  >
    <!-- 자동 임시 저장된 초안이 있으면 물어보고 선택하게 한다(자동 복원 아님). -->
    <div
      v-if="autosave.available.value"
      id="draft-restore-banner"
      class="draft-banner"
    >
      <p class="draft-banner__text">
        임시 저장된 내용이 있습니다. 사진 첨부는 복원되지 않아 다시 선택해야 합니다.
      </p>
      <div class="draft-banner__actions">
        <button
          id="btn-draft-restore"
          type="button"
          class="btn btn-sm btn-outline-primary"
          @click="restoreDraft"
        >
          복원
        </button>
        <button
          id="btn-draft-discard"
          type="button"
          class="btn btn-sm btn-outline-secondary"
          @click="autosave.discard()"
        >
          새로 시작
        </button>
      </div>
    </div>

    <div class="mb-3">
      <label for="title">제목</label>
      <input
        id="title"
        ref="titleInput"
        v-model="draft.title"
        type="text"
        class="form-control"
        maxlength="255"
        required
        :disabled="saving"
      >
    </div>
    <div class="mb-3">
      <label for="edit-category">분류</label>
      <select
        id="edit-category"
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
        v-model="draft.content"
        :maxlength="10000"
        :disabled="saving"
      />
    </div>
    <div class="mb-3">
      <label for="edit-picture">사진</label>
      <input
        id="edit-picture"
        ref="fileInput"
        type="file"
        class="form-control"
        accept="image/jpeg,image/png,image/gif,image/webp"
        aria-describedby="edit-picture-help"
        :disabled="saving || picture.uploading.value"
        @change="onFileChange"
      >
      <small
        id="edit-picture-help"
        class="form-text text-muted"
      >JPG, JPEG, PNG, GIF, WEBP · 최대 5MB · 1개. 새 파일을 선택하면 기존 이미지를 대체합니다.</small>
    </div>

    <!-- 기존 이미지(교체할 파일을 아직 선택하지 않았을 때만 표시). -->
    <div
      v-if="picture.showExistingPreview.value"
      id="edit-picture-current"
      class="picture-preview"
    >
      <img
        class="picture-preview__image"
        :src="post.picture ?? undefined"
        alt="현재 첨부된 이미지"
      >
      <div class="picture-preview__meta">
        <p class="picture-preview__name">
          현재 이미지
        </p>
        <button
          id="btn-edit-picture-remove"
          type="button"
          class="btn btn-sm btn-outline-danger"
          :disabled="saving || picture.uploading.value"
          @click="picture.removeExisting()"
        >
          이미지 삭제
        </button>
      </div>
    </div>

    <!-- 새로 선택한 파일의 로컬 미리보기. -->
    <div
      v-show="picture.hasFile.value"
      id="edit-picture-preview"
      class="picture-preview"
    >
      <img
        id="edit-picture-preview-image"
        class="picture-preview__image"
        :src="picture.previewUrl.value ?? undefined"
        alt="선택한 이미지 미리보기"
      >
      <div class="picture-preview__meta">
        <p
          id="edit-picture-preview-name"
          class="picture-preview__name"
        >
          {{ picture.fileLabel.value }}
        </p>
        <button
          id="btn-edit-picture-clear"
          type="button"
          class="btn btn-sm btn-outline-secondary"
          :disabled="saving || picture.uploading.value"
          @click="clearPicture"
        >
          선택 해제
        </button>
      </div>
    </div>

    <!-- v-show(display:none) 대신 항상 렌더링한 채 텍스트만 바꾼다(FE-16) — 라이브 리전이
         display:none 상태였다가 나타나는 것과 동시에 내용이 채워지면, 스크린 리더 구현에
         따라 그 변화를 놓칠 수 있다. 비어 있을 때는 내용이 없어 화면에 거의 티가 나지
         않는다. -->
    <p
      id="post-update-progress"
      class="form-progress"
      aria-live="polite"
    >
      {{ progressText }}
    </p>

    <div class="btn-group-gap">
      <button
        id="btn-cancel-edit"
        type="button"
        class="btn btn-secondary"
        :disabled="saving"
        @click="cancelEdit"
      >
        취소
      </button>
      <button
        id="btn-update"
        type="submit"
        class="btn btn-primary"
        :disabled="saving"
      >
        {{ saving ? '저장 중…' : '저장' }}
      </button>
    </div>
  </form>
</template>
