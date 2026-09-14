<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { showToast } from '@ui/toast.js';
import * as flash from '@ui/flash.js';
import CommentItem from './CommentItem.vue';

/**
 * 댓글 목록 전체. 서버가 최초 렌더링 시 canManage까지 계산해 내려준 목록(initialComments)을
 * 그대로 초기 상태로 쓰고, 이후 생성·수정·삭제는 서버를 다시 조회하지 않고 로컬 상태만
 * 갱신한다(낙관적 갱신) — 새로고침하면 서버가 다시 정확한 canManage를 계산해 주므로 정합성
 * 문제는 없다.
 */
const props = defineProps({
    postId: { type: String, required: true },
    authenticated: { type: Boolean, required: true },
    initialComments: { type: Array, required: true },
});

const comments = reactive([...props.initialComments]);
const newContent = ref('');
const saving = ref(false);

async function save() {
    saving.value = true;
    try {
        const id = await api.post(`${API.POSTS}/${props.postId}/comments`, {
            content: newContent.value,
        });
        // 응답은 id뿐이다. 방금 내가 쓴 댓글이므로 관리 가능하고, 작성자 표시는 화면에 이미
        // 렌더링된 로그인 사용자 닉네임(navbar의 #user)을 그대로 쓴다.
        comments.push({
            id,
            content: newContent.value,
            author: document.getElementById('user')?.textContent ?? '',
            createdAt: new Date().toISOString(),
            canManage: true,
        });
        newContent.value = '';
        flash.showNow('COMMENT_SAVED');
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        saving.value = false;
    }
}

function onUpdated({ id, content }) {
    const target = comments.find((c) => String(c.id) === String(id));
    if (target) {
        target.content = content;
    }
    flash.showNow('COMMENT_UPDATED');
}

// 삭제는 게시글과 공유하는 모달(delete-confirm.js)이 처리하고, 끝나면 이 이벤트로 알려온다.
function onExternalDelete(event) {
    const index = comments.findIndex((c) => String(c.id) === String(event.detail.id));
    if (index !== -1) {
        comments.splice(index, 1);
    }
}

onMounted(() => window.addEventListener('kraft:comment-deleted', onExternalDelete));
onUnmounted(() => window.removeEventListener('kraft:comment-deleted', onExternalDelete));
</script>

<template>
  <h2
    id="comments-heading"
    class="comments__heading"
  >
    댓글 {{ comments.length }}개
  </h2>

  <p
    v-if="comments.length === 0"
    class="comments__empty"
  >
    첫 댓글을 남겨 보세요.
  </p>

  <ul
    v-else
    id="comment-list"
    class="comment-list"
  >
    <CommentItem
      v-for="comment in comments"
      :key="comment.id"
      :comment="comment"
      @updated="onUpdated"
    />
  </ul>

  <div
    v-if="authenticated"
    class="mb-3 comment-form"
  >
    <label for="comment-content">댓글 작성</label>
    <textarea
      id="comment-content"
      v-model="newContent"
      class="form-control"
      placeholder="댓글을 입력하세요"
      maxlength="1000"
    />
    <button
      id="btn-comment-save"
      type="button"
      class="btn btn-primary mt-2"
      :disabled="saving"
      @click="save"
    >
      댓글 등록
    </button>
  </div>
  <p
    v-else
    class="comments__login-hint"
  >
    <a href="/login">로그인 후 댓글을 작성할 수 있습니다.</a>
  </p>
</template>
