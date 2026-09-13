/**
 * 서버와 맞춰야 하는 값들.
 *
 * 예전에는 이 상수들이 postEdit·postForm 두 곳에 그대로 복사되어 있었고, 서버의
 * `PostImageService`에도 같은 값이 또 있었다(3중 중복). 화면 쪽은 여기 하나로 모았다.
 *
 * 서버와의 중복은 언어가 달라 없앨 수 없지만, 어긋나면 `UploadPolicySyncTest`가 깨지도록
 * 해 두었다 — 한쪽만 고치는 일을 막기 위해서다.
 *
 * 중요: 여기 있는 검사는 **왕복을 줄이기 위한 편의**일 뿐 서버 검증을 대체하지 않는다.
 * 실제 판정은 항상 서버가 내린다(매직 바이트 검사, 픽셀 수 제한, 계정별 저장량까지).
 */

export const UPLOAD = {
    MAX_BYTES: 5 * 1024 * 1024,
    ALLOWED_EXTENSIONS: ['jpg', 'jpeg', 'png', 'gif', 'webp'],

    /**
     * 아이폰 기본 촬영 포맷(HEIC/HEIF). 허용 목록에 없어서가 아니라 "받아봐야 대부분의
     * 브라우저에서 보이지 않기 때문"에 막는 것이라, 일반 형식 오류와 다른 안내를 준다.
     *
     * 서버는 이름만 바꾼 HEIC까지 잡으려고 더 넓은 집합(heix·mif1 등 10종)을 파일 내용으로
     * 검사한다. 화면은 확장자만 보므로 여기가 더 좁은 것은 의도된 차이다.
     */
    HEIF_EXTENSIONS: ['heic', 'heif'],
};

export const UPLOAD_MESSAGES = {
    HEIF:
        '아이폰 사진 형식(HEIC)은 일부 브라우저에서 표시되지 않아 첨부할 수 없습니다. '
        + "아이폰 [설정] > [카메라] > [포맷]을 '높은 호환성'으로 바꾸면 JPG로 저장됩니다.",
    NOT_ALLOWED: 'JPG, JPEG, PNG, GIF, WEBP 형식만 첨부할 수 있습니다.',
    TOO_LARGE: '파일 크기는 5MB를 초과할 수 없습니다.',
};

export const API = {
    POSTS: '/api/v1/posts',
    POST_IMAGES: '/api/v1/posts/images',
    COMMENTS: '/api/v1/comments',
};
