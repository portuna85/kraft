// /static/js/app/index.js
(function () {
    const $doc = $(document);

    // CSRF 세팅
    const csrfToken = $('meta[name="_csrf"]').attr('content');
    const csrfHeader = $('meta[name="_csrf_header"]').attr('content');
    $.ajaxSetup({
        beforeSend: function (xhr) {
            if (csrfToken && csrfHeader) xhr.setRequestHeader(csrfHeader, csrfToken);
        }
    });

    // 공통: 알림 + 이동
    function goHome(msg) {
        if (msg) alert(msg);
        window.location.assign('/');
    }

    // 간단 검증
    function required(val) { return val != null && String(val).trim().length > 0; }

    const main = {
        init: function () {
            $doc.on('click', '#btn-save', main.save);
            $doc.on('click', '#btn-update', main.update);
            $doc.on('click', '#btn-delete', main.remove);
        },

        save: function () {
            const $btn = $('#btn-save').prop('disabled', true);
            const data = {
                title: $('#title').val(),
                author: $('#author').val(),
                content: $('#content').val()
            };

            if (!required(data.title) || !required(data.author) || !required(data.content)) {
                alert('제목/작성자/내용을 모두 입력하세요.');
                return void $btn.prop('disabled', false);
            }

            $.ajax({
                method: 'POST',
                url: '/api/v1/posts',
                dataType: 'json',
                contentType: 'application/json; charset=utf-8',
                data: JSON.stringify(data)
            })
                .done(() => goHome('글이 등록되었습니다.'))
                .fail(err => alert(JSON.stringify(err)))
                .always(() => $btn.prop('disabled', false));
        },

        update: function () {
            const $btn = $('#btn-update').prop('disabled', true);
            const id = $('#id').val();
            const data = {
                title: $('#title').val(),
                content: $('#content').val()
            };

            if (!required(id)) {
                alert('잘못된 요청입니다: id 없음');
                return void $btn.prop('disabled', false);
            }
            if (!required(data.title) || !required(data.content)) {
                alert('제목/내용을 입력하세요.');
                return void $btn.prop('disabled', false);
            }

            $.ajax({
                method: 'PUT',
                url: '/api/v1/posts/' + encodeURIComponent(id),
                dataType: 'json',
                contentType: 'application/json; charset=utf-8',
                data: JSON.stringify(data)
            })
                .done(() => goHome('글이 수정되었습니다.'))
                .fail(err => alert(JSON.stringify(err)))
                .always(() => $btn.prop('disabled', false));
        },

        remove: function () {
            const $btn = $('#btn-delete').prop('disabled', true);
            const id = $('#id').val();
            if (!required(id)) {
                alert('잘못된 요청입니다: id 없음');
                return void $btn.prop('disabled', false);
            }
            if (!confirm('정말 삭제하시겠습니까?')) {
                return void $btn.prop('disabled', false);
            }

            $.ajax({
                method: 'DELETE',
                url: '/api/v1/posts/' + encodeURIComponent(id)
            })
                .done(() => goHome('글이 삭제되었습니다.'))
                .fail(err => alert(JSON.stringify(err)))
                .always(() => $btn.prop('disabled', false));
        }
    };

    main.init();
})();
