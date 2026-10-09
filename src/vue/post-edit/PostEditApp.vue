<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { api } from '@core/http.js';
import { API, UPLOAD_MESSAGES } from '@core/constants.js';
import { ifMatchHeaders } from '@core/etag.js';
import * as flash from '@ui/flash.js';
import { useImageUpload } from '../shared/useImageUpload.js';
import { useUnsavedGuard } from '../shared/useUnsavedGuard.js';
import { useDraftAutosave } from '../shared/useDraftAutosave.js';
import { clearDraft, safeLocalStorage } from '../shared/draftStorage.js';
import { useFieldErrors } from '../shared/useFieldErrors.js';
import { usePostSubmit } from '../shared/usePostSubmit.js';
import { useSessionKeepAlive } from '../shared/useSessionKeepAlive.js';
import PostFormFields from '../shared/PostFormFields.vue';
import DraftRestoreBanner from '../shared/DraftRestoreBanner.vue';
import PostView from './PostView.vue';

/**
 * 게시글 보기와 편집 전환. 읽기 화면은 PostView, 입력 칸은 PostFormFields, 사진 업로드부터
 * 오류 안내까지의 제출 흐름은 usePostSubmit이 맡고(등록 화면과 공유), 여기에는 편집 상태·초안·
 * 이탈 방지·수정 요청만 남는다.
 */
const props = defineProps({
    post: { type: /** @type {import('vue').PropType<import('../shared/types.js').PostViewDto>} */ (Object), required: true },
    categoryOptions: { type: /** @type {import('vue').PropType<import('../shared/types.js').CategoryOption[]>} */ (Array), required: true },
    authenticated: { type: Boolean, required: true },
    // 자동 임시 저장 키를 계정별로 분리하는 데만 쓴다. 편집
    // 폼 자체가 로그인·소유권을 요구하므로 실제로는 항상 값이 있다. null이면(value == null이라
    // Vue가 타입 검사를 건너뛴다) 아래에서 초안 기능을 끈다.
    userId: { type: Number, default: null },
});

const mode = ref('view'); // 'view' | 'edit'
// 편집 폼은 처음 편집을 누를 때 마운트한다. 글을 읽기만 하는 방문자(작성자 본인 포함)에게
// 툴바·미리보기·사진 입력까지 달린 폼 전체를 매번 만들 이유가 없다. 한 번 열린 뒤에는 v-show로
// 유지해 입력 중이던 내용이 보기/편집을 오가도 사라지지 않는다.
const editMounted = ref(false);
const original = reactive({
    title: props.post.title,
    content: props.post.content,
    category: props.post.category,
});
const draft = reactive({ ...original });
const version = ref(props.post.version);
const progressText = ref(/** @type {string | null} */ (null));
const saving = ref(false);

const picture = useImageUpload({ initialUrl: props.post.picture });

const postView = ref(/** @type {InstanceType<typeof PostView> | null} */ (null));
const form = ref(/** @type {InstanceType<typeof PostFormFields> | null} */ (null));
const { fieldErrors, apply: applyFieldErrors, clearOnEdit } = useFieldErrors();
clearOnEdit(draft);

// 과거에 분류 필드가 여기서 빠져 있던 적이 있다(회귀). 필드를 하나씩 나열하는 대신
// original/draft의 키를 순회해서, 필드가 늘어나도 비교에서 빠지는 일이 구조적으로 없게 한다.
const isDirty = computed(() =>
    Object.keys(original).some((key) => draft[key] !== original[key])
    || picture.removedExisting.value
    || picture.hasFile.value,
);

/**
 * 편집 중 브라우저 탭을 닫거나 다른 주소로 이동하면(뒤로 가기 포함) 입력한 내용이 그대로
 * 사라진다 — "취소" 버튼은 confirm()으로 막지만, 그 경로 밖의 이탈은 아무 안내도 없었다.
 * 저장에 성공해 스스로 이동할 때는 이 확인을 띄우지 않는다(unsavedGuard.allowNavigation()).
 */
const unsavedGuard = useUnsavedGuard(isDirty);

// 자동 임시 저장(이탈 경고를 대체하지 않고 나란히 쓴다 — useDraftAutosave.js 참고). 사진은
// 직렬화할 수 없어 제목·분류·내용만 담는다. 글마다 따로 기억하도록 키에 id를 넣는다.
//
// 회원 id도 함께 넣는다 — 예전 키(kraft:draft:post-edit:{id})는
// 사용자 구분이 없어, 공용 PC에서 다른 계정이 같은 글을 편집하다 만 초안을 그대로 보게 될 수
// 있었다. userId가 없으면(편집 폼 자체가 로그인·소유권을 요구하므로 실제로는 일어나지 않는다)
// storage를 null로 둬 초안 기능 자체를 건너뛴다.
const LEGACY_DRAFT_KEY = `kraft:draft:post-edit:${props.post.id}`;
const draftKey = props.userId != null
    ? `kraft:draft:${props.userId}:post-edit:${props.post.id}`
    : LEGACY_DRAFT_KEY;
