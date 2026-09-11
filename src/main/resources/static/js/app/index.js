function showToast(message, type) {
    var $toast = $('#app-toast');
    var $body = $('#app-toast-body');
    var $title = $('#app-toast-title');

    $toast.removeClass('bg-success text-white bg-danger');
    if (type === 'success') {
        $toast.addClass('bg-success text-white');
        $title.text('완료');
    } else if (type === 'danger') {
        $toast.addClass('bg-danger text-white');
        $title.text('오류');
    } else {
        $title.text('알림');
    }

    $body.text(message);
    $toast.toast('show');
}

/**
 * 서버 오류 응답에서 사용자에게 보여줄 메시지를 뽑아낸다.
 * <p>
 * 401/403, 그리고 "JSON을 기대한 요청에 200으로 로그인 페이지 HTML이 돌아온 경우"(세션 만료로
 * 로그인 폼으로 리다이렉트되었지만 브라우저가 그 응답을 그대로 따라가 최종 상태 코드가 200이
 * 되는 상황 — dataType:'json'이 파싱에 실패해 이 함수까지 도달한다)를 구분해 안내한다. 이
 * 경우를 성공으로 처리하지 않는다. 403은 권한 부족일 수도, CSRF·세션 만료일 수도 있으므로
 * 무조건 "인증 만료"로 단정하지 않고 재시도·재로그인을 함께 안내한다.
 */
function extractErrorMessage(xhr) {
    if (!xhr) {
        return '오류가 발생했습니다.';
    }
    if (xhr.responseJSON && xhr.responseJSON.detail) {
        return xhr.responseJSON.detail;
    }
    if (xhr.status === 401 || (xhr.status === 200 && !xhr.responseJSON)) {
        return '로그인이 필요합니다. 다시 로그인해 주세요.';
    }
    if (xhr.status === 403) {
        return '권한이 없거나 세션·보안 토큰이 만료되었습니다. 새로고침 후 다시 시도하거나 다시 로그인해 주세요.';
    }
    if (xhr.status === 0) {
        return '네트워크 오류가 발생했습니다. 다시 시도해 주세요.';
    }
    return '오류가 발생했습니다.';
}

function formatFileSize(bytes) {
    if (bytes < 1024) {
        return bytes + ' B';
    }
    if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(0) + ' KB';
    }
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

/**
 * 이동 후 목적 화면에서 한 번만 표시하는 결과 메시지이자, 폼이 즉시 표시하는 지속 오류 요약.
 * 허용된 키만 저장·표시한다 — 비밀번호·본문·토큰 같은 사용자 입력은 절대 넣지 않는다.
 * sessionStorage를 쓸 수 없는 환경(예외를 던지는 브라우저 설정)에서도 이동 자체는
 * 정상 동작해야 하므로 모든 접근을 try/catch로 감싼다.
 */
