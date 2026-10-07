<script setup>
import { nextTick, ref, watch } from 'vue';
import { useRecommendation } from './useRecommendation.js';
import RecommendResults from './RecommendResults.vue';
import RecommendSettings from './RecommendSettings.vue';

/**
 * 번호 추천 화면. 왼쪽 "설정" 카드와 오른쪽 "결과" 카드를 나란히(좁은 화면에서는 위아래로) 그린다.
 *
 * 진입 시 자동 생성은 하지 않는다 — 사용자가 "번호 추천받기" 버튼을 눌렀을 때만 서버에 요청한다. 이 컴포넌트는
 * 두 카드를 잇고 포커스 이동만 맡는다. 상태와 요청은 useRecommendation, 표시는 두 카드 컴포넌트가 맡는다.
 */
defineProps({
    // 서버가 렌더링 시점에 아는 검증된 이력의 마지막 회차(없으면 null).
    historyRound: { type: Number, default: null },
});

const {
    status,
    count,
    result,
    errorMessage,
    liveAnnouncement,
    generate,
} = useRecommendation();

const results = ref(/** @type {InstanceType<typeof RecommendResults> | null} */ (null));

// 성공 응답이 오면 결과 제목으로 포커스를 옮긴다 — 스크린리더 사용자가 생성 버튼 다음에
// 이어질 결과를 놓치지 않게 한다. 실패한 재시도는 포커스를 가져가지 않는다.
watch(status, async (next) => {
    if (next === 'ready') {
        await nextTick();
        results.value?.focusHeading();
    }
});
</script>

<template>
  <div class="recommend">
    <RecommendSettings
      v-model:count="count"
      :history-round="historyRound"
      :generating="status === 'generating'"
      @submit="generate"
    />

    <RecommendResults
      ref="results"
      :result="result"
      :status="status"
      :error-message="errorMessage"
    />

    <p
      class="visually-hidden"
      aria-live="polite"
    >
      {{ liveAnnouncement }}
    </p>
  </div>
</template>
