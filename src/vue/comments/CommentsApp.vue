<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import { showToast } from '@ui/toast.js';
import * as flash from '@ui/flash.js';
import CommentItem from './CommentItem.vue';

/**
 * 댓글 목록 전체. 서버가 최초 렌더링 시 canManage까지 계산해 내려준 목록(initialComments)을
 * 그대로 초기 상태로 쓰고, 이후 생성·수정·삭제는 서버를 다시 조회하지 않고 로컬 상태만
 * 갱신한다(낙관적 갱신) — 새로고침하면 서버가 다시 정확한 canManage를 계산해 주므로 정합성
 * 문제는 없다.
 */
const props = defineProps({
    postId: { type: String, required: true },
    authenticated: { type: Boolean, required: true },
    // 이메일 인증까지 끝난 회원만 댓글을 쓸 수 있다(WriteAccessPolicy). 로그인만 한 GUEST에게
    // 입력창을 보여주면 다 쓰고 등록을 눌러야 거절 사유를 알게 되므로, 글쓰기 화면처럼 먼저 알린다.
    canWrite: { type: Boolean, required: true },
    // 쓸 수 없는 이유. 서버가 작성 경로와 같은 규칙으로 만든 문장을 그대로 보여준다.
    writeBlockReason: { type: String, default: '' },
    initialComments: { type: Array, required: true },
    // 로드된 배열 길이와는 별개로, 전체 댓글 수를 항상 정확히 보여주기 위한 값이다.
    initialTotalCount: { type: Number, required: true },
    initialHasMore: { type: Boolean, required: true },
});

const comments = reactive([...props.initialComments]);
const totalCount = ref(props.initialTotalCount);
const hasMore = ref(props.initialHasMore);
const loadingMore = ref(false);
// 삭제로 배열에서 항목이 빠져도 "다음 페이지"의 기준은 항상 마지막으로 받아 온 댓글의 id여야
// 한다 — comments 배열 자체에서 매번 다시 구하면 삭제 직후 잘못된 커서를 보낼 수 있다.
const lastLoadedId = ref(props.initialComments.at(-1)?.id ?? null);
const newContent = ref('');
const saving = ref(false);

// 로컬에서 낙관적으로 추가한 새 댓글과 "더 보기"로 받아 온 서버 페이지가 겹칠 수 있다
// (같은 댓글이 새 등록 응답과 다음 페이지 응답 양쪽에 나타남). id 기준으로 중복을 걸러내고
// 항상 오름차순을 유지해, 이미 들어와 있는 항목을 다시 push하지 않는다.
function mergeComments(newItems) {
    const existingIds = new Set(comments.map((c) => String(c.id)));
    for (const item of newItems) {
        if (!existingIds.has(String(item.id))) {
            comments.push(item);
            existingIds.add(String(item.id));
        }
    }
    comments.sort((a, b) => Number(a.id) - Number(b.id));
}

async function loadMore() {
    loadingMore.value = true;
    try {
        const page = await api.get(
            `${API.POSTS}/${props.postId}/comments/page?afterId=${lastLoadedId.value}`,
        );
        mergeComments(page.comments);
        totalCount.value = page.totalCount;
        hasMore.value = page.hasMore;
        if (page.comments.length > 0) {
            lastLoadedId.value = page.comments.at(-1).id;
        }
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        loadingMore.value = false;
    }
}

async function save() {
    // 요청이 진행되는 동안 입력창을 막아 두므로(:disabled="saving"), 응답이 올 때까지
    // content는 바뀌지 않는다 — 서버에 보낸 값과 화면에 표시하는 값을 같은 스냅샷으로 고정한다.
    const content = newContent.value;
    saving.value = true;
    try {
        const id = await api.post(`${API.POSTS}/${props.postId}/comments`, {
            content,
        });
        // 응답은 id뿐이다. 방금 내가 쓴 댓글이므로 관리 가능하고, 작성자 표시는 화면에 이미
        // 렌더링된 로그인 사용자 닉네임(navbar의 #user)을 그대로 쓴다.
        mergeComments([{
            id,
            content,
            author: document.getElementById('user')?.textContent ?? '',
            createdAt: new Date().toISOString(),
            canManage: true,
            replies: [],
        }]);
        // lastLoadedId는 건드리지 않는다 — 아직 안 불러온 더 오래된 댓글이 있다면(hasMore),
        // 새 댓글의 id로 커서를 앞당기면 "더 보기"가 그 구간을 건너뛰게 된다.
        totalCount.value += 1;
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

function onUpdated({ id, content }) {
    const target = findCommentById(id);
    if (target) {
        target.content = content;
    }
    flash.showNow('COMMENT_UPDATED');
}

/** 답글 등록 성공 시 그 부모의 replies에 붙인다(CommentItem이 emit). */
function onReplied({ parentId, reply }) {
    const parent = comments.find((c) => String(c.id) === String(parentId));
    if (parent) {
        if (!parent.replies) {
            parent.replies = [];
        }
        parent.replies.push(reply);
    }
    totalCount.value += 1;
    flash.showNow('COMMENT_SAVED');
}

// 삭제는 게시글과 공유하는 모달(delete-confirm.js)이 처리하고, 끝나면 이 이벤트로 알려온다.
function onExternalDelete(event) {
    const id = event.detail.id;

    // 최상위 댓글이면 그 답글까지 통째로 사라진다 — 백엔드가 ON DELETE CASCADE로 답글을
    // 함께 지우므로, 화면의 전체 개수(totalCount)도 답글 수까지 함께 빼야 서버 상태와
    // 어긋나지 않는다.
    const topIndex = comments.findIndex((c) => String(c.id) === String(id));
    if (topIndex !== -1) {
        const removed = 1 + (comments[topIndex].replies?.length ?? 0);
        comments.splice(topIndex, 1);
        totalCount.value -= removed;
        return;
    }

    // 답글이면 그 부모의 replies에서만 지운다.
    for (const comment of comments) {
        const replyIndex = comment.replies?.findIndex((r) => String(r.id) === String(id)) ?? -1;
        if (replyIndex !== -1) {
            comment.replies.splice(replyIndex, 1);
            totalCount.value -= 1;
            return;
        }
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
    <a href="/login">로그인 후 댓글을 작성할 수 있습니다.</a>
  </p>
</template>
