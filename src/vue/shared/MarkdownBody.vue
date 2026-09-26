<script setup>
import { computed } from 'vue';
import { parseMarkdown } from './markdown.js';
import MarkdownInline from './MarkdownInline.vue';

/**
 * 가벼운 마크다운을 그린다(12단계). 저장 형식은 지금과 똑같은 평문이고, 여기서 하는 일은
 * "화면에 어떻게 보여줄까"뿐이다 — `markdown.js`가 만든 블록 트리를 템플릿으로 그대로
 * 옮기므로 텍스트는 전부 `{{ }}`/`MarkdownInline`을 거쳐 Vue가 자동으로 이스케이프한다.
 * `v-html`은 쓰지 않는다. `PostEditApp.vue`의 조회 모드와 `MarkdownToolbar.vue`의
 * 미리보기가 함께 쓴다.
 */
const props = defineProps({
    source: { type: String, required: true },
});

const blocks = computed(() => parseMarkdown(props.source));
</script>

<template>
  <template
    v-for="(block, index) in blocks"
    :key="index"
  >
    <p v-if="block.type === 'p'">
      <MarkdownInline :nodes="block.children" />
    </p>
    <ul v-else-if="block.type === 'ul'">
      <li
        v-for="(item, itemIndex) in block.items"
        :key="itemIndex"
      >
        <MarkdownInline :nodes="item" />
      </li>
    </ul>
    <ol v-else-if="block.type === 'ol'">
      <li
        v-for="(item, itemIndex) in block.items"
        :key="itemIndex"
      >
        <MarkdownInline :nodes="item" />
      </li>
    </ol>
  </template>
</template>