var flash = {
    STORAGE_KEY: 'kraft.flash',
    MESSAGES: {
        POST_SAVED: { text: '글이 등록되었습니다.', type: 'success' },
        POST_UPDATED: { text: '글이 수정되었습니다.', type: 'success' },
        POST_DELETED: { text: '글이 삭제되었습니다.', type: 'success' },
        COMMENT_SAVED: { text: '댓글이 등록되었습니다.', type: 'success' },
        COMMENT_UPDATED: { text: '댓글이 수정되었습니다.', type: 'success' },
        COMMENT_DELETED: { text: '댓글이 삭제되었습니다.', type: 'success' },
        SIGNUP_DONE: { text: '가입이 완료되었습니다. 이메일 인증 안내를 확인해 주세요.', type: 'success' },
        PASSWORD_CHANGED: { text: '비밀번호가 변경되었습니다. 다시 로그인해 주세요.', type: 'success' }
    },
    set: function (key) {
        if (!this.MESSAGES[key]) {
            return;
        }
        try {
            sessionStorage.setItem(this.STORAGE_KEY, key);
        } catch (e) {
            // 저장소를 쓸 수 없어도 이동은 정상적으로 진행된다.
        }
    },
    /**
     * 화면에 즉시 오류 요약을 띄운다(이동 없이). 사라지는 토스트 대신 지속되는 영역에 남겨
     * 사용자가 다시 시도하기 전까지 원인을 계속 읽을 수 있게 한다. role="alert"로 올려 즉시
     * 주의가 필요한 오류임을 알린다 — 일반 성공 메시지는 role="status"(polite)를 유지한다.
     */
    showError: function (text) {
        this.render(text, true);
    },
    /**
     * 사용자가 원인을 스스로 고친 뒤(예: 파일을 다시 선택)에도 이전 오류가 화면에 남아 있으면
     * 이미 해결된 문제처럼 보이지 않게 감춘다. 다음 제출에서 다시 실패하면 render()가 새로
     * 채운다.
     */
    hide: function () {
        $('#flash').attr('hidden', 'hidden');
    },
    consume: function () {
        var key = null;
        try {
            key = sessionStorage.getItem(this.STORAGE_KEY);
            if (key) {
                sessionStorage.removeItem(this.STORAGE_KEY);
            }
        } catch (e) {
            return;
        }

        var entry = key ? this.MESSAGES[key] : null;
        if (!entry) {
            return;
        }
        this.render(entry.text, entry.type === 'danger');
    },
    render: function (text, isError) {
        var $flash = $('#flash');
        if (!$flash.length) {
            return;
        }
        $('#flash-text').text(text);
        $flash.toggleClass('flash--danger', !!isError);
        $flash.attr('role', isError ? 'alert' : 'status');
        $flash.removeAttr('hidden');
    }
};

/**
 * 헤더 메뉴 토글. 992px 미만에서는 hidden 속성으로 열림·닫힘을 표현하고,
 * 그 이상에서는 CSS가 항상 펼쳐 보이므로 이 스크립트가 상태를 건드리지 않는다.
 */
var siteNav = {
    init: function () {
        var $toggle = $('#btn-nav-toggle');
        var $nav = $('#site-nav');
        if (!$toggle.length || !$nav.length) {
            return;
        }

        $toggle.on('click', function () {
            var expanded = $toggle.attr('aria-expanded') === 'true';
            if (expanded) {
                $nav.attr('hidden', 'hidden');
                $toggle.attr('aria-expanded', 'false');
            } else {
                $nav.removeAttr('hidden');
                $toggle.attr('aria-expanded', 'true');
            }
        });

        $(document).on('keydown', function (e) {
            if (e.key === 'Escape' && $toggle.attr('aria-expanded') === 'true') {
                $nav.attr('hidden', 'hidden');
                $toggle.attr('aria-expanded', 'false');
                $toggle.trigger('focus');
            }
        });
    }
};

/**
 * 게시글 읽기·편집 화면(post-update.html)의 읽기/편집 상태 전환과 저장.
 * <p>
 * 이미지 교체는 {@code postForm}과 같은 업로드→저장 2단계 패턴을 따른다. 편집 폼에는 세 가지
 * 이미지 상태가 있다: (1) 손대지 않음 — 저장 시 기존 picture를 그대로 다시 보낸다, (2) 새 파일을
 * 선택함 — 업로드 후 그 URL로 교체한다, (3) "이미지 삭제"를 눌러 명시적으로 지움 — 저장 시
 * picture를 null로 보낸다. {@code removedExisting}이 (3)을 표시하고, 파일을 다시 선택하면
 * 그 표시는 무시된다(파일이 있으면 항상 우선).
 */