const autosave = useDraftAutosave(draftKey, draft, {
    storage: props.userId != null ? undefined : null,
});
useSessionKeepAlive();

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
    form.value?.titleInput?.focus();
}

function discardDraft() {
    autosave.discard();
    form.value?.titleInput?.focus();
}

async function startEdit() {
    editMounted.value = true;
    mode.value = 'edit';
    // 서버 원본과 같은 초안은 되찾을 게 없으니 배너를 띄우지 않는다.
    autosave.checkAvailable((stored) =>
        stored.title === original.title && stored.content === original.content && stored.category === original.category,
    );
    // 옛(사용자 구분 없는) 키에 남아 있을 수 있는 초안은 지운다 — 다음 사용자에게 보이지
    // 않게 한다. 지금 쓸 키가 바로 그 옛 키이면(userId 없음) 건드리지 않는다.
    if (props.userId != null) {
        clearDraft(safeLocalStorage(), LEGACY_DRAFT_KEY);
    }
    await nextTick();
    form.value?.titleInput?.focus();
}

async function cancelEdit() {
    if (isDirty.value && !window.confirm('변경한 내용을 버리시겠습니까?')) {
        return;
    }
    draft.title = original.title;
    draft.content = original.content;
    draft.category = original.category;
    form.value?.clearPicture();
    picture.removedExisting.value = false;
    mode.value = 'view';
    autosave.discard();
    await nextTick();
    postView.value?.focusEditButton();
}

const { submit } = usePostSubmit({
    saving,
    progressText,
    picture,
    applyFieldErrors,
    fieldRefs: {
        title: computed(() => form.value?.titleInput ?? null),
        content: computed(() => form.value?.contentInput ?? null),
    },
    verb: '저장',
});

function onSubmit() {
    // 업로드를 기다리는 동안 입력을 잠그지만(:disabled="saving"), 등록 화면과 동일하게
    // 제출 시점 값을 한 번 더 스냅샷으로 고정해 둔다 — 최종 요청은 항상 이 스냅샷을 쓴다.
    const snapshot = { title: draft.title, content: draft.content, category: draft.category };
    // 기준 버전은 요청 본문이 아니라 If-Match 헤더로만 보낸다 — 이 값도 제출 시점에 고정해 둔다.
    const baseVersion = version.value;

    return submit({
        // 새 파일을 고르지 않았으면 기존 사진을 지웠는지, 그대로 두는지에 따라 정한다.
        fallbackPicture: () => picture.removedExisting.value
            ? { picture: null, pictureWidth: null, pictureHeight: null }
            : {
                picture: props.post.picture || null,
                pictureWidth: props.post.pictureWidth ?? null,
                pictureHeight: props.post.pictureHeight ?? null,
            },
        // 편집을 시작할 때 받아간 버전(baseVersion)을 If-Match로 보내 "이 버전을 기준으로 고친다"고
        // 밝힌다. 그 사이 다른 곳에서 저장됐으면 서버가 412로 거절한다(저장 시점에 겹치면 409).
        send: (pictureFields) => api.put(
            `${API.POSTS}/${props.post.id}`,
            { ...snapshot, ...pictureFields },
            { headers: ifMatchHeaders(baseVersion) },
        ),
        onSuccess: () => {
            picture.revokePreview();
            flash.set('POST_UPDATED');
            unsavedGuard.allowNavigation();
            autosave.discard();
            // 목록으로 튕기지 않고 같은 글(이 화면 자신의 URL)을 새로고침한다 — 서버가 다시 그린 화면이 방금 저장한 제목·본문·버전을
            // 그대로 보여준다.
            window.location.href = `/posts/update/${props.post.id}`;
        },
    });
}
</script>

<template>
  <PostView
    v-show="mode === 'view'"
    ref="postView"
    :post="post"
    :category-options="categoryOptions"
    :authenticated="authenticated"
    @edit="startEdit"
  />

  <form
    v-if="post.canManagePost && editMounted"
    v-show="mode === 'edit'"
    id="post-edit"
    class="card-kraft post-edit"
    @submit.prevent="onSubmit"
  >
    <!-- 자동 임시 저장된 초안이 있으면 물어보고 선택하게 한다(자동 복원 아님). -->
    <DraftRestoreBanner
      :available="autosave.available.value"
      @restore="restoreDraft"
      @discard="discardDraft"
    />

    <PostFormFields
      ref="form"
      v-model:title="draft.title"
      v-model:category="draft.category"
      v-model:content="draft.content"
      id-prefix="edit-"
      :category-options="categoryOptions"
      :field-errors="fieldErrors"
      :saving="saving"
      :picture="picture"
      :picture-help="`${UPLOAD_MESSAGES.HELP}. 새 파일을 선택하면 기존 이미지를 대체합니다.`"
    >
      <!-- 기존 이미지(교체할 파일을 아직 선택하지 않았을 때만 표시). -->
      <template #picture-current>
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
      </template>
    </PostFormFields>

    <!-- v-show(display:none) 대신 항상 렌더링한 채 텍스트만 바꾼다 — 라이브 리전이
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
        :disabled="saving || picture.processing.value"
      >
        {{ saving ? '저장 중…' : '저장' }}
      </button>
    </div>
  </form>
</template>
