<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { showToast } from '@ui/toast.js';
import * as flash from '@ui/flash.js';
import CommentItem from './CommentItem.vue';
import { applyDelete, applyRepliesPage, applyReplyCreated, applySoftDelete, initReplyCursor } from './commentState.js';

/**
 * 댓글 목록 전체. 서버가 최초 렌더링 때 canManage까지 계산해 내려준 목록을 초기 상태로 쓰고, 생성·수정·삭제는 각 요청의 성공 응답을 받은 뒤에야 로컬 상태를 그 자리에서
 * 패치한다(낙관적 갱신이 아니다). 새로고침하면 서버가 다시 정확한 canManage를 계산한다.
 */
const props = defineProps({
    postId: { type: String, required: true },
    authenticated: { type: Boolean, required: true },
    // 이메일 인증까지 끝난 회원만 쓸 수 있다(WriteAccessPolicy). 로그인만 한 GUEST에게 입력창을 보이면 등록 뒤에야 거절 사유를 알게 되므로 먼저 알린다.
    canWrite: { type: Boolean, required: true },
    // 쓸 수 없는 이유. 서버가 작성 경로와 같은 규칙으로 만든 문장을 그대로 보여준다.
    writeBlockReason: { type: String, default: '' },
    initialComments: { type: /** @type {import('vue').PropType<import('../shared/types.js').CommentViewDto[]>} */ (Array), required: true },
    // 로드된 배열 길이와는 별개로, 전체 댓글 수를 항상 정확히 보여주기 위한 값이다.
    initialTotalCount: { type: Number, required: true },
    initialHasMore: { type: Boolean, required: true },
});

// 로그인 후 이 글로 돌아오게 한다(navbar의 로그인 링크와 같은 규칙 — NavModelAdvice.currentPath).
const loginHref = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;

const comments = reactive([...props.initialComments]);
comments.forEach(initReplyCursor);
const totalCount = ref(props.initialTotalCount);
const hasMore = ref(props.initialHasMore);
const loadingMore = ref(false);
// 토스트는 지나가면 사라지므로, 토스트를 놓친 사용자를 위해 버튼 자리에 계속 보이는 실패 상태를 둔다.
const loadError = ref(false);
// 다음 페이지 기준은 항상 마지막으로 받아 온 댓글의 id다 — comments 배열에서 매번 구하면 삭제 직후 잘못된 커서를 보낸다. Array.prototype.at()은 iOS 15.4부터라 인덱스로 접근한다.
const lastLoadedId = ref(props.initialComments[props.initialComments.length - 1]?.id ?? null);
const newContent = ref('');
const saving = ref(false);

// totalCount를 로컬에서 증감하는 동작(등록·답글·삭제)마다 올린다. loadMore() 응답 시점에 요청 시작 때와 다르면 그 사이 변경이 있었다는 뜻이라, 정확한 로컬 값을 낡은 page.totalCount로 덮지 않는다.
const mutationSeq = ref(0);
// 삭제한 댓글·답글 id. 삭제와 겹친 "더 보기" 응답에 그 항목이 다시 있어도 되살리지 않고, 같은 삭제 알림이 두 번 와도 한 번만 센다.
const deletedIds = reactive(new Set());

// 등록 응답으로 추가한 새 댓글과 "더 보기" 응답의 서버 페이지가 겹칠 수 있다 — id로 중복을 거르고 오름차순을 유지한다.
function mergeComments(newItems) {
    const existingIds = new Set(comments.map((c) => String(c.id)));
    for (const item of newItems) {
        const id = String(item.id);
        if (!existingIds.has(id) && !deletedIds.has(id)) {
            initReplyCursor(item);
            comments.push(item);
            existingIds.add(id);
        }
    }
    comments.sort((a, b) => Number(a.id) - Number(b.id));
}

async function loadMore() {
    if (loadingMore.value) {
        return;
    }
    loadingMore.value = true;
    loadError.value = false;
    const seqAtStart = mutationSeq.value;
    try {
        const page = await api.get(
            `${API.POSTS}/${props.postId}/comments/page?afterId=${lastLoadedId.value}`,
        );
        mergeComments(page.comments);
        // 요청 중 등록/답글/삭제가 없었을 때만 응답의 totalCount를 믿는다(변경이 있었다면 로컬 값을 유지). 후속 페이지는 서버가 null로 생략할 수도 있다.
        if (mutationSeq.value === seqAtStart && page.totalCount != null) {
            totalCount.value = page.totalCount;
        }
        hasMore.value = page.hasMore;
        if (page.comments.length > 0) {
            lastLoadedId.value = page.comments[page.comments.length - 1].id;
        }
    } catch (error) {
        showToast(messageOf(error), 'danger');
        loadError.value = true;
    } finally {
        loadingMore.value = false;
    }
}

async function save() {
    if (saving.value) {
        return;
    }
    // 입력창이 요청 중 비활성화되어 content가 바뀌지 않으므로, 서버에 보낸 값과 화면에 표시하는 값을 같은 스냅샷으로 고정한다.
    const content = newContent.value;
    saving.value = true;
    try {
        const saved = await api.post(`${API.POSTS}/${props.postId}/comments`, {
            content,
        });
        // 서버가 확정한 id·author·createdAt·version을 그대로 쓴다(직접 만든 시각은 새로고침 뒤 표시가 달랐고, version이 없으면 새로고침 전 수정이 낡은 화면 검사를 건너뛰었다).
        mergeComments([saved]);
        // lastLoadedId는 건드리지 않는다 — 새 댓글 id로 커서를 앞당기면 아직 안 불러온 더 오래된 댓글 구간을 "더 보기"가 건너뛴다.
        totalCount.value += 1;
        mutationSeq.value += 1;
        if (newContent.value === content) {
            newContent.value = '';
        }
        flash.showNow('COMMENT_SAVED');
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        saving.value = false;
    }
}