var postEdit = {
    MAX_SIZE: 5 * 1024 * 1024,
    ALLOWED_EXT: ['jpg', 'jpeg', 'png', 'gif', 'webp'],
    previewUrl: null,
    uploadedUrl: null,
    uploadedForFile: null,
    removedExisting: false,

    init: function () {
        var _this = this;
        $('#btn-edit').on('click', function () {
            _this.enterEdit();
        });

        $('#btn-cancel-edit').on('click', function () {
            _this.cancelEdit();
        });

        $('#post-edit').on('submit', function (e) {
            e.preventDefault();
            _this.submit();
        });

        $('#edit-picture').on('change', function () {
            _this.onFileChange();
        });

        $('#btn-edit-picture-clear').on('click', function () {
            _this.clearFile();
        });

        $('#btn-edit-picture-remove').on('click', function () {
            _this.removeExisting();
        });

        $(window).on('pagehide', function () {
            _this.revokePreview();
        });
    },
    enterEdit: function () {
        $('#post-view').attr('hidden', 'hidden');
        $('#post-edit').removeAttr('hidden');
        $('#title').trigger('focus');
    },
    cancelEdit: function () {
        var titleChanged = $('#title').val() !== $('#original-title').val();
        var contentChanged = $('#content').val() !== $('#original-content').val();
        var pictureChanged = this.removedExisting || !!($('#edit-picture').length && $('#edit-picture')[0].files[0]);
        if ((titleChanged || contentChanged || pictureChanged) && !window.confirm('변경한 내용을 버리시겠습니까?')) {
            return;
        }
        $('#title').val($('#original-title').val());
        $('#content').val($('#original-content').val());
        this.resetPictureState();
        $('#post-edit').attr('hidden', 'hidden');
        $('#post-view').removeAttr('hidden');
        $('#btn-edit').trigger('focus');
    },
    resetPictureState: function () {
        $('#edit-picture').val('');
        this.uploadedUrl = null;
        this.uploadedForFile = null;
        this.removedExisting = false;
        this.revokePreview();
        $('#edit-picture-preview').attr('hidden', 'hidden');
        if ($('#original-picture').val()) {
            $('#edit-picture-current').removeAttr('hidden');
        }
    },
    onFileChange: function () {
        var file = $('#edit-picture').length ? $('#edit-picture')[0].files[0] : null;
        this.uploadedUrl = null;
        this.uploadedForFile = null;
        this.revokePreview();

        if (!file) {
            $('#edit-picture-preview').attr('hidden', 'hidden');
            return;
        }

        // 클라이언트 사전 검사는 불필요한 왕복을 줄이기 위한 것일 뿐, 서버(PostImageService)의
        // 확장자·용량 검증을 대체하지 않는다 — 실제 판정은 항상 서버가 내린다.
        var extension = (file.name.split('.').pop() || '').toLowerCase();
        if (this.ALLOWED_EXT.indexOf(extension) === -1) {
            flash.showError('JPG, JPEG, PNG, GIF, WEBP 형식만 첨부할 수 있습니다.');
            this.clearFile();
            return;
        }
        if (file.size > this.MAX_SIZE) {
            flash.showError('파일 크기는 5MB를 초과할 수 없습니다.');
            this.clearFile();
            return;
        }

        flash.hide();
        this.removedExisting = false;
        $('#edit-picture-current').attr('hidden', 'hidden');
        this.previewUrl = URL.createObjectURL(file);
        $('#edit-picture-preview-image').attr('src', this.previewUrl);
        $('#edit-picture-preview-name').text(file.name + ' · ' + formatFileSize(file.size));
        $('#edit-picture-preview').removeAttr('hidden');
    },
    clearFile: function () {
        $('#edit-picture').val('');
        this.uploadedUrl = null;
        this.uploadedForFile = null;
        this.revokePreview();
        $('#edit-picture-preview').attr('hidden', 'hidden');
        if (!this.removedExisting && $('#original-picture').val()) {
            $('#edit-picture-current').removeAttr('hidden');
        }
    },
    removeExisting: function () {
        this.removedExisting = true;
        $('#edit-picture-current').attr('hidden', 'hidden');
    },
    revokePreview: function () {
        if (this.previewUrl) {
            URL.revokeObjectURL(this.previewUrl);
            this.previewUrl = null;
        }
    },
    setProgress: function (text) {
        var $progress = $('#post-update-progress');
        if (!$progress.length) {
            return;
        }
        if (!text) {
            $progress.attr('hidden', 'hidden').text('');
            return;
        }
        $progress.text(text).removeAttr('hidden');
    },
    setBusy: function (busy) {
        $('#btn-update').prop('disabled', busy).attr('aria-busy', busy ? 'true' : 'false');
    },
    submit: function () {
        var _this = this;
        var file = $('#edit-picture').length ? $('#edit-picture')[0].files[0] : null;

        this.setBusy(true);

        if (file && this.uploadedUrl && this.uploadedForFile === file) {
            this.doUpdate(this.uploadedUrl);
            return;
        }

        if (file) {
            this.setProgress('이미지 업로드 중…');
            var formData = new FormData();
            formData.append('file', file);

            $.ajax({
                type: 'POST',
                url: '/api/v1/posts/images',
                data: formData,
                processData: false,
                contentType: false,
                dataType: 'json'
            }).done(function (response) {
                _this.uploadedUrl = response.url;
                _this.uploadedForFile = file;
                _this.doUpdate(response.url);
            }).fail(function (error) {
                _this.setProgress(null);
                _this.setBusy(false);
                flash.showError('이미지 업로드에 실패했습니다. ' + extractErrorMessage(error));
            });
        } else if (this.removedExisting) {
            this.doUpdate(null);
        } else {
            this.doUpdate($('#original-picture').val() || null);
        }
    },
    doUpdate: function (pictureUrl) {
        var _this = this;
        var data = {
            title: $('#title').val(),
            content: $('#content').val(),
            picture: pictureUrl,
            category: $('#edit-category').val()
        };

        var id = $('#id').val();
        this.setProgress('게시글 저장 중…');

        $.ajax({
            type: 'PUT',
            url: '/api/v1/posts/' + id,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            _this.revokePreview();
            flash.set('POST_UPDATED');
            window.location.href = '/';
        }).fail(function (error) {
            _this.setProgress(null);
            _this.setBusy(false);
            var retryHint = pictureUrl
                ? ' 이미지는 이미 업로드되어 있으니 다시 "저장"을 누르면 같은 이미지로 재시도합니다.'
                : '';
            showToast(extractErrorMessage(error) + retryHint, 'danger');
        });
    }
};

