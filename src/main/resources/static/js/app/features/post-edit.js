import { byId, on, setBusy, setHidden, setText, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { API } from '../core/constants.js';
import * as flash from '../ui/flash.js';
import { createImageUploadField } from './image-upload.js';

/**
 * 게시글 읽기·편집 화면(post-update.html)의 상태 전환과 저장.
 *
 * 편집 폼의 이미지에는 세 가지 상태가 있다:
 *   (1) 손대지 않음      → 저장 시 기존 picture를 그대로 다시 보낸다
 *   (2) 새 파일을 선택함 → 업로드한 URL로 교체한다
 *   (3) "이미지 삭제"    → picture를 null로 보낸다
 * (3)을 표시하는 것이 removedExisting이고, 파일을 다시 고르면 그 표시는 무시된다(파일 우선).
 */
export function init() {
    const form = byId('post-edit');
    if (!form) {
        return;
    }

    // 편집 화면에만 있는 "기존 이미지" 상태. 공용 업로드 필드가 알 필요는 없고, 두 훅으로만 엮인다.
    let removedExisting = false;

    const picture = createImageUploadField({
        inputId: 'edit-picture',
        previewId: 'edit-picture-preview',
        previewImageId: 'edit-picture-preview-image',
        previewNameId: 'edit-picture-preview-name',
        clearButtonId: 'btn-edit-picture-clear',
        // 새 파일을 고르면 기존 이미지는 교체 대상이므로 감춘다.
        onFileAccepted: () => {
            removedExisting = false;
            setHidden(byId('edit-picture-current'), true);
        },
        // 선택을 해제하면, 명시적으로 지운 게 아닌 한 기존 이미지를 다시 보여준다.
        onCleared: () => {
            if (!removedExisting && valueOf(byId('original-picture'))) {
                setHidden(byId('edit-picture-current'), false);
            }
        },
    });

    on(byId('btn-edit'), 'click', () => {
        setHidden(byId('post-view'), true);
        setHidden(form, false);
        byId('title').focus();
    });

    on(byId('btn-edit-picture-remove'), 'click', () => {
        removedExisting = true;
        setHidden(byId('edit-picture-current'), true);
    });

    on(byId('btn-cancel-edit'), 'click', () => {
        if (isDirty(picture, removedExisting) && !window.confirm('변경한 내용을 버리시겠습니까?')) {
            return;
        }
        restore();
        removedExisting = false;
        picture.clear();
        setHidden(form, true);
        setHidden(byId('post-view'), false);
        byId('btn-edit').focus();
    });

    on(form, 'submit', async (event) => {
        event.preventDefault();
        await save(picture, removedExisting);
    });
}

/**
 * 제목·본문·분류·이미지 중 하나라도 바뀌었는지 본다.
 *
 * 분류가 빠져 있던 적이 있다. 그때는 분류만 바꾸고 취소하면 확인창도 뜨지 않은 채 바뀐 값이
 * 남았고, 다시 편집해 저장하면 그때 딸려 들어갔다.
 */
function isDirty(picture, removedExisting) {
    return (
        valueOf(byId('title')) !== valueOf(byId('original-title'))
        || valueOf(byId('content')) !== valueOf(byId('original-content'))
        || valueOf(byId('edit-category')) !== valueOf(byId('original-category'))
        || removedExisting
        || picture.hasFile()
    );
}

function restore() {
    byId('title').value = valueOf(byId('original-title'));
    byId('content').value = valueOf(byId('original-content'));
    byId('edit-category').value = valueOf(byId('original-category'));
}

function setProgress(text) {
    const progress = byId('post-update-progress');
    if (!progress) {
        return;
    }
    setText(progress, text ?? '');
    setHidden(progress, !text);
}

async function save(picture, removedExisting) {
    const button = byId('btn-update');
    setBusy(button, true);

    let pictureUrl;
    try {
        if (picture.hasFile()) {
            setProgress('이미지 업로드 중…');
            pictureUrl = await picture.resolveUrl();
        } else if (removedExisting) {
            pictureUrl = null;
        } else {
            pictureUrl = valueOf(byId('original-picture')) || null;
        }
    } catch (error) {
        setProgress(null);
        setBusy(button, false);
        flash.showError(`이미지 업로드에 실패했습니다. ${messageOf(error)}`);
        return;
    }

    setProgress('게시글 저장 중…');
    try {
        await api.put(`${API.POSTS}/${valueOf(byId('id'))}`, {
            title: valueOf(byId('title')),
            content: valueOf(byId('content')),
            picture: pictureUrl,
            category: valueOf(byId('edit-category')),
            // 편집을 시작할 때 받아간 버전. 그 사이 다른 곳에서 저장됐으면 서버가 409로 거절한다.
            version: valueOf(byId('post-version')),
        });
        picture.revokePreview();
        flash.set('POST_UPDATED');
        window.location.href = '/';
    } catch (error) {
        setProgress(null);
        setBusy(button, false);
        const retryHint = pictureUrl
            ? ' 이미지는 이미 업로드되어 있으니 다시 "저장"을 누르면 같은 이미지로 재시도합니다.'
            : '';
        // 등록 화면과 같은 방식(지속 배너)으로 알린다. 예전에는 여기만 사라지는 토스트를 써서
        // 같은 실패인데 편집 화면에서만 안내가 없어졌다.
        flash.showError(`게시글 저장에 실패했습니다. ${messageOf(error)}${retryHint}`);
    }
}
