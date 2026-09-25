<script setup>
import { computed, reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import * as flash from '@ui/flash.js';
import { useImageUpload } from '../shared/useImageUpload.js';
import { useUnsavedGuard } from '../shared/useUnsavedGuard.js';

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
        await api.post(API.POSTS, {
            title: snapshot.title,
            content: snapshot.content,
            picture: pictureUrl,
            category: snapshot.category,
        });
        picture.revokePreview();
        flash.set('POST_SAVED');
        unsavedGuard.allowNavigation();
        window.location.href = '/';
    } catch (error) {
        progressText.value = null;
        saving.value = false;
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
    <div class="mb-3">
      <label for="title">제목</label>
      <input
        id="title"
        v-model="draft.title"
        type="text"
        class="form-control"
        placeholder="제목을 입력하세요"
        maxlength="255"
        required
        :disabled="saving"
      >
    </div>
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
      <label for="author">작성자</label>
      <!-- 닉네임을 보여준다. 값은 서버가 principal에서 꺼내 내려준 것이고, 저장 요청에는
           담지 않는다 — 작성자는 서버가 로그인 계정으로 정한다. -->
      <input
        id="author"
        class="form-control"
        type="text"
        readonly
        :value="author"
      >
      <small class="form-text text-muted">작성자는 로그인 계정으로 자동 지정됩니다.</small>
    </div>
    <div class="mb-3">
      <label for="content">내용</label>
      <textarea
        id="content"
        v-model="draft.content"
        class="form-control post-edit__textarea"
        placeholder="내용을 입력하세요"
        maxlength="10000"
        required
        :disabled="saving"
      />
    </div>
    <div class="mb-3">
      <label for="picture">사진</label>
      <input
        id="picture"
        ref="fileInput"
        type="file"
        class="form-control"
        accept="image/jpeg,image/png,image/gif,image/webp"
        :disabled="saving || picture.uploading.value"
        @change="onFileChange"
      >
      <small class="form-text text-muted">JPG, JPEG, PNG, GIF, WEBP · 최대 5MB · 1개</small>
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
        등록
      </button>
    </div>
  </form>
</template>
