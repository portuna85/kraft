<script setup>
/**
 * 번호 추천 화면의 아이콘(24px 격자, 선 2px). 장식이라 항상 aria-hidden이고, 의미는 옆 글자나 버튼의
 * aria-label이 맡는다. 외부 아이콘 라이브러리 없이 필요한 몇 개만 둔다.
 */
defineProps({
    name: { type: String, required: true },
});

const PATHS = {
    shuffle: ['M16 3h5v5', 'M4 20 21 3', 'M21 16v5h-5', 'M15 15l6 6', 'M4 4l5 5'],
    check: ['M20 6 9 17l-5-5'],
    shield: [
        'M20 13c0 5-3.5 7.5-7.7 8.97a1 1 0 0 1-.6 0C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
        'm9 12 2 2 4-4',
    ],
    copy: [
        'M8 8h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z',
        'M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2',
    ],
};
</script>

<template>
  <svg
    class="recommend__icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path
      v-for="d in PATHS[name]"
      :key="d"
      :d="d"
    />
  </svg>
</template>
