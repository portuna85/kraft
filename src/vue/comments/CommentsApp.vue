<script setup>
/**
 * 배선 확인용 자리표시자. 실제 댓글 목록/작성/수정 로직은 다음 커밋에서 채운다.
 * 이 시점에는 어떤 템플릿도 #comments-app을 렌더링하지 않으므로 화면 동작에 영향이 없다.
 */
defineProps({
    postId: { type: String, required: true },
    authenticated: { type: Boolean, required: true },
    initialComments: { type: Array, required: true },
});
</script>

<template>
  <div />
</template>
