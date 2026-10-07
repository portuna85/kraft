<script setup>
import { ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { showToast } from '@ui/toast.js';

/**
 * 관리자에게만 보이는 게시글 상태 줄. 지금은 소프트 삭제된 글의 "삭제됨" 표시와 복구 버튼만 맡는다.
 * 노출 여부(post.canModerate)는 서버가 판정한 값이고, 복구 API도 서버가 관리자인지 다시 확인한다.
 */
const props = defineProps({
    post: { type: /** @type {import('vue').PropType<import('../shared/types.js').PostViewDto>} */ (Object), required: true },
});

const restoring = ref(false);

async function restore() {
    if (restoring.value) {
        return;
    }
    restoring.value = true;
    try {
        await api.post(`${API.ADMIN_POSTS}/${props.post.id}/restore`);
        // 복구한 글은 일반 화면(수정·삭제 버튼, 추천)으로 다시 그려야 하므로 서버가 새로 렌더링하게 한다.
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        restoring.value = false;
    }
}
</script>

<template>
  <div
    v-if="post.canModerate && post.deleted"
    id="post-admin-bar"
    class="alert alert-warning d-flex flex-wrap align-items-center justify-content-between gap-2"
    role="status"
  >
    <span>삭제된 글입니다. 관리자에게만 보이며, 보관 기간이 지나면 영구 삭제됩니다.</span>
    <button
      id="btn-restore-post"
      type="button"
      class="btn btn-sm btn-outline-dark"
      :disabled="restoring"
      @click="restore"
    >
      복구
    </button>
  </div>
</template>
