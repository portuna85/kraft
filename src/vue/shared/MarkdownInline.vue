<script setup>
/**
 * `markdown.js`의 인라인 노드(문자열 | br | code | strong | em | a)를 그린다.
 * strong·em·a는 자기 안에 또 인라인 노드를 담을 수 있어(예: `**굵고 *기울인* 글자**`)
 * 이 컴포넌트가 자기 자신을 재귀 호출한다 — `defineOptions({ name })`으로 이름을 준
 * 이유가 템플릿의 재귀 참조 때문이다.
 *
 * 텍스트는 전부 `{{ }}` 보간이라 Vue가 자동으로 이스케이프한다 — `v-html`을 쓰지 않는다.
 */
defineOptions({ name: 'MarkdownInline' });

defineProps({
    nodes: {
        type: /** @type {import('vue').PropType<import('./markdown.js').InlineNode[]>} */ (Array),
        required: true,
    },
});
</script>

<template>
  <template
    v-for="(node, index) in nodes"
    :key="index"
  >
    <template v-if="typeof node === 'string'">
      {{ node }}
    </template>
    <br v-else-if="node.type === 'br'">
    <code v-else-if="node.type === 'code'">{{ node.text }}</code>
    <strong v-else-if="node.type === 'strong'">
      <MarkdownInline :nodes="node.children" />
    </strong>
    <em v-else-if="node.type === 'em'">
      <MarkdownInline :nodes="node.children" />
    </em>
    <!-- 사용자 글의 외부 링크다 — 검색엔진에 이 글의 평판을 넘기지 않고(nofollow), 사용자가
         만든 콘텐츠임을 표시하며(ugc), 새 탭에서 열어도 opener를 통한 리버스 탭내빙을
         막는다(noopener noreferrer). -->
    <a
      v-else-if="node.type === 'a'"
      :href="node.href"
      target="_blank"
      rel="nofollow ugc noopener noreferrer"
    >
      <MarkdownInline :nodes="node.children" />
    </a>
  </template>
</template>
