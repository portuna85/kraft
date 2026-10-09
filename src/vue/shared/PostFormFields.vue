<script setup>
import { ref } from 'vue';
import { POST } from '@core/constants.js';
import MarkdownToolbar from './MarkdownToolbar.vue';

/**
 * 글쓰기·수정 폼이 함께 쓰는 입력 칸(제목·분류·내용·사진)과 선택한 사진 미리보기.
 * 예전에는 PostSaveApp.vue와 PostEditApp.vue가 같은 마크업을 각자 갖고 있었다.
 *
 * 두 화면의 id는 서로 달라야 한다(수정 화면은 글 보기 영역과 한 문서에 함께 있고, E2E도 id로 고른다) —
 * 그래서 idPrefix('' 또는 'edit-')를 받아 분류·사진·오류 요소의 id에 붙인다. 제목과 내용 입력의
 * id는 두 화면 모두 `title`·`content`로 같다.
 *
 * 값은 `v-model:title`·`v-model:category`·`v-model:content`로 주고받는다. 제출·초안·이탈 방지는 이 컴포넌트가
 * 모른다 — 부모(App)가 맡는다.
 */
const props = defineProps({
    idPrefix: { type: String, default: '' },
    categoryOptions: { type: /** @type {import('vue').PropType<import('./types.js').CategoryOption[]>} */ (Array), required: true },
    /** 서버 검증 오류(useFieldErrors의 fieldErrors). */
    fieldErrors: { type: /** @type {import('vue').PropType<Record<string, string>>} */ (Object), required: true },
    saving: { type: Boolean, default: false },
    /** useImageUpload가 돌려준 객체. */
    picture: { type: Object, required: true },
    titlePlaceholder: { type: String, default: undefined },
    contentPlaceholder: { type: String, default: '' },
    pictureHelp: { type: String, required: true },
});

const title = defineModel('title', { type: String, required: true });
const category = defineModel('category', { type: String, required: true });
const content = defineModel('content', { type: String, required: true });

const titleInput = ref(/** @type {HTMLInputElement | null} */ (null));
/** @type {import('vue').Ref<InstanceType<typeof MarkdownToolbar> | null>} */
const contentInput = ref(null);
const fileInput = ref(/** @type {HTMLInputElement | null} */ (null));

const id = (name) => `${props.idPrefix}${name}`;

function onFileChange(event) {
    props.picture.onFileSelected(event.target.files?.[0] ?? null);
}

/** 선택한 사진을 비운다. 파일 입력의 값도 비워야 같은 파일을 다시 골라도 change 이벤트가 또 일어난다. */
function clearPicture() {
    props.picture.clear();
    if (fileInput.value) {
        fileInput.value.value = '';
    }
}

// titleInput·contentInput은 부모가 오류 칸으로 포커스를 옮기는 데 쓴다(useFieldErrors.apply).
defineExpose({ titleInput, contentInput, clearPicture });
</script>

<template>
  <div class="mb-3">
    <label for="title">제목</label>
    <input
      id="title"
      ref="titleInput"
      v-model="title"
      type="text"
      class="form-control"
      :class="{ 'is-invalid': fieldErrors.title }"
      :aria-invalid="fieldErrors.title ? 'true' : undefined"
      :placeholder="titlePlaceholder"
      maxlength="255"
      :aria-describedby="id('title-error')"
      required
      :disabled="saving"
    >
    <div
      :id="id('title-error')"
      class="invalid-feedback"
    >
      {{ fieldErrors.title }}
    </div>
  </div>

  <slot name="after-title" />

  <div class="mb-3">
    <label :for="id('category')">분류</label>
    <select
      :id="id('category')"
      v-model="category"
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
      v-model="content"
      :placeholder="contentPlaceholder"
      :maxlength="POST.CONTENT_MAX_LENGTH"
      :disabled="saving"
      :invalid="!!fieldErrors.content"
      :describedby="fieldErrors.content ? id('content-error') : undefined"
    />
    <div
      v-if="fieldErrors.content"
      :id="id('content-error')"
      class="invalid-feedback d-block"
    >
      {{ fieldErrors.content }}
    </div>
  </div>
  <div class="mb-3">
    <label :for="id('picture')">사진</label>
    <input
      :id="id('picture')"
      ref="fileInput"
      type="file"
      class="form-control"
      accept="image/jpeg,image/png,image/gif,image/webp"
      :aria-describedby="id('picture-help')"
      :disabled="saving || picture.uploading.value"
      @change="onFileChange"
    >
    <small
      :id="id('picture-help')"
      class="form-text text-muted"
    >{{ pictureHelp }}</small>
  </div>

  <!-- 수정 화면의 기존 이미지 미리보기 같은, 화면마다 다른 사진 영역. -->
  <slot name="picture-current" />

  <!-- 새로 선택한 파일의 이름·크기·로컬 미리보기. -->
  <div
    v-show="picture.hasFile.value"
    :id="id('picture-preview')"
    class="picture-preview"
  >
    <img
      :id="id('picture-preview-image')"
      class="picture-preview__image"
      :src="picture.previewUrl.value ?? undefined"
      alt="선택한 이미지 미리보기"
    >
    <div class="picture-preview__meta">
      <p
        :id="id('picture-preview-name')"
        class="picture-preview__name"
      >
        {{ picture.fileLabel.value }}
      </p>
      <button
        :id="`btn-${id('picture-clear')}`"
        type="button"
        class="btn btn-sm btn-outline-secondary"
        :disabled="saving || picture.uploading.value"
        @click="clearPicture"
      >
        선택 해제
      </button>
    </div>
  </div>
</template>
