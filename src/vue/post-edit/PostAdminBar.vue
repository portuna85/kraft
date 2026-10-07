<script setup>
import { ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { showToast } from '@ui/toast.js';

/**
 * 관리자에게만 보이는 게시글 상태 줄 — 소프트 삭제된 글의 "삭제됨" 표시와 복구 버튼, 신고 처리로
 * 숨겨진 글의 "숨김" 표시와 숨김 해제 버튼을 맡는다. 노출 여부(post.canModerate)는 서버가 판정한 값이고,
 * 복구·해제 API도 서버가 관리자인지 다시 확인한다. 삭제된 글은 복구가 먼저다(삭제된 글의 숨김은 풀 수 없다).
 */
const props = defineProps({
    post: { type: /** @type {import('vue').PropType<import('../shared/types.js').PostViewDto>} */ (Object), required: true },
});

const busy = ref(false);

/** 상태를 바꾸는 관리자 API를 부르고, 성공하면 서버가 새로 렌더링한 화면으로 다시 불러온다. */
async function change(action) {
    if (busy.value) {
        return;
    }
    busy.value = true;
    try {
        await api.post(`${API.ADMIN_POSTS}/${props.post.id}/${action}`);
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        busy.value = false;
    }
}
</script>

<template>
  <div
    v-if="post.canModerate && (post.deleted || post.blinded)"
    id="post-admin-bar"
    class="alert alert-warning d-flex flex-wrap align-items-center justify-content-between gap-2"
    role="status"
  >
    <span v-if="post.deleted">삭제된 글입니다. 관리자에게만 보이며, 보관 기간이 지나면 영구 삭제됩니다.</span>
    <span v-else>신고 처리로 숨겨진 글입니다. 관리자에게만 보입니다.</span>
    <button
      v-if="post.deleted"
      id="btn-restore-post"
      type="button"
      class="btn btn-sm btn-outline-dark"
      :disabled="busy"
      @click="change('restore')"
    >
      복구
    </button>
    <button
      v-else
      id="btn-unblind-post"
      type="button"
      class="btn btn-sm btn-outline-dark"
      :disabled="busy"
      @click="change('unblind')"
    >
      숨김 해제
    </button>
  </div>
</template>