/**
 * 게시글 등록 화면(post-save.html): 제목·내용·선택한 이미지를 미리보기로 보여주고,
 * 업로드→저장 2단계 요청을 진행 상태와 함께 처리한다.
 * <p>
 * uploadedUrl/uploadedForFile은 "이미 업로드에 성공한 파일"을 기억해, 업로드는 성공했지만
 * 글 저장만 실패했을 때 재시도가 같은 파일을 다시 올리지 않게 한다. 파일을 바꾸거나
 * 선택을 해제하면 즉시 초기화한다.
 */
var postForm = {
    MAX_SIZE: 5 * 1024 * 1024,
    ALLOWED_EXT: ['jpg', 'jpeg', 'png', 'gif', 'webp'],
    previewUrl: null,
    uploadedUrl: null,
    uploadedForFile: null,

    init: function () {
        var _this = this;
        var $form = $('#post-save-form');
        if (!$form.length) {
            return;
        }

        $form.on('submit', function (e) {
            e.preventDefault();
            _this.submit();
        });

        $('#picture').on('change', function () {
            _this.onFileChange();
        });

        $('#btn-picture-clear').on('click', function () {
            _this.clearFile();
        });

        // 탭을 벗어나거나 닫을 때도 object URL을 해제한다(교체·해제 시점은 각 핸들러가 처리).
        $(window).on('pagehide', function () {
            _this.revokePreview();
        });
    },

    onFileChange: function () {
        var file = $('#picture').length ? $('#picture')[0].files[0] : null;
        this.uploadedUrl = null;
        this.uploadedForFile = null;
        this.revokePreview();

        if (!file) {
            $('#picture-preview').attr('hidden', 'hidden');
            return;
        }

        // 클라이언트 사전 검사는 불필요한 왕복을 줄이기 위한 것일 뿐, 서버(PostImageService)의
        // 확장자·용량 검증을 대체하지 않는다 — 실제 판정은 항상 서버가 내린다.
        var extension = (file.name.split('.').pop() || '').toLowerCase();
        if (this.ALLOWED_EXT.indexOf(extension) === -1) {
            flash.showError('JPG, JPEG, PNG, GIF, WEBP 형식만 첨부할 수 있습니다.');
            this.clearFile();
            return;
        }
        if (file.size > this.MAX_SIZE) {
            flash.showError('파일 크기는 5MB를 초과할 수 없습니다.');
            this.clearFile();
            return;
        }

        flash.hide();
        this.previewUrl = URL.createObjectURL(file);
        $('#picture-preview-image').attr('src', this.previewUrl);
        $('#picture-preview-name').text(file.name + ' · ' + formatFileSize(file.size));
        $('#picture-preview').removeAttr('hidden');
    },

    clearFile: function () {
        $('#picture').val('');
        this.uploadedUrl = null;
        this.uploadedForFile = null;
        this.revokePreview();
        $('#picture-preview').attr('hidden', 'hidden');
    },

    revokePreview: function () {
        if (this.previewUrl) {
            URL.revokeObjectURL(this.previewUrl);
            this.previewUrl = null;
        }
    },

    setProgress: function (text) {
        var $progress = $('#post-save-progress');
        if (!$progress.length) {
            return;
        }
        if (!text) {
            $progress.attr('hidden', 'hidden').text('');
            return;
        }
        $progress.text(text).removeAttr('hidden');
    },

    setBusy: function (busy) {
        $('#btn-save').prop('disabled', busy).attr('aria-busy', busy ? 'true' : 'false');
    },

    submit: function () {
        var _this = this;
        var title = $('#title').val();
        var content = $('#content').val();

        if (!title || !content) {
            flash.showError('제목과 내용을 모두 입력해 주세요.');
            return;
        }

        var file = $('#picture').length ? $('#picture')[0].files[0] : null;

        this.setBusy(true);

        if (file && this.uploadedUrl && this.uploadedForFile === file) {
            // 같은 파일로 이전 시도의 업로드까지는 성공했다 — 재업로드 없이 저장만 재시도한다.
            this.doSave(this.uploadedUrl);
            return;
        }

        if (file) {
            this.setProgress('이미지 업로드 중…');
            var formData = new FormData();
            formData.append('file', file);

            $.ajax({
                type: 'POST',
                url: '/api/v1/posts/images',
                data: formData,
                processData: false,
                contentType: false,
                dataType: 'json'
            }).done(function (response) {
                _this.uploadedUrl = response.url;
                _this.uploadedForFile = file;
                _this.doSave(response.url);
            }).fail(function (error) {
                _this.setProgress(null);
                _this.setBusy(false);
                flash.showError('이미지 업로드에 실패했습니다. ' + extractErrorMessage(error));
            });
        } else {
            this.doSave(null);
        }
    },

    doSave: function (pictureUrl) {
        var _this = this;
        var data = {
            title: $('#title').val(),
            content: $('#content').val(),
            picture: pictureUrl,
            category: $('#category').val()
        };

        this.setProgress('게시글 등록 중…');

        $.ajax({
            type: 'POST',
            url: '/api/v1/posts',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            _this.revokePreview();
            flash.set('POST_SAVED');
            window.location.href = '/';
        }).fail(function (error) {
            _this.setProgress(null);
            _this.setBusy(false);
            var retryHint = pictureUrl
                ? ' 이미지는 이미 업로드되어 있으니 다시 "등록"을 누르면 같은 이미지로 재시도합니다.'
                : '';
            flash.showError('게시글 등록에 실패했습니다. ' + extractErrorMessage(error) + retryHint);
        });
    }
};

