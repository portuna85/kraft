<script setup>
import { computed, nextTick, ref } from 'vue';
import { POST } from '@core/constants.js';
import { applyMarkup, nextToolbarIndex } from './markdownToolbar.js';
import MarkdownBody from './MarkdownBody.vue';

/**
 * 글쓰기·수정 폼의 본문 입력 위에 얹는 마크다운 툴바. `PostSaveApp.vue`·`PostEditApp.vue`가 같이 쓰며, `v-model`로 `draft.content`를 그대로 바꾸므로 자동 임시 저장과 맞물린다.
 *
 * textarea는 슬롯이 아니라 이 컴포넌트가 직접 그린다 — 선택 영역(selectionStart/End)을 읽고 커서를 되돌리려면 같은 컴포넌트 안에서 ref로 붙잡고 있어야 한다.
 *
 * 서식 버튼 묶음은 WAI-ARIA 툴바 패턴(role="toolbar" + roving tabindex)이다. 작성/미리보기 전환은 서식과 다른 종류의 조작이라 별도 role="group"으로 뺐다. Ctrl/⌘+B·I 단축키를 지원한다.
 */
const props = defineProps({
    modelValue: { type: String, required: true },
    id: { type: String, required: true },
    disabled: { type: Boolean, default: false },
    maxlength: { type: Number, default: POST.CONTENT_MAX_LENGTH },
    placeholder: { type: String, default: '' },
    // 서버 검증 오류가 이 필드를 가리킬 때 부모가 넘긴다. aria-invalid가 루트 div가 아니라 textarea에 직접 걸리도록 명시적인 prop으로 받는다.
    invalid: { type: Boolean, default: false },
    // 오류 메시지 요소의 id. 서식 도움말(hintId)과 함께 aria-describedby에 합쳐진다.
    describedby: { type: String, default: undefined },
});
const emit = defineEmits(['update:modelValue']);

const textarea = ref(/** @type {HTMLTextAreaElement | null} */ (null));
const mode = ref('write'); // 'write' | 'preview'
const hintId = `${props.id}-markdown-hint`;
const describedBy = computed(() => [props.describedby, hintId].filter(Boolean).join(' '));

const TOOLS = [
    { kind: 'bold', label: '굵게', aria: '굵게' },
    { kind: 'italic', label: '기울임', aria: '기울임' },
    { kind: 'code', label: '코드', aria: '코드' },
    { kind: 'ul', label: '목록', aria: '글머리 목록' },
    { kind: 'ol', label: '번호', aria: '번호 목록' },
    { kind: 'link', label: '링크', aria: '링크' },
];

// roving tabindex: 이 인덱스의 버튼만 tabindex="0"이다. 툴바에 처음 Tab으로 들어오면 항상 첫 버튼에서 시작한다(마지막 사용 버튼을 기억하지 않는다 — 예측 가능성을 우선한다).
const activeToolIndex = ref(0);
const toolButtons = ref(/** @type {HTMLButtonElement[]} */ ([]));

async function applyTool(kind, index) {
    activeToolIndex.value = index;
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

/** @param {KeyboardEvent} event */
function onToolbarKeydown(event) {
    const nextIndex = nextToolbarIndex(activeToolIndex.value, event.key, TOOLS.length);
    if (nextIndex === activeToolIndex.value) {
        return;
    }
    event.preventDefault();
    activeToolIndex.value = nextIndex;
    toolButtons.value[nextIndex]?.focus();
}

/** @param {Event} event */
function onInput(event) {
    emit('update:modelValue', /** @type {HTMLTextAreaElement} */ (event.target).value);
}

/** Ctrl/⌘+B, Ctrl/⌘+I 단축키. 브라우저 기본 동작(굵게 서식 등)을 막고 툴바와 같은 처리를 한다. */
function onTextareaKeydown(event) {
    if (!(event.ctrlKey || event.metaKey) || event.shiftKey || event.altKey) {
        return;
    }
    if (event.key === 'b' || event.key === 'B') {
        event.preventDefault();
        applyTool('bold', 0);
    } else if (event.key === 'i' || event.key === 'I') {
        event.preventDefault();
        applyTool('italic', 1);
    }
}

// v-show(display:none) 대신 시각적으로만 가린다 — display:none인 요소는 브라우저 폼 검증 대상에서 빠지는데, 미리보기 상태로 등록을 눌러도 내용이 비어 있으면 required가 막아야 한다.
// 대신 tabindex="-1"·aria-hidden="true"로 탐색·스크린 리더 낭독에서는 뺀다. 브라우저는 제출 시 invalid 필드에 자동으로 포커스를 시도하는데 그때 시각적으로 숨겨져 있으면
// 사용자가 아무 반응도 못 보므로, invalid 이벤트에서 먼저 작성 모드로 돌아온 뒤 직접 포커스한다.
function onInvalid() {
    mode.value = 'write';
    nextTick(() => {
        textarea.value?.focus();
    });
}

// 서버 검증 오류가 이 필드를 가리킬 때 부모가 포커스를 옮길 수 있게 한다 — onInvalid와 같은 이유로 먼저 작성 모드로 돌아온다.
defineExpose({
    focus() {
        mode.value = 'write';
        nextTick(() => textarea.value?.focus());
    },
});
</script>

<template>
  <div class="markdown-toolbar">
    <div
      class="markdown-toolbar__bar"
      role="toolbar"
      aria-label="서식 도구"
      @keydown="onToolbarKeydown"
    >
      <button
        v-for="(tool, index) in TOOLS"
        :key="tool.kind"
        ref="toolButtons"
        type="button"
        class="btn btn-sm btn-outline-secondary markdown-toolbar__btn"
        :aria-label="tool.aria"
        :tabindex="index === activeToolIndex ? 0 : -1"
        :disabled="disabled || mode === 'preview'"
        @click="applyTool(tool.kind, index)"
      >
        {{ tool.label }}
      </button>
    </div>

    <div
      class="markdown-toolbar__view-switch"
      role="group"
      aria-label="보기 방식"
    >
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

    <textarea
      :id="id"
      ref="textarea"
      class="form-control post-edit__textarea"
      :class="{ 'markdown-toolbar__textarea--hidden': mode === 'preview', 'is-invalid': invalid }"
      :tabindex="mode === 'preview' ? -1 : 0"
      :aria-hidden="mode === 'preview'"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-describedby="describedBy"
      :maxlength="maxlength"
      :placeholder="placeholder"
      :disabled="disabled"
      required
      :value="modelValue"
      @input="onInput"
      @keydown="onTextareaKeydown"
      @invalid="onInvalid"
    />
    <!-- v-show가 아니라 v-if다 — 숨겨 둔 채로는 키 입력마다 parseMarkdown이 돈다. 미리보기를 켤 때만 렌더링(=파싱)한다. -->
    <div
      v-if="mode === 'preview'"
      class="markdown-toolbar__preview post-body post-body--md"
      role="region"
      aria-label="미리보기"
    >
      <MarkdownBody :source="modelValue" />
    </div>

    <p
      :id="hintId"
      class="markdown-toolbar__hint"
    >
      **굵게** · *기울임* · `코드` · 줄 앞 "- "로 목록 · [글자](https://주소)로 링크 ·
      Ctrl/⌘+B 굵게 · Ctrl/⌘+I 기울임
    </p>
  </div>
</template>