/** id가 최상위 댓글이든 답글이든 상관없이 찾아 돌려준다(2단계 댓글). */
function findCommentById(id) {
    for (const comment of comments) {
        if (String(comment.id) === String(id)) {
            return comment;
        }
        const reply = comment.replies?.find((r) => String(r.id) === String(id));
        if (reply) {
            return reply;
        }
    }
    return null;
}

function onUpdated({ id, content, version }) {
    const target = findCommentById(id);
    if (target) {
        target.content = content;
        // 저장이 올린 새 버전을 반영한다 — 아니면 새로고침 없이 다시 수정할 때 자신의 편집을 낡은 버전으로 오인해 불필요한 충돌(412)이 난다.
        if (version !== undefined) {
            target.version = version;
        }
    }
    flash.showNow('COMMENT_UPDATED');
}

/** 답글 등록 성공 시 그 부모의 replies에 붙인다(CommentItem이 emit). 새 답글 id로 커서를 앞당기면 아직 받지 않은 답글을 건너뛰므로 커서는 움직이지 않는다. */
function onReplied({ parentId, reply }) {
    const parent = comments.find((c) => String(c.id) === String(parentId));
    if (parent && !applyReplyCreated(parent, reply)) {
        return;
    }
    totalCount.value += 1;
    mutationSeq.value += 1;
    flash.showNow('COMMENT_SAVED');
}

/** 답글 더 보기 응답을 그 부모의 replies에 합친다(중복·삭제된 답글을 거르고 커서를 서버 페이지 기준으로 전진). */
function onMoreRepliesLoaded({ parentId, replies, hasMore }) {
    const parent = comments.find((c) => String(c.id) === String(parentId));
    if (!parent) {
        return;
    }
    applyRepliesPage(parent, replies, hasMore, deletedIds);
}

// 삭제는 게시글과 공유하는 모달(delete-confirm.js)이 처리하고, 끝나면 이 이벤트로 알려온다.
function onExternalDelete(event) {
    const { id, softDeleted } = event.detail;
    // 답글이 있는 최상위 댓글은 행을 지우지 않고 내용만 비운다 — "삭제된 댓글입니다"로 바꿔 보이며 전체 개수는 그대로다.
    if (softDeleted) {
        applySoftDelete(comments, id);
        return;
    }
    // 최상위 댓글은 답글까지 통째로 사라지므로(CommentService.delete) 전체 개수에서 서버 기준 답글 수까지 함께 빼고, 답글이면 부모의 replyCount도 줄인다(규칙: commentState.applyDelete).
    const removed = applyDelete(comments, id, deletedIds);
    if (removed > 0) {
        totalCount.value -= removed;
        mutationSeq.value += 1;
    }
}

onMounted(() => window.addEventListener('kraft:comment-deleted', onExternalDelete));
onUnmounted(() => window.removeEventListener('kraft:comment-deleted', onExternalDelete));
</script>

<template>
  <h2
    id="comments-heading"
    class="comments__heading"
    tabindex="-1"
  >
    댓글 {{ totalCount }}개
  </h2>

  <p
    v-if="comments.length === 0"
    class="comments__empty"
  >
    첫 댓글을 남겨 보세요.
  </p>

  <ul
    v-else
    id="comment-list"
    class="comment-list"
  >
    <CommentItem
      v-for="comment in comments"
      :key="comment.id"
      :comment="comment"
      :authenticated="authenticated"
      :can-write="canWrite"
      :post-id="postId"
      @updated="onUpdated"
      @replied="onReplied"
      @more-replies-loaded="onMoreRepliesLoaded"
    />
  </ul>

  <button
    v-if="hasMore"
    id="btn-comments-load-more"
    type="button"
    class="btn btn-outline-secondary btn-sm mb-3"
    :disabled="loadingMore"
    @click="loadMore"
  >
    댓글 더 보기
  </button>
  <!-- 같은 오류를 토스트(assertive)가 이미 낭독하므로 role="alert"를 또 두지 않는다. -->
  <p
    v-if="loadError"
    class="comments__load-error"
  >
    댓글을 더 불러오지 못했습니다.
    <button
      type="button"
      class="btn btn-link btn-sm"
      @click="loadMore"
    >
      다시 시도
    </button>
  </p>

  <!-- 빈 댓글은 required가 먼저 막는다. Enter는 줄바꿈이어야 하므로 제출 단축키로 쓰지 않는다. -->
  <form
    v-if="canWrite"
    class="mb-3 comment-form"
    @submit.prevent="save"
  >
    <label for="comment-content">댓글 작성</label>
    <textarea
      id="comment-content"
      v-model="newContent"
      class="form-control"
      placeholder="댓글을 입력하세요"
      maxlength="1000"
      required
      :disabled="saving"
    />
    <button
      id="btn-comment-save"
      type="submit"
      class="btn btn-primary mt-2"
      :disabled="saving"
    >
      댓글 등록
    </button>
  </form>
  <p
    v-else-if="authenticated"
    class="comments__login-hint"
  >
    {{ writeBlockReason || '지금은 댓글을 쓸 수 없습니다.' }}
  </p>
  <p
    v-else
    class="comments__login-hint"
  >
    <a :href="loginHref">로그인 후 댓글을 작성할 수 있습니다.</a>
  </p>
</template>