/**
 * 게시글·댓글이 공유하는 삭제 확인 모달. 호출한 버튼(대상)을 기억해두었다가
 * 확인을 누르면 그 대상에 맞는 삭제를 수행하고, 모달을 닫으면 호출 버튼으로
 * 포커스를 되돌린다.
 */
var deleteConfirm = {
    pending: null, // { kind: 'post' | 'comment', id, $trigger }
    init: function () {
        var _this = this;

        $(document).on('click', '[data-target-kind="post"]', function () {
            _this.open('post', $('#id').val(), $(this));
        });

        $(document).on('click', '[data-target-kind="comment"]', function () {
            var $item = $(this).closest('.comment-list__item');
            _this.open('comment', $item.attr('data-comment-id'), $(this));
        });

        $('#confirmDeleteModal').on('hidden.bs.modal', function () {
            if (_this.pending && _this.pending.$trigger) {
                _this.pending.$trigger.trigger('focus');
            }
            _this.pending = null;
        });

        $('#btn-confirm-delete').on('click', function () {
            _this.confirm();
        });
    },
    open: function (kind, id, $trigger) {
        this.pending = { kind: kind, id: id, $trigger: $trigger };
        var message = kind === 'post' ? '이 게시글을 삭제하시겠습니까?' : '이 댓글을 삭제하시겠습니까?';
        $('#confirmDeleteModalLabel').text(kind === 'post' ? '게시글 삭제' : '댓글 삭제');
        $('#confirmDeleteMessage').text(message);
        $('#confirmDeleteModal').modal('show');
    },
    confirm: function () {
        if (!this.pending) {
            return;
        }
        var pending = this.pending;
        var $confirmBtn = $('#btn-confirm-delete');
        if ($confirmBtn.prop('disabled')) {
            return;
        }
        $confirmBtn.prop('disabled', true);

        var url = pending.kind === 'post'
            ? '/api/v1/posts/' + pending.id
            : '/api/v1/comments/' + pending.id;
        var flashKey = pending.kind === 'post' ? 'POST_DELETED' : 'COMMENT_DELETED';

        $.ajax({
            type: 'DELETE',
            url: url,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8'
        }).done(function () {
            $('#confirmDeleteModal').modal('hide');
            flash.set(flashKey);
            if (pending.kind === 'post') {
                window.location.href = '/';
            } else {
                window.location.reload();
            }
        }).fail(function (error) {
            $('#confirmDeleteModal').modal('hide');
            showToast(extractErrorMessage(error), 'danger');
        }).always(function () {
            $confirmBtn.prop('disabled', false);
        });
    }
};

