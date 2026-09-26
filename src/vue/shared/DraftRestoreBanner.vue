<script setup>
import { ref, watch } from 'vue';

/**
 * 자동 임시 저장(8단계) 복원 배너. PostSaveApp·PostEditApp이 똑같은 마크업을 각자 갖고
 * 있었던 것을 13단계에서 공용 컴포넌트로 뺐다. 포커스 이동(복원·새로 시작 모두 제목
 * 입력으로)은 호출부가 titleInput ref를 쥐고 있으므로 여기서 하지 않고 이벤트로 위임한다.
 *
 * 배너 자체는 `v-if`라 나타나는 순간을 스크린 리더가 놓칠 수 있다 — 그래서 항상 DOM에
 * 남아 있는 별도의 aria-live 문구(`status`)로 "나타났다"/"복원했다"를 알린다. 기존 ID
 * (#draft-restore-banner, #btn-draft-restore, #btn-draft-discard)는 e2e
 * (post-draft-autosave.spec.js)가 그대로 참조하므로 유지한다.
 */
const props = defineProps({
    available: { type: Boolean, required: true },
});
const emit = defineEmits(['restore', 'discard']);

const status = ref('');

watch(() => props.available, (value) => {
    if (value) {
        status.value = '임시 저장된 내용이 있습니다.';
    }
});

function onRestore() {
    status.value = '임시 저장된 내용을 복원했습니다.';
    emit('restore');
}

function onDiscard() {
    status.value = '';
    emit('discard');
}
</script>

<template>
  <p
    aria-live="polite"
    class="visually-hidden"
  >
    {{ status }}
  </p>
  <div
    v-if="available"
    id="draft-restore-banner"
    class="draft-banner"
    role="region"
    aria-label="임시 저장 복원"
  >
    <p class="draft-banner__text">
      임시 저장된 내용이 있습니다. 사진 첨부는 복원되지 않아 다시 선택해야 합니다.
    </p>
    <div class="draft-banner__actions">
      <button
        id="btn-draft-restore"
        type="button"
        class="btn btn-sm btn-outline-primary"
        @click="onRestore"
      >
        복원
      </button>
      <button
        id="btn-draft-discard"
        type="button"
        class="btn btn-sm btn-outline-secondary"
        @click="onDiscard"
      >
        새로 시작
      </button>
    </div>
  </div>
</template>
