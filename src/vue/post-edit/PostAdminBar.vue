<script setup>
import { ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { formatDateTime } from '@core/datetime.js';
import { showToast } from '@ui/toast.js';

/**
 * 관리자에게만 보이는 게시글 상태 줄 — 소프트 삭제된 글의 "삭제됨" 표시와 복구 버튼, 숨겨진 글의 "숨김"
 * 표시와 숨김 해제 버튼, 그리고 보통 글의 기한 고정과 숨기기를 맡는다. 노출 여부
 * (post.canModerate)는 서버가 판정한 값이고, 모든 API도 서버가 관리자인지 다시 확인한다. 삭제된 글은
 * 복구가 먼저다(삭제된 글의 숨김은 풀 수 없고 고정할 수도 없다).
 */
const props = defineProps({
    post: { type: /** @type {import('vue').PropType<import('../shared/types.js').PostViewDto>} */ (Object), required: true },
});

const busy = ref(false);

/**
 * 고정 기한 입력의 기본값 — 지금부터 7일 뒤를 한국 시간(서버 기준) 벽시계로 쓴다. `datetime-local`은 시간대
 * 없는 값을 만들고 서버도 KST로 읽으므로, 브라우저 시간대가 아니라 KST로 맞춰 둬야 해외에서 열어도 어긋나지
 * 않는다.
 */
function defaultPinUntil() {
    return new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
        .toLocaleString('sv-SE', { timeZone: 'Asia/Seoul', hour12: false })
        .slice(0, 16)
        .replace(' ', 'T');
}

const pinUntil = ref(defaultPinUntil());

/** 서버 API를 부르고, 성공하면 서버가 새로 렌더링한 화면으로 다시 불러온다. */
async function run(request) {
    if (busy.value) {
        return;
    }
    busy.value = true;
    try {
        await request();
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        busy.value = false;
    }
}

const change = (action) => run(() => api.post(`${API.ADMIN_POSTS}/${props.post.id}/${action}`));
const pin = () => run(() => api.put(`${API.ADMIN_POSTS}/${props.post.id}/pin`, { pinnedUntil: pinUntil.value }));
const unpin = () => run(() => api.del(`${API.ADMIN_POSTS}/${props.post.id}/pin`));
</script>

<template>
  <div
    v-if="post.canModerate && (post.deleted || post.blinded)"
    id="post-admin-bar"
    class="alert alert-warning d-flex flex-wrap align-items-center justify-content-between gap-2"
    role="status"
  >
    <span v-if="post.deleted">삭제된 글입니다. 관리자에게만 보이며, 보관 기간이 지나면 영구 삭제됩니다.</span>
    <span v-else>관리자가 숨긴 글입니다. 관리자에게만 보입니다.</span>
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

  <!-- 보통 글에서만 고정할 수 있다. 삭제·숨김 글은 위 줄이 대신 그려진다. -->
  <div
    v-else-if="post.canModerate"
    id="post-pin-bar"
    class="d-flex flex-wrap align-items-center gap-2 mb-3"
  >
    <span
      v-if="post.pinnedUntil"
      id="post-pin-status"
      class="text-muted"
    >목록 상단에 고정 중 · {{ formatDateTime(post.pinnedUntil) }}까지</span>
    <label
      class="visually-hidden"
      for="pin-until"
    >고정 기한(한국 시간)</label>
    <input
      id="pin-until"
      v-model="pinUntil"
      type="datetime-local"
      class="form-control form-control-sm w-auto"
      :disabled="busy"
    >
    <button
      id="btn-pin-post"
      type="button"
      class="btn btn-sm btn-outline-secondary"
      :disabled="busy || !pinUntil"
      @click="pin"
    >
      {{ post.pinnedUntil ? '기한 변경' : '목록 상단에 고정' }}
    </button>
    <button
      v-if="post.pinnedUntil"
      id="btn-unpin-post"
      type="button"
      class="btn btn-sm btn-outline-danger"
      :disabled="busy"
      @click="unpin"
    >
      고정 해제
    </button>
    <button
      id="btn-blind-post"
      type="button"
      class="btn btn-sm btn-outline-secondary"
      :disabled="busy"
      @click="change('blind')"
    >
      숨기기
    </button>
  </div>
</template>