/**
 * 게시글 상세 화면의 추천(좋아요) 토글 버튼. 서버가 반환하는 최종 상태(liked/likeCount)로만
 * 화면을 갱신한다 — 클릭 즉시 낙관적으로 뒤집지 않는 이유는, 이미 다른 탭에서 취소했거나
 * 요청이 실패했을 때 버튼 상태가 실제와 어긋나는 것을 피하기 위해서다.
 */
var postLike = {
    init: function () {
        var _this = this;
        $('#btn-like').on('click', function () {
            _this.toggle();
        });
    },
    toggle: function () {
        var $btn = $('#btn-like');
        if (!$btn.length || $btn.prop('disabled')) {
            return;
        }
        var id = $('#id').val();
        $btn.prop('disabled', true);

        $.ajax({
            type: 'PUT',
            url: '/api/v1/posts/' + id + '/like',
            dataType: 'json'
        }).done(function (response) {
            $('#like-count').text(response.likeCount);
            $btn.toggleClass('is-active', response.liked).attr('aria-pressed', response.liked ? 'true' : 'false');
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
        }).always(function () {
            $btn.prop('disabled', false);
        });
    }
};

var comment = {
    init: function () {
        var _this = this;
        $('#btn-comment-save').on('click', function () {
            _this.save();
        });

        $(document).on('click', '.btn-comment-edit', function () {
            var $item = $(this).closest('.comment-list__item');
            $item.find('.comment-view').attr('hidden', 'hidden');
            var $form = $item.find('.comment-edit-form');
            $form.removeAttr('hidden');
            $form.find('textarea').trigger('focus');
        });

        $(document).on('click', '.btn-comment-cancel', function () {
            var $item = $(this).closest('.comment-list__item');
            $item.find('.comment-edit-form').attr('hidden', 'hidden');
            $item.find('.comment-view').removeAttr('hidden');
        });

        $(document).on('submit', '.comment-edit-form', function (e) {
            e.preventDefault();
            _this.update($(this));
        });
    },
    save: function () {
        var postId = $('#comment-post-id').val();
        var data = {
            content: $('#comment-content').val()
        };

        var $btn = $('#btn-comment-save');
        $btn.prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'POST',
            url: '/api/v1/posts/' + postId + '/comments',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            flash.set('COMMENT_SAVED');
            window.location.reload();
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
            $btn.prop('disabled', false).removeAttr('aria-busy');
        });
    },
    update: function ($form) {
        var $item = $form.closest('.comment-list__item');
        var id = $item.attr('data-comment-id');
        var data = {
            content: $form.find('textarea').val()
        };

        var $btn = $form.find('.btn-comment-save');
        $btn.prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'PUT',
            url: '/api/v1/comments/' + id,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            flash.set('COMMENT_UPDATED');
            window.location.reload();
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
            $btn.prop('disabled', false).removeAttr('aria-busy');
        });
    }
};

