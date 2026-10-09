<script setup>
import { computed, nextTick, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { formatDateTime } from '@core/datetime.js';
import { API } from '@core/constants.js';
import { ifMatchHeaders } from '@core/etag.js';
import { showToast } from '@ui/toast.js';
import { replyAfterId } from './commentState.js';

/**
 * 댓글 한 건의 읽기뷰/인라인 수정폼. 삭제 버튼은 여기서 처리하지 않는다 — 게시글과 공용인 삭제 모달(delete-confirm.js)이 document 위임으로 집어간다.
 * 답글(2단계)도 같은 컴포넌트로 그리며({@code isReply=true}) "답글" 버튼을 숨겨 3단계를 UI에서도 막는다. 최종 판정은 서버다(CommentService.resolveParent).
 */
const props = defineProps({
    comment: { type: /** @type {import('vue').PropType<import('../shared/types.js').CommentViewDto>} */ (Object), required: true },
    authenticated: { type: Boolean, required: true },
    // 답글 자신이면 true — "답글" 버튼과 중첩 답글 목록을 그리지 않는다.
    isReply: { type: Boolean, default: false },
    // 이메일 인증까지 끝난 회원만 답글을 쓸 수 있다 — CommentsApp의 새 댓글 폼과 같은 정책.
    canWrite: { type: Boolean, default: false },
    postId: { type: String, required: true },
});

const emit = defineEmits(['updated', 'replied', 'moreRepliesLoaded']);

const editing = ref(false);
const draftContent = ref(props.comment.content);
const saving = ref(false);
const editTextarea = ref(/** @type {HTMLTextAreaElement | null} */ (null));
const editButton = ref(/** @type {HTMLButtonElement | null} */ (null));

const replying = ref(false);
const replyContent = ref('');
const replySaving = ref(false);
const replyTextarea = ref(/** @type {HTMLTextAreaElement | null} */ (null));
const replyButton = ref(/** @type {HTMLButtonElement | null} */ (null));

async function startReply() {
    replyContent.value = '';
    replying.value = true;
    await nextTick();
    replyTextarea.value?.focus();
}

// 취소·저장 성공 모두 폼을 닫는다 — 트리거 버튼으로 포커스를 되돌리지 않으면 숨겨진 입력창·저장 버튼이 여전히 활성 요소로 남는다.
async function returnFocusToReplyButton() {
    await nextTick();
    replyButton.value?.focus();
}

async function cancelReply() {
    if (replySaving.value) {
        return;
    }
    replying.value = false;
    await returnFocusToReplyButton();
}

async function saveReply() {
    if (replySaving.value) {
        return;
    }
    const content = replyContent.value;
    replySaving.value = true;
    try {
        const saved = await api.post(`${API.POSTS}/${props.postId}/comments`, {
            content,
            parentId: props.comment.id,
        });
        // 서버가 확정한 id·createdAt·version을 그대로 쓴다(직접 만든 시각은 새로고침 뒤 달라 보였고, version이 없으면 새로고침 전 수정이 서버 검사를 건너뛰었다).
        emit('replied', {
            parentId: props.comment.id,
            reply: saved,
        });
        replying.value = false;
        await returnFocusToReplyButton();
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        replySaving.value = false;
    }
}

async function startEdit() {
    draftContent.value = props.comment.content;
    editing.value = true;
    await nextTick();
    editTextarea.value?.focus();
}

async function returnFocusToEditButton() {
    await nextTick();
    editButton.value?.focus();
}

async function cancelEdit() {
    // 저장 요청 중 취소하면 폼은 사라져도 응답이 도착해 취소한 내용으로 되돌아온다 — 버튼은 saving일 때 비활성화되지만 방어적으로 여기서도 막는다.
    if (saving.value) {
        return;
    }
    editing.value = false;
    await returnFocusToEditButton();
}

async function save() {
    if (saving.value) {
        return;
    }
    // 요청에 보낸 값과 updated 이벤트에 담는 값이 갈리지 않게 시작 시점에 한 번만 읽는다(프로그램에 의한 변경까지 막는 방어).
    const content = draftContent.value;
    const requestVersion = props.comment.version;
    saving.value = true;
    try {
        const saved = await api.put(`${API.COMMENTS}/${props.comment.id}`, { content }, {
            // 편집을 시작할 때 받아간 버전을 If-Match로 보낸다(서버는 기준 버전 없는 수정을 받지 않는다 — 방금 만든 댓글도 등록 응답의 version을 들고 있다). 그 사이 저장됐으면 412.
            headers: ifMatchHeaders(requestVersion),
        });
        // 서버가 실제로 반영한 version을 쓴다 — "+1" 추측은 내용이 바뀌지 않아 DB 버전이 그대로일 때 어긋나 다음 정상 수정이 가짜 충돌(412)을 받는다.
        emit('updated', { id: props.comment.id, content: saved.content, version: saved.version });
        editing.value = false;
        await returnFocusToEditButton();
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        saving.value = false;
    }
}

// 답글 더 보기. 최초 페이지는 부모당 답글을 일부만 내려주므로(서버 INITIAL_REPLIES_PER_PARENT) comment.hasMoreReplies가 true면 이어서 부른다.
const loadingMoreReplies = ref(false);

async function loadMoreReplies() {
    if (loadingMoreReplies.value) {
        return;
    }
    // 화면 배열의 마지막 id가 아니라 서버 페이지로 받은 마지막 답글 id를 쓴다 — 새로 쓴 답글이 끝에 있으면 아직 받지 않은 답글을 건너뛴다.
    const afterId = replyAfterId(props.comment);
    loadingMoreReplies.value = true;
    try {
        const page = await api.get(`${API.COMMENTS}/${props.comment.id}/replies?afterId=${afterId}`);
        emit('moreRepliesLoaded', { parentId: props.comment.id, replies: page.comments, hasMore: page.hasMore });
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        loadingMoreReplies.value = false;
    }
}

// 관리자가 숨긴 댓글을 관리자가 아닌 사람이 보는 경우 — 서버가 내용을 비워 보낸다.
const masked = computed(() => props.comment.blinded && !props.comment.canModerate);

/** 버튼 줄에 그릴 것이 하나라도 있는가 — 삭제되거나 숨겨진(가려진) 댓글은 답글만 가능하다. */
const hasActions = computed(() => (
    props.comment.deleted || masked.value
        ? !props.isReply && props.canWrite
        : props.comment.canManage || props.authenticated
));

const moderating = ref(false);

/**
 * 댓글을 숨기거나('blind') 숨김을 푼다('unblind'). 관리자만 쓰며, 성공하면 서버가 새로 렌더링한 화면으로 다시 불러온다.
 *
 * @param {'blind' | 'unblind'} action
 */
async function moderate(action) {
    if (moderating.value) {
        return;
    }
    moderating.value = true;
    try {
        await api.post(`${API.ADMIN_COMMENTS}/${props.comment.id}/${action}`);
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        moderating.value = false;
    }
}
</script>

<template>
  <li
    class="comment-list__item"
    :data-comment-id="comment.id"
  >
    <div
      v-show="!editing"
      class="comment-view"
    >
      <div class="comment-list__head">
        <strong>{{ comment.author }}</strong>
        <small class="text-muted">
          <time :datetime="comment.createdAt">{{ formatDateTime(comment.createdAt) }}</time>
        </small>
      </div>
      <!-- 답글이 있어 행은 남기고 내용만 비운 댓글 — 수정·삭제는 숨기고 답글은 계속 달 수 있게 둔다(대화가 이어져야 한다). -->
      <p
        v-if="comment.deleted"
        class="comment-list__content comment-list__content--deleted text-muted"
      >
        삭제된 댓글입니다.
      </p>
      <p
        v-else-if="masked"
        class="comment-list__content comment-list__content--deleted text-muted"
      >
        관리자가 숨긴 댓글입니다.
      </p>
      <p
        v-else
        class="comment-list__content"
      >
        <small
          v-if="comment.blinded"
          class="text-muted me-1"
        >[숨김]</small>{{ comment.content }}
      </p>
      <!-- 행동 버튼 줄은 한 번만 그린다: 삭제된 댓글은 답글만, 내 댓글은 수정·삭제, 답글 버튼은 모두 같은 자리 하나에서. -->
      <div
        v-if="hasActions"
        class="btn-group-gap comment-actions"
      >
        <template v-if="!comment.deleted && comment.canManage">
          <button
            ref="editButton"
            type="button"
            class="btn btn-sm btn-outline-secondary btn-comment-edit"
            :aria-label="`${comment.author}의 댓글 수정`"
            @click="startEdit"
          >
            수정
          </button>
          <button
            type="button"
            class="btn btn-sm btn-outline-danger btn-comment-delete"
            data-target-kind="comment"
            :data-target-name="comment.content"
            :aria-label="`${comment.author}의 댓글 삭제`"
          >
            삭제
          </button>
        </template>
        <button
          v-if="comment.canModerate && !comment.blinded && !comment.deleted"
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-blind"
          :disabled="moderating"
          :aria-label="`${comment.author}의 댓글 숨기기`"
          @click="moderate('blind')"
        >
          숨기기
        </button>
        <button
          v-if="comment.blinded && comment.canModerate"
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-unblind"
          :disabled="moderating"
          :aria-label="`${comment.author}의 댓글 숨김 해제`"
          @click="moderate('unblind')"
        >
          숨김 해제
        </button>
        <!-- 답글 버튼은 최상위 댓글에만 보인다(isReply면 그리지 않아 3단계를 UI에서도 막는다). 최종 판정은 서버(CommentService.resolveParent). -->
        <button
          v-if="!isReply && canWrite"
          ref="replyButton"
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-reply"
          :aria-label="`${comment.author}의 댓글에 답글 달기`"
          @click="startReply"
        >
          답글
        </button>
      </div>
    </div>

    <!-- 열렸을 때만 마운트한다(v-if). draftContent/replyContent는 setup 스코프의 ref라 폼이 사라져도 값은 남는다. -->
    <form
      v-if="!isReply && replying"
      class="comment-reply-form"
      @submit.prevent="saveReply"
    >
      <div class="mb-3">
        <label
          class="visually-hidden"
          :for="`comment-reply-${comment.id}`"
        >답글 내용</label>
        <textarea
          :id="`comment-reply-${comment.id}`"
          ref="replyTextarea"
          v-model="replyContent"
          class="form-control comment-edit__textarea"
          placeholder="답글을 입력하세요"
          maxlength="1000"
          required
          :disabled="replySaving"
        />
      </div>
      <div class="btn-group-gap">
        <button
          type="button"
          class="btn btn-sm btn-secondary btn-comment-reply-cancel"
          :disabled="replySaving"
          @click="cancelReply"
        >
          취소
        </button>
        <button
          type="submit"
          class="btn btn-sm btn-primary btn-comment-reply-save"
          :disabled="replySaving"
        >
          답글 등록
        </button>
      </div>
    </form>
    <!-- canManage가 아니면 editing을 true로 만들 경로가 없지만(수정 버튼 자체가 canManage일 때만 그려진다) 방어적으로 조건에 함께 넣는다. -->
    <form
      v-if="editing && comment.canManage"
      class="comment-edit-form"
      @submit.prevent="save"
    >
      <div class="mb-3">
        <label
          class="visually-hidden"
          :for="`comment-edit-${comment.id}`"
        >댓글 내용</label>
        <textarea
          :id="`comment-edit-${comment.id}`"
          ref="editTextarea"
          v-model="draftContent"
          class="form-control comment-edit__textarea"
          maxlength="1000"
          required
          :disabled="saving"
        />
      </div>
      <div class="btn-group-gap">
        <button
          type="button"
          class="btn btn-sm btn-secondary btn-comment-cancel"
          :disabled="saving"
          @click="cancelEdit"
        >
          취소
        </button>
        <button
          type="submit"
          class="btn btn-sm btn-primary btn-comment-save"
          :disabled="saving"
        >
          저장
        </button>
      </div>
    </form>

    <!-- 답글 목록. 2단계뿐이라 재귀는 여기서 끝난다 — isReply=true로 넘겨 답글에는 "답글" 버튼도 중첩 목록도 그려지지 않는다. -->
    <ul
      v-if="!isReply && comment.replies && comment.replies.length > 0"
      class="comment-list comment-list__replies"
    >
      <CommentItem
        v-for="reply in comment.replies"
        :key="reply.id"
        :comment="reply"
        :authenticated="authenticated"
        :is-reply="true"
        :can-write="canWrite"
        :post-id="postId"
        @updated="emit('updated', $event)"
      />
    </ul>

    <!-- 부모 하나당 답글을 일부만 내려받았을 때만 보인다(comment.hasMoreReplies). -->
    <button
      v-if="!isReply && comment.hasMoreReplies"
      type="button"
      class="btn btn-sm btn-outline-secondary btn-comment-replies-load-more"
      :disabled="loadingMoreReplies"
      @click="loadMoreReplies"
    >
      답글 더 보기
    </button>
  </li>
</template>
