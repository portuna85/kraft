import { byId, on, setBusy, setHidden, setText, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { API } from '../core/constants.js';
import * as flash from '../ui/flash.js';
import { createImageUploadField } from './image-upload.js';

/**
 * 게시글 등록 화면(post-save.html).
 *
 * 제목·내용의 필수 검사는 HTML `required`에 맡긴다 — 브라우저가 submit 이벤트 자체를 막으므로
 * JS로 한 번 더 검사하면 도달하지 않는 코드가 된다(예전에 그런 검사가 있었다).
 */
export function init() {
    const form = byId('post-save-form');
    if (!form) {
        return;
    }

    const picture = createImageUploadField({
        inputId: 'picture',
        previewId: 'picture-preview',
        previewImageId: 'picture-preview-image',
        previewNameId: 'picture-preview-name',
        clearButtonId: 'btn-picture-clear',
    });

    on(form, 'submit', async (event) => {
        event.preventDefault();
        await save(picture);
    });
}

function setProgress(text) {
    const progress = byId('post-save-progress');
    if (!progress) {
        return;
    }
    setText(progress, text ?? '');
    setHidden(progress, !text);
}

async function save(picture) {
    const button = byId('btn-save');
    setBusy(button, true);

    let pictureUrl = null;
    try {
        if (picture.hasFile()) {
            setProgress('이미지 업로드 중…');
            pictureUrl = await picture.resolveUrl();
        }
    } catch (error) {
        setProgress(null);
        setBusy(button, false);
        flash.showError(`이미지 업로드에 실패했습니다. ${messageOf(error)}`);
        return;
    }

    setProgress('게시글 등록 중…');
    try {
        await api.post(API.POSTS, {
            title: valueOf(byId('title')),
            content: valueOf(byId('content')),
            picture: pictureUrl,
            category: valueOf(byId('category')),
        });
        picture.revokePreview();
        flash.set('POST_SAVED');
        window.location.href = '/';
    } catch (error) {
        setProgress(null);
        setBusy(button, false);
        // 업로드까지는 끝났다는 사실을 알려야 사용자가 파일을 다시 고르지 않는다.
        const retryHint = pictureUrl
            ? ' 이미지는 이미 업로드되어 있으니 다시 "등록"을 누르면 같은 이미지로 재시도합니다.'
            : '';
        flash.showError(`게시글 등록에 실패했습니다. ${messageOf(error)}${retryHint}`);
    }
}
