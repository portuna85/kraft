var main = {
    init : function () {
        var _this = this;
        $('#btn-save').on('click', function () {
            _this.save();
        });

        $('#btn-update').on('click', function () {
            _this.update();
        });

        $('#btn-delete').on('click', function () {
            _this.delete();
        });
    },
    save : function () {
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
    uploadImage : function (file, callback) {
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
            alert(JSON.stringify(error));
        });
    },
    doSave : function (pictureUrl) {
        var data = {
            title: $('#title').val(),
            content: $('#content').val(),
            picture: pictureUrl
        };

        $.ajax({
            type: 'POST',
            url: '/api/v1/posts',
            dataType: 'json',
            contentType:'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function() {
            alert('글이 등록되었습니다.');
            window.location.href = '/';
        }).fail(function (error) {
            alert(JSON.stringify(error));
        });
    },
    update : function () {
        var data = {
            title: $('#title').val(),
            content: $('#content').val()
        };

        var id = $('#id').val();

        $.ajax({
            type: 'PUT',
            url: '/api/v1/posts/'+id,
            dataType: 'json',
            contentType:'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function() {
            alert('글이 수정되었습니다.');
            window.location.href = '/';
        }).fail(function (error) {
            alert(JSON.stringify(error));
        });
    },
    delete : function () {
        var id = $('#id').val();

        $.ajax({
            type: 'DELETE',
            url: '/api/v1/posts/'+id,
            dataType: 'json',
            contentType:'application/json; charset=utf-8'
        }).done(function() {
            alert('글이 삭제되었습니다.');
            window.location.href = '/';
        }).fail(function (error) {
            alert(JSON.stringify(error));
        });
    }

};

var comment = {
    init : function () {
        var _this = this;
        $('#btn-comment-save').on('click', function () {
            _this.save();
        });

        $(document).on('click', '.btn-comment-delete', function () {
            _this.remove($(this).data('id'));
        });
    },
    save : function () {
        var postId = $('#comment-post-id').val();
        var data = {
            content: $('#comment-content').val()
        };

        $.ajax({
            type: 'POST',
            url: '/api/v1/posts/' + postId + '/comments',
            dataType: 'json',
            contentType:'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function() {
            alert('댓글이 등록되었습니다.');
            window.location.reload();
        }).fail(function (error) {
            alert(JSON.stringify(error));
        });
    },
    remove : function (id) {
        if (!confirm('댓글을 삭제하시겠습니까?')) {
            return;
        }

        $.ajax({
            type: 'DELETE',
            url: '/api/v1/comments/' + id,
            dataType: 'json',
            contentType:'application/json; charset=utf-8'
        }).done(function() {
            alert('댓글이 삭제되었습니다.');
            window.location.reload();
        }).fail(function (error) {
            alert(JSON.stringify(error));
        });
    }
};

var signup = {
    init : function () {
        $('#btn-signup').on('click', function () {
            signup.save();
        });
    },
    save : function () {
        var data = {
            name: $('#name').val(),
            email: $('#email').val(),
            password: $('#password').val()
        };

        $.ajax({
            type: 'POST',
            url: '/api/v1/users',
            dataType: 'json',
            contentType:'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function() {
            alert('회원가입이 완료되었습니다. 로그인해 주세요.');
            window.location.href = '/login';
        }).fail(function (error) {
            alert(JSON.stringify(error));
        });
    }
};

var changePassword = {
    init : function () {
        $('#btn-change-password').on('click', function () {
            changePassword.save();
        });
    },
    save : function () {
        var data = {
            currentPassword: $('#currentPassword').val(),
            newPassword: $('#newPassword').val()
        };

        $.ajax({
            type: 'PUT',
            url: '/api/v1/users/me/password',
            contentType:'application/json; charset=utf-8',
            data: JSON.stringify(data)
        }).done(function() {
            alert('비밀번호가 변경되었습니다. 다시 로그인해 주세요.');
            window.location.href = '/logout';
        }).fail(function (error) {
            alert(JSON.stringify(error));
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

    main.init();
    comment.init();
    signup.init();
    changePassword.init();
});
