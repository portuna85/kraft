<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { api } from '@core/http.js';
import { API, UPLOAD_MESSAGES } from '@core/constants.js';
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

/**
 * 게시글 등록 화면.
 *
 * 제목·내용의 필수 검사는 HTML `required`에 맡긴다 — 브라우저가 submit 이벤트 자체를 막으므로
 * JS로 한 번 더 검사하면 도달하지 않는 코드가 된다.
 *
 * 분류 선택지는 서버가 CategoryPolicy로 계산해 내려준다(관리자가 아니면 공지 없음). 화면에서
 * 거르지 않는 이유는 편집 화면과 같다 — 실제 경계는 저장 요청에서 서버가 잡는다.
 *
 * 입력 칸은 PostFormFields, 사진 업로드부터 오류 안내까지의 제출 흐름은 usePostSubmit이
 * 편집 화면과 함께 쓴다(FE-12). 여기에 남은 것은 등록 요청 자체와 초안·이탈 방지다.
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
const form = ref(/** @type {InstanceType<typeof PostFormFields> | null} */ (null));

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
const { fieldErrors, apply: applyFieldErrors, clearOnEdit } = useFieldErrors();
clearOnEdit(draft);
const LEGACY_DRAFT_KEY = 'kraft:draft:post-save';
const draftKey = props.userId != null ? `kraft:draft:${props.userId}:post-save` : LEGACY_DRAFT_KEY;
const autosave = useDraftAutosave(draftKey, draft, {
    storage: props.userId != null ? undefined : null,
});
useSessionKeepAlive();

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
    form.value?.titleInput?.focus();
}

function discardDraft() {
    autosave.discard();
    form.value?.titleInput?.focus();
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
    verb: '등록',
});

function onSubmit() {
    // 업로드를 기다리는 동안 입력을 잠그지만(:disabled="saving"), 편집 화면과 동일하게 제출
    // 시점 값을 한 번 더 스냅샷으로 고정해 둔다 — 최종 요청은 항상 이 스냅샷을 쓴다(개선
    // 보고서 F03).
    const snapshot = { title: draft.title, content: draft.content, category: draft.category };

    return submit({
        // 새 글은 사진을 고르지 않았으면 사진 없이 등록한다.
        fallbackPicture: () => ({ picture: null, pictureWidth: null, pictureHeight: null }),
        send: (pictureFields) => api.post(API.POSTS, { ...snapshot, ...pictureFields }),
        onSuccess: (newPostId) => {
            picture.revokePreview();
            flash.set('POST_SAVED');
            unsavedGuard.allowNavigation();
            autosave.discard();
            // 목록 첫 페이지가 아니라 방금 쓴 글로 이동한다(전체 리뷰 2026-09-26 A-FE-02) —
            // 등록 API가 새 글 id를 그대로 돌려준다.
            window.location.href = `/posts/update/${newPostId}`;
        },
    });
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

    <PostFormFields
      ref="form"
      v-model:title="draft.title"
      v-model:category="draft.category"
      v-model:content="draft.content"
      :category-options="categoryOptions"
      :field-errors="fieldErrors"
      :saving="saving"
      :picture="picture"
      title-placeholder="제목을 입력하세요"
      content-placeholder="내용을 입력하세요"
      :picture-help="UPLOAD_MESSAGES.HELP"
    >
      <!-- 닉네임을 보여준다. 값은 서버가 principal에서 꺼내 내려준 것이고, 저장 요청에는
           담지 않는다 — 작성자는 서버가 로그인 계정으로 정한다. 입력할 것이 없는 값이라
           채울 단계(mb-3 입력 필드)가 아니라 제목 아래 안내문구로만 보여준다. -->
      <template #after-title>
        <p class="post-form__byline text-muted">
          작성자: {{ author }} · 로그인 계정으로 자동 지정됩니다.
        </p>
      </template>
    </PostFormFields>

    <!-- 업로드→저장 진행 상태. aria-live 영역이 나타나는 순간 내용이 채워지면 스크린 리더가 놓칠 수 있어
         v-show 없이 항상 렌더한다(FE-08, 수정 화면과 같다). 비어 있을 때는 화면에 거의 티가 나지 않는다. -->
    <p
      id="post-save-progress"
      class="form-progress"
      aria-live="polite"
    >
      {{ progressText }}
    </p>

    <div class="btn-group-gap">
      <a
        href="/community"
        class="btn btn-secondary"
      >취소</a>
      <button
        id="btn-save"
        type="submit"
        class="btn btn-primary"
        :disabled="saving || picture.processing.value"
      >
        {{ saving ? '등록 중…' : '등록' }}
      </button>
    </div>
  </form>
</template>
