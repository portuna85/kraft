<script setup>
import { computed, nextTick, reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import * as flash from '@ui/flash.js';
import { showToast } from '@ui/toast.js';
import { useImageUpload } from '../shared/useImageUpload.js';

/**
 * 게시글 읽기·편집·추천 상태를 관리한다. 추천은 서버가 반환한 상태만 반영한다.
 */
const props = defineProps({
    post: { type: Object, required: true },
    categoryOptions: { type: Array, required: true },
    authenticated: { type: Boolean, required: true },
});

const mode = ref('view'); // 'view' | 'edit'
const original = reactive({
    title: props.post.title,
    content: props.post.content,
    category: props.post.category,
});
const draft = reactive({ ...original });
const version = ref(props.post.version);
const progressText = ref(null);
const saving = ref(false);
const liked = ref(props.post.likedByMe);
const likeCount = ref(props.post.likeCount);
const liking = ref(false);

const picture = useImageUpload({ initialUrl: props.post.picture });

const titleInput = ref(null);
const editButton = ref(null);
const fileInput = ref(null);

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

async function startEdit() {
    mode.value = 'edit';
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

async function onSubmit() {
    saving.value = true;

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
            title: draft.title,
            content: draft.content,
            picture: pictureUrl,
            category: draft.category,
            // 편집을 시작할 때 받아간 버전. 그 사이 다른 곳에서 저장됐으면 서버가 409로 거절한다.
            version: version.value,
        });
        picture.revokePreview();
        flash.set('POST_UPDATED');
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
      id="post-content-text"
      class="post-body"
    >
      {{ post.content }}
    </div>

    <div
      v-if="post.picture"
      class="post-image"
    >
      <img
        :src="post.picture"
        alt="게시글 첨부 이미지"
      >
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
      <a href="/login">로그인 후 추천할 수 있습니다.</a>
    </p>

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
      >
    </div>
    <div class="mb-3">
      <label for="edit-category">분류</label>
      <select
        id="edit-category"
        v-model="draft.category"
        class="form-select"
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
      <textarea
        id="content"
        v-model="draft.content"
        class="form-control post-edit__textarea"
        maxlength="10000"
        required
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
        @change="onFileChange"
      >
      <small class="form-text text-muted">JPG, JPEG, PNG, GIF, WEBP · 최대 5MB · 1개. 새 파일을 선택하면 기존 이미지를 대체합니다.</small>
    </div>

    <!-- 기존 이미지(교체할 파일을 아직 선택하지 않았을 때만 표시). -->
    <div
      v-if="picture.showExistingPreview.value"
      id="edit-picture-current"
      class="picture-preview"
    >
      <img
        class="picture-preview__image"
        :src="post.picture"
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
        :src="picture.previewUrl.value"
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
          @click="clearPicture"
        >
          선택 해제
        </button>
      </div>
    </div>

    <p
      v-show="progressText"
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
        저장
      </button>
    </div>
  </form>
</template>
