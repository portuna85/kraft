<script setup>
import { computed, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { readItem, writeItem } from '@core/storage.js';
import { showToast } from '@ui/toast.js';
import MarkdownBody from '../shared/MarkdownBody.vue';
import PostAdminBar from './PostAdminBar.vue';

/**
 * 게시글 읽기 화면(FE-12): 본문·글자 크기·공유·추천·신고/수정/삭제 버튼. 편집 폼은 PostEditApp이 맡고,
 * 이 컴포넌트는 "수정"을 누르면 `edit`만 알린다. 추천은 서버가 반환한 상태만 반영한다.
 */
const props = defineProps({
    post: { type: /** @type {import('vue').PropType<import('../shared/types.js').PostViewDto>} */ (Object), required: true },
    categoryOptions: { type: /** @type {import('vue').PropType<import('../shared/types.js').CategoryOption[]>} */ (Array), required: true },
    authenticated: { type: Boolean, required: true },
});
defineEmits(['edit']);

// 로그인 후 이 글로 돌아오게 한다(FE-16) — navbar의 로그인 링크와 같은 규칙
// (NavModelAdvice.currentPath)이다. 예전에는 href="/login"만 써서 로그인 뒤 홈으로 떨어졌다.
const loginHref = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;

const liked = ref(props.post.likedByMe);
const likeCount = ref(props.post.likeCount);
const liking = ref(false);
const editButton = ref(/** @type {HTMLButtonElement | null} */ (null));

// 글자크기 조절: 3단계(작게/보통/크게), 세션을 넘어 유지하도록 localStorage에 기억한다.
// localStorage 접근이 막힌 환경(프라이빗 모드 등)에서도 화면은 기본값으로 그대로 동작해야
// 하므로 읽기·쓰기 모두 조용히 실패를 삼킨다.
const FONT_SCALE_STORAGE_KEY = 'kraft:post-font-scale';
const FONT_SCALES = ['0.875rem', '1rem', '1.125rem'];
const DEFAULT_FONT_SCALE_INDEX = 1;

function readStoredFontScaleIndex() {
    const raw = readItem(FONT_SCALE_STORAGE_KEY);
    if (raw === null) {
        return DEFAULT_FONT_SCALE_INDEX;
    }
    const stored = Number(raw);
    return Number.isInteger(stored) && stored >= 0 && stored < FONT_SCALES.length
        ? stored
        : DEFAULT_FONT_SCALE_INDEX;
}

const fontScaleIndex = ref(readStoredFontScaleIndex());
const postBodyStyle = computed(() => ({ '--kraft-post-font-size': FONT_SCALES[fontScaleIndex.value] }));

function setFontScaleIndex(index) {
    fontScaleIndex.value = index;
    // 저장 실패는 이번 열람에서만 크기가 적용되는 정도로 넘어간다.
    writeItem(FONT_SCALE_STORAGE_KEY, String(index));
}

/**
 * navigator.share가 있으면(iOS Safari·Android Chrome) 네이티브 공유 시트를 먼저 띄운다
 * (A-FE-14) — 메시지 앱으로 바로 보내기 같은, 클립보드 복사보다 나은 경로를 그 플랫폼이
 * 이미 제공하기 때문이다. 없는 브라우저(대부분의 데스크톱)는 기존 클립보드 복사로 물러선다.
 * 사용자가 공유 시트를 취소하면 AbortError가 나는데, 이때는 클립보드로도 대신 복사하지
 * 않는다 — 취소는 "공유하지 않겠다"는 의사 표시라 조용히 끝나는 것이 맞고, 그런데도 뭔가
 * 복사됐다는 토스트가 뜨면 오히려 혼란스럽다.
 */
async function shareLink() {
    if (navigator.share) {
        try {
            await navigator.share({ title: props.post.title, url: window.location.href });
        } catch (error) {
            if (error?.name !== 'AbortError') {
                showToast('공유에 실패했습니다. 주소창의 URL을 직접 복사해 주세요.', 'danger');
            }
        }
        return;
    }

    try {
        await navigator.clipboard.writeText(window.location.href);
        showToast('링크를 복사했습니다.', 'success');
    } catch {
        showToast('링크 복사에 실패했습니다. 주소창의 URL을 직접 복사해 주세요.', 'danger');
    }
}

async function setLike() {
    if (liking.value) {
        return;
    }
    liking.value = true;
    try {
        const result = await api.put(`${API.POSTS}/${props.post.id}/like`, { liked: !liked.value });
        liked.value = result.liked;
        likeCount.value = result.likeCount;
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        liking.value = false;
    }
}

function categoryTitle(value) {
    return props.categoryOptions.find((option) => option.value === value)?.title ?? value;
}

// 취소하고 돌아왔을 때 포커스를 "수정" 버튼으로 되돌리는 데 쓴다(PostEditApp.cancelEdit).
defineExpose({ focusEditButton: () => editButton.value?.focus() });
</script>

<template>
  <article id="post-view">
    <PostAdminBar :post="post" />
    <span class="post-category">{{ categoryTitle(post.category) }}</span>
    <h1
      id="post-title-text"
      class="post-title"
    >
      {{ post.title }}
    </h1>
    <p class="post-byline">
      <span>{{ post.author }}</span> · 글 번호 <span>{{ post.id }}</span>
      · 조회 <span>{{ post.viewCount }}</span>
    </p>

    <div
      class="post-font-controls"
      role="group"
      aria-label="글자 크기 조절"
    >
      <button
        v-for="(item, index) in [
          { label: '가-', name: '글자 작게' },
          { label: '가', name: '글자 보통' },
          { label: '가+', name: '글자 크게' },
        ]"
        :key="item.label"
        type="button"
        class="btn btn-sm btn-outline-secondary post-font-controls__btn"
        :class="{ 'is-active': fontScaleIndex === index }"
        :aria-pressed="fontScaleIndex === index"
        :aria-label="item.name"
        @click="setFontScaleIndex(index)"
      >
        {{ item.label }}
      </button>
    </div>

    <div
      id="post-content-text"
      class="post-body post-body--md"
      :style="postBodyStyle"
    >
      <MarkdownBody :source="post.content" />
    </div>

    <div
      v-if="post.picture"
      class="post-image"
    >
      <!-- 상세 본문 이미지는 대개 첫 화면(LCP 후보)이다(A-FE-09). 크기를 알면(V32 이후
           저장된 글) eager+높은 우선순위로 바꾸고 width/height로 레이아웃 이동(CLS)을
           막는다 — 크기를 모르는 옛 글은 이전 동작(lazy, 속성 없음) 그대로 둔다. -->
      <img
        :src="post.picture"
        :width="post.pictureWidth ?? undefined"
        :height="post.pictureHeight ?? undefined"
        alt="게시글 첨부 이미지"
        :loading="post.pictureWidth ? 'eager' : 'lazy'"
        :fetchpriority="post.pictureWidth ? 'high' : undefined"
        decoding="async"
      >
    </div>

    <div class="btn-group-gap post-actions">
      <button
        id="btn-share"
        type="button"
        class="btn btn-sm btn-outline-secondary"
        @click="shareLink"
      >
        공유
      </button>
    </div>

    <!-- 삭제된 글(관리자에게만 열린다)에는 추천·신고·수정·삭제를 두지 않는다 — 복구만 할 수 있다. -->
    <div
      v-if="authenticated && !post.deleted"
      class="btn-group-gap post-actions"
    >
      <button
        id="btn-like"
        type="button"
        class="btn btn-outline-primary post-like"
        :class="{ 'is-active': liked }"
        :aria-pressed="liked"
        :disabled="liking"
        @click="setLike"
      >
        추천 <span id="like-count">{{ likeCount }}</span>
      </button>
    </div>
    <p
      v-else-if="!authenticated"
      v-once
      class="post-actions"
    >
      추천 <span>{{ post.likeCount }}</span>개 ·
      <a :href="loginHref">로그인 후 추천할 수 있습니다.</a>
    </p>

    <div
      v-if="post.canManagePost"
      class="btn-group-gap post-actions"
    >
      <button
        id="btn-edit"
        ref="editButton"
        type="button"
        class="btn btn-outline-primary"
        @click="$emit('edit')"
      >
        수정
      </button>
      <button
        id="btn-delete-post"
        type="button"
        class="btn btn-outline-danger"
        data-target-kind="post"
        :data-target-name="post.title"
      >
        삭제
      </button>
    </div>
  </article>
</template>
