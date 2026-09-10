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

function extractErrorMessage(error) {
    if (error && error.responseJSON && error.responseJSON.detail) {
        return error.responseJSON.detail;
    }
    return '오류가 발생했습니다.';
}

/**
 * 이동 후 목적 화면에서 한 번만 표시하는 결과 메시지.
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
        COMMENT_DELETED: { text: '댓글이 삭제되었습니다.', type: 'success' }
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

        var $flash = $('#flash');
        if (!$flash.length) {
            return;
        }
        $('#flash-text').text(entry.text);
        $flash.toggleClass('flash--danger', entry.type === 'danger');
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

var main = {
    init: function () {
        var _this = this;
        $('#btn-save').on('click', function () {
            _this.save();
        });

        $('#btn-edit').on('click', function () {
            _this.enterEdit();
        });

        $('#btn-cancel-edit').on('click', function () {
            _this.cancelEdit();
        });

        $('#post-edit').on('submit', function (e) {
            e.preventDefault();
            _this.update();
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
        if ((titleChanged || contentChanged) && !window.confirm('변경한 내용을 버리시겠습니까?')) {
            return;
        }
        $('#title').val($('#original-title').val());
        $('#content').val($('#original-content').val());
        $('#post-edit').attr('hidden', 'hidden');
        $('#post-view').removeAttr('hidden');
        $('#btn-edit').trigger('focus');
    },
    save: function () {
        var _this = this;
        var file = $('#picture').length ? $('#picture')[0].files[0] : null;

        if (file) {
            _this.uploadImage(file, function (url) {
                _this.doSave(url);
            });
        } else {
            _this.doSave(null);
        }
    },
    uploadImage: function (file, callback) {
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
            callback(response.url);
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
        });
    },
    doSave: function (pictureUrl) {
        var data = {
            title: $('#title').val(),
            content: $('#content').val(),
            picture: pictureUrl
        };

        $('#btn-save').prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'POST',
            url: '/api/v1/posts',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            flash.set('POST_SAVED');
            window.location.href = '/';
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
        }).always(function () {
            $('#btn-save').prop('disabled', false).removeAttr('aria-busy');
        });
    },
    update: function () {
        var data = {
            title: $('#title').val(),
            content: $('#content').val()
        };

        var id = $('#id').val();

        $('#btn-update').prop('disabled', true).attr('aria-busy', 'true');

        $.ajax({
            type: 'PUT',
            url: '/api/v1/posts/' + id,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            flash.set('POST_UPDATED');
            window.location.href = '/';
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
            $('#btn-update').prop('disabled', false).removeAttr('aria-busy');
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
    save: function () {
        var password = $('#password').val();
        var passwordConfirm = $('#passwordConfirm').val();

        if (password !== passwordConfirm) {
            showToast('비밀번호가 일치하지 않습니다.', 'danger');
            return;
        }

        var data = {
            name: $('#name').val(),
            email: $('#email').val(),
            password: password
        };

        $.ajax({
            type: 'POST',
            url: '/api/v1/users',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            showToast('회원가입이 완료되었습니다. 로그인해 주세요.', 'success');
            window.location.href = '/login';
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
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

        $.ajax({
            type: 'PUT',
            url: '/api/v1/users/me/password',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function () {
            showToast('비밀번호가 변경되었습니다. 다시 로그인해 주세요.', 'success');
            $('#logout-form').trigger('submit');
        }).fail(function (error) {
            showToast(extractErrorMessage(error), 'danger');
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
    main.init();
    deleteConfirm.init();
    comment.init();
    signup.init();
    changePassword.init();
});
