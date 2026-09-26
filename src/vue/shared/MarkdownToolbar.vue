<script setup>
import { nextTick, ref } from 'vue';
import { applyMarkup } from './markdownToolbar.js';
import MarkdownBody from './MarkdownBody.vue';

/**
 * 글쓰기·수정 폼의 본문 입력 위에 얹는 마크다운 툴바(12단계). `PostSaveApp.vue`·
 * `PostEditApp.vue`가 같은 컴포넌트를 쓴다 — `v-model`로 `draft.content`를 그대로
 * 바꾸므로 자동 임시 저장(8단계)과 그대로 맞물린다.
 *
 * textarea는 이 컴포넌트가 직접 그린다(슬롯이 아니다) — 선택 영역(selectionStart/End)을
 * 읽고 커서를 되돌리려면 같은 컴포넌트 안에서 ref로 붙잡고 있어야 한다.
 */
const props = defineProps({
    modelValue: { type: String, required: true },
    id: { type: String, required: true },
    disabled: { type: Boolean, default: false },
    maxlength: { type: Number, default: 10000 },
    placeholder: { type: String, default: '' },
});
const emit = defineEmits(['update:modelValue']);

const textarea = ref(/** @type {HTMLTextAreaElement | null} */ (null));
const mode = ref('write'); // 'write' | 'preview'

const TOOLS = [
    { kind: 'bold', label: '굵게', aria: '굵게' },
    { kind: 'italic', label: '기울임', aria: '기울임' },
    { kind: 'code', label: '코드', aria: '코드' },
    { kind: 'ul', label: '목록', aria: '글머리 목록' },
    { kind: 'ol', label: '번호', aria: '번호 목록' },
    { kind: 'link', label: '링크', aria: '링크' },
];

async function applyTool(kind) {
    const el = textarea.value;
    if (!el) {
        return;
    }
    const result = applyMarkup(props.modelValue, el.selectionStart, el.selectionEnd, kind);
    emit('update:modelValue', result.value);

    // v-model 갱신이 DOM에 반영된 뒤에 선택 영역을 되돌려야 위치가 어긋나지 않는다.
    await nextTick();
    el.focus();
    el.setSelectionRange(result.selStart, result.selEnd);
}

/** @param {Event} event */
function onInput(event) {
    emit('update:modelValue', /** @type {HTMLTextAreaElement} */ (event.target).value);
}
</script>

<template>
  <div class="markdown-toolbar">
    <div
      class="markdown-toolbar__bar"
      role="group"
      aria-label="서식 도구"
    >
      <button
        v-for="tool in TOOLS"
        :key="tool.kind"
        type="button"
        class="btn btn-sm btn-outline-secondary markdown-toolbar__btn"
        :aria-label="tool.aria"
        :disabled="disabled || mode === 'preview'"
        @click="applyTool(tool.kind)"
      >
        {{ tool.label }}
      </button>

      <div class="markdown-toolbar__spacer" />

      <button
        type="button"
        class="btn btn-sm btn-outline-secondary markdown-toolbar__btn"
        :class="{ 'is-active': mode === 'write' }"
        :aria-pressed="mode === 'write'"
        @click="mode = 'write'"
      >
        작성
      </button>
      <button
        type="button"
        class="btn btn-sm btn-outline-secondary markdown-toolbar__btn"
        :class="{ 'is-active': mode === 'preview' }"
        :aria-pressed="mode === 'preview'"
        @click="mode = 'preview'"
      >
        미리보기
      </button>
    </div>

    <!-- v-show(display:none) 대신 시각적으로만 가린다 — display:none인 요소는 브라우저의
         폼 검증 대상에서 빠진다(제약 검증 제외 규칙). 미리보기 상태로 등록을 눌러도
         내용이 비어 있으면 required가 그대로 막아야 하므로, 이 textarea는 미리보기
         상태에서도 계속 검증 대상으로 DOM에 남아 있어야 한다. -->
    <textarea
      :id="id"
      ref="textarea"
      class="form-control post-edit__textarea"
      :class="{ 'markdown-toolbar__textarea--hidden': mode === 'preview' }"
      :maxlength="maxlength"
      :placeholder="placeholder"
      :disabled="disabled"
      required
      :value="modelValue"
      @input="onInput"
    />
    <div
      v-show="mode === 'preview'"
      class="markdown-toolbar__preview post-body post-body--md"
    >
      <MarkdownBody :source="modelValue" />
    </div>

    <p class="markdown-toolbar__hint">
      **굵게** · *기울임* · `코드` · 줄 앞 "- "로 목록 · [글자](https://주소)로 링크
    </p>
  </div>
</template>