var signup = {
    init: function () {
        $('#btn-signup').on('click', function () {
            signup.save();
        });
    },
    clearFieldError: function () {
        $('#passwordConfirm').removeClass('is-invalid').removeAttr('aria-invalid');
        $('#passwordConfirm-error').text('');
    },
    showFieldError: function (message) {
        var $field = $('#passwordConfirm');
        $field.addClass('is-invalid').attr('aria-invalid', 'true');
        $('#passwordConfirm-error').text(message);
        $field.trigger('focus');
    },
    save: function () {
        this.clearFieldError();

        var password = $('#password').val();
        var passwordConfirm = $('#passwordConfirm').val();

        if (password !== passwordConfirm) {
            this.showFieldError('비밀번호가 일치하지 않습니다.');
            return;
        }

        var data = {
            name: $('#name').val(),
            email: $('#email').val(),
            password: password
        };

        var $btn = $('#btn-signup');
        $btn.prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'POST',
            url: '/api/v1/users',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            flash.set('SIGNUP_DONE');
            window.location.href = '/login';
        }).fail(function (error) {
            flash.showError(extractErrorMessage(error));
            $btn.prop('disabled', false).removeAttr('aria-busy');
        });
    }
};

var changePassword = {
    init: function () {
        $('#btn-change-password').on('click', function () {
            changePassword.save();
        });
    },
    save: function () {
        var data = {
            currentPassword: $('#currentPassword').val(),
            newPassword: $('#newPassword').val()
        };

        var $btn = $('#btn-change-password');
        $btn.prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'PUT',
            url: '/api/v1/users/me/password',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            flash.set('PASSWORD_CHANGED');
            $('#logout-form').trigger('submit');
        }).fail(function (error) {
            flash.showError(extractErrorMessage(error));
            $btn.prop('disabled', false).removeAttr('aria-busy');
        });
    }
};

var verifyEmail = {
    init: function () {
        $('#btn-resend-verification').on('click', function () {
            verifyEmail.resend();
        });
    },
    resend: function () {
        var $btn = $('#btn-resend-verification');
        $btn.prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'POST',
            url: '/api/v1/users/me/verify-email/resend'
        }).done(function () {
            showToast('인증 메일을 다시 보냈습니다. 메일함을 확인해 주세요.', 'success');
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
        }).always(function () {
            $btn.prop('disabled', false).removeAttr('aria-busy');
        });
    }
};

$(function () {
    var csrfToken = $('meta[name="_csrf"]').attr('content');
    var csrfHeader = $('meta[name="_csrf_header"]').attr('content');

    $(document).ajaxSend(function (e, xhr) {
        if (csrfHeader) {
            xhr.setRequestHeader(csrfHeader, csrfToken);
        }
    });

    $('#btn-logout').on('click', function () {
        $('#logout-form').trigger('submit');
    });

    flash.consume();
    siteNav.init();
    postEdit.init();
    postLike.init();
    postForm.init();
    deleteConfirm.init();
    comment.init();
    signup.init();
    changePassword.init();
    verifyEmail.init();
});
