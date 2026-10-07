<script setup>
import { computed, nextTick, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { formatDateTime } from '@core/datetime.js';
import { API } from '@core/constants.js';
import { ifMatchHeaders } from '@core/etag.js';
import { showToast } from '@ui/toast.js';
import { replyAfterId } from './commentState.js';

/**
 * 댓글 한 건의 읽기뷰/인라인 수정폼. 삭제 버튼은 여기서 처리하지 않는다 — 게시글과 댓글이
 * 함께 쓰는 공용 삭제 모달(delete-confirm.js)이 document 위임으로 이 버튼을 그대로 집어간다.
 * <p>
 * 답글(2단계 댓글) 자신을 그릴 때도 같은 컴포넌트를 재사용한다({@code isReply=true}) — "답글"
 * 버튼만 숨겨 3단계(답글의 답글)를 UI 단에서도 막는다. 서버가 최종 판정자다
 * (CommentService.save의 resolveParent).
 */
const props = defineProps({
    comment: { type: /** @type {import('vue').PropType<import('../shared/types.js').CommentViewDto>} */ (Object), required: true },
    // 신고 버튼은 로그인한 사람에게만 보인다. 목록이 이 값을 그대로 내려준다.
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

// 취소·저장 성공 모두 폼을 닫는다 — 게시글 편집처럼 그 트리거 버튼으로 포커스를 되돌리지
// 않으면, 방금까지 포커스를 갖고 있던 입력창·저장 버튼이 v-show로 숨겨진 채 여전히 활성
// 요소로 남는다(개선 보고서 F09).
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
        // 서버가 확정한 id·createdAt·version을 그대로 쓴다(개선 보고서 COR-05·COR-08) —
        // 직접 만든 시각은 새로고침 전후로 다르게 보였고, version이 없으면 그 답글을
        // 새로고침 전에 다시 수정할 때 서버 검사를 건너뛰었다.
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
    // 저장 요청이 진행 중일 때 취소하면 폼은 사라지지만 응답은 그대로 도착해, 이미 취소한
    // 내용으로 되돌아온다(개선 보고서 "저장 중 댓글 변경과 동적 삭제 모듈 누락"). 버튼은
    // saving일 때 비활성화되지만, 방어적으로 여기서도 막는다.
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
    // 요청에 실제로 보낸 값과 updated 이벤트에 담는 값이 갈리지 않도록, 시작 시점에 한 번만
    // 읽어 둔다(F02) — 예전에는 요청 본문은 이 시점의 draftContent를, emit은 await가 끝난
    // 뒤의 draftContent를 따로 읽었다. textarea가 saving 중 비활성화돼 일반 입력으로는 그
    // 사이 값이 바뀌지 않지만, 프로그램에 의한 변경까지 막는 방어적 조치다.
    const content = draftContent.value;
    const requestVersion = props.comment.version;
    saving.value = true;
    try {
        const saved = await api.put(`${API.COMMENTS}/${props.comment.id}`, { content }, {
            // 편집을 시작할 때 받아간 버전을 If-Match로 보낸다. 서버는 기준 버전 없는 수정을 받지 않는다(F11) —
            // 방금 이 화면에서 만든 댓글·답글도 등록 응답(CommentViewDto)의 version을 그대로 들고 있다. 그 사이
            // 다른 곳에서 저장됐으면 서버가 412로 거절한다(B12).
            headers: ifMatchHeaders(requestVersion),
        });
        // 서버가 실제로 반영한 version을 그대로 쓴다(개선 보고서 COR-05) — 예전에는
        // "성공했으니 +1"로 추측했는데, 내용이 실제로 바뀌지 않으면 DB의 버전이 그대로라
        // 그 추측이 어긋나 다음 정상 수정이 가짜 충돌(412)을 받았다.
        emit('updated', { id: props.comment.id, content: saved.content, version: saved.version });
        editing.value = false;
        await returnFocusToEditButton();
    } catch (error) {
        showToast(messageOf(error), 'danger');
    } finally {
        saving.value = false;
    }
}

// 답글 더 보기(개선 보고서 COR-05). 최초 페이지는 부모 하나당 답글을 일부만(서버 상수
// INITIAL_REPLIES_PER_PARENT) 내려준다 — comment.hasMoreReplies가 true면 이어서 부른다.
const loadingMoreReplies = ref(false);

async function loadMoreReplies() {
    if (loadingMoreReplies.value) {
        return;
    }
    // 화면 배열의 마지막 id가 아니라 서버 페이지로 받은 마지막 답글 id를 쓴다 — 이 화면에서
    // 새로 쓴 답글이 배열 끝에 있으면 그 사이 아직 받지 않은 답글을 건너뛴다(F02).
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

const unblinding = ref(false);

/** 신고 처리로 숨긴 댓글의 숨김을 푼다(관리자). 서버가 새로 렌더링한 화면으로 다시 불러온다. */
async function unblind() {
    if (unblinding.value) {
        return;
    }
    unblinding.value = true;
    try {
        await api.post(`${API.ADMIN_COMMENTS}/${props.comment.id}/unblind`);
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        unblinding.value = false;
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
      <!-- 답글이 있어 행은 남기고 내용만 비운 댓글이다(개선 보고서 A-BE-06). 수정·삭제·신고는
           숨기고, 답글은 계속 달 수 있게 둔다(대화가 이어질 수 있어야 한다). -->
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
      <!-- 행동 버튼 줄은 한 번만 그린다(FE-28). 삭제된 댓글은 답글만, 내 댓글은 수정·삭제, 남의 댓글은 신고를
           앞에 두고, 답글 버튼은 모두 같은 자리 하나에서 그린다. -->
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
        <!-- 신고는 남의 댓글에만 보인다. 자기 댓글은 서버도 거절한다(직접 지우면 된다). -->
        <button
          v-else-if="!comment.deleted && !masked && authenticated"
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-report"
          data-report-kind="comment"
          :aria-label="`${comment.author}의 댓글 신고`"
        >
          신고
        </button>
        <button
          v-if="comment.blinded && comment.canModerate"
          type="button"
          class="btn btn-sm btn-outline-secondary btn-comment-unblind"
          :disabled="unblinding"
          :aria-label="`${comment.author}의 댓글 숨김 해제`"
          @click="unblind"
        >
          숨김 해제
        </button>
        <!-- 답글은 최상위 댓글에만 보인다 — isReply면 이 버튼 자체를 그리지 않아 3단계(답글의
             답글)를 UI 단에서도 막는다. 최종 판정은 서버가 한다(CommentService.resolveParent). -->
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

    <!-- 열렸을 때만 마운트한다(v-show가 아니라 v-if) — 답글 폼은 열림 여부와 무관하게 항상
         DOM에 있었다(개선 보고서 F11). draftContent/replyContent는 컴포넌트 setup 스코프의
         ref라 폼이 사라져도 값 자체는 남는다. -->
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
    <!-- 같은 이유(F11). canManage가 아닌 사람은 애초에 editing을 true로 만들 경로가 없지만
         (수정 버튼 자체가 canManage일 때만 그려진다), 방어적으로 조건에 함께 넣는다. -->
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

    <!-- 답글 목록. 2단계뿐이므로 재귀는 여기서 끝난다 — isReply=true로 넘겨 답글 자신에게는
         "답글" 버튼도, 또 다른 중첩 목록도 그려지지 않는다. -->
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

    <!-- 부모 하나당 답글을 일부만 내려받았을 때만 보인다(comment.hasMoreReplies, COR-05). -->
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
