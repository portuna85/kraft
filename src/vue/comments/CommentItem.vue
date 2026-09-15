<script setup>
import { ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { showToast } from '@ui/toast.js';

/**
 * 댓글 한 건의 읽기뷰/인라인 수정폼. 삭제 버튼은 여기서 처리하지 않는다 — 게시글과 댓글이
 * 함께 쓰는 공용 삭제 모달(delete-confirm.js)이 document 위임으로 이 버튼을 그대로 집어간다.
 */
const props = defineProps({
    comment: { type: Object, required: true },
    // 신고 버튼은 로그인한 사람에게만 보인다. 목록이 이 값을 그대로 내려준다.
    authenticated: { type: Boolean, required: true },
});

const emit = defineEmits(['updated']);

const editing = ref(false);
const draftContent = ref(props.comment.content);
const saving = ref(false);

function startEdit() {
    draftContent.value = props.comment.content;
    editing.value = true;
}

function cancelEdit() {
    editing.value = false;
}

async function save() {
    saving.value = true;
    try {
        await api.put(`${API.COMMENTS}/${props.comment.id}`, { content: draftContent.value });
        emit('updated', { id: props.comment.id, content: draftContent.value });
        editing.value = false;
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        saving.value = false;
    }
}

function formatDate(iso) {
    if (!iso) {
        return '';
    }
    const date = new Date(iso);
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
</script>

<template>
  <li
    class="comment-list__item"
    :data-comment-id="comment.id"
  >
    <div
      v-show="!editing"
      class="comment-view"
    >
      <div class="comment-list__head">
        <strong>{{ comment.author }}</strong>
        <small class="text-muted">{{ formatDate(comment.createdAt) }}</small>
      </div>
      <p class="comment-list__content">
        {{ comment.content }}
      </p>
      <div
        v-if="comment.canManage"
        class="btn-group-gap comment-actions"
      >
        <button
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-edit"
          @click="startEdit"
        >
          수정
        </button>
        <button
          type="button"
          class="btn btn-sm btn-outline-danger btn-comment-delete"
          data-target-kind="comment"
        >
          삭제
        </button>
      </div>

      <!-- 신고는 남의 댓글에만 보인다. 자기 댓글은 서버도 거절한다(직접 지우면 된다). -->
      <div
        v-else-if="authenticated"
        class="btn-group-gap comment-actions"
      >
        <button
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-report"
          data-report-kind="comment"
        >
          신고
        </button>
      </div>
    </div>
    <form
      v-show="editing"
      class="comment-edit-form"
      @submit.prevent="save"
    >
      <div class="mb-3">
        <label class="visually-hidden">댓글 내용</label>
        <textarea
          v-model="draftContent"
          class="form-control comment-edit__textarea"
          maxlength="1000"
        />
      </div>
      <div class="btn-group-gap">
        <button
          type="button"
          class="btn btn-sm btn-secondary btn-comment-cancel"
          @click="cancelEdit"
        >
          취소
        </button>
        <button
          type="submit"
          class="btn btn-sm btn-primary btn-comment-save"
          :disabled="saving"
        >
          저장
        </button>
      </div>
    </form>
  </li>
</template>
