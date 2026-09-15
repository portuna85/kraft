package com.kraft.shared.security;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * "글을 쓸 수 있는 사람인가"를 Post/Comment 등 여러 도메인의 작성 경로에서 공유하기 위한 순수
 * 정적 유틸리티. {@link OwnershipPolicy}와 같은 스타일로, 실패 시 {@link AccessDeniedException}을
 * 던져 {@code ApiExceptionHandler}가 403으로 변환하도록 한다.
 * <p>
 * 두 가지를 본다. 이메일 인증을 마쳤는가({@link Role#GUEST}가 아닌가), 그리고 지금 정지 중이
 * 아닌가. 정지는 읽기와 로그인을 막지 않는다 — 정지된 사람도 자기 상태와 사유를 볼 수 있어야
 * 하고, 그러려면 들어올 수는 있어야 한다.
 */
public final class WriteAccessPolicy {

    private WriteAccessPolicy() {
    }

    public static void requireVerified(User user) {
        blockReason(user).ifPresent(reason -> {
            throw new AccessDeniedException(reason + " id=" + user.getId());
        });
    }

    /**
     * 지금 글을 쓸 수 없는 이유. 쓸 수 있으면 빈 값이다.
     * <p>
     * 화면이 "폼을 보여줄지"를 정할 때도 이 메서드를 쓴다. 서버가 거절할 것을 화면이 같은
     * 규칙으로 미리 판단해야, 다 쓰고 나서야 이유를 알게 되는 흐름이 생기지 않는다.
     */
    public static Optional<String> blockReason(User user) {
        if (user.getRole() == Role.GUEST) {
            return Optional.of("이메일 인증을 완료해야 글을 쓸 수 있습니다.");
        }
        if (user.isSuspended()) {
            return Optional.of(suspensionMessage(user));
        }
        return Optional.empty();
    }

    /** 정지 안내 문구. 화면과 API가 같은 문장을 쓰도록 여기 한 곳에서 만든다. */
    public static String suspensionMessage(User user) {
        long hoursLeft = Math.max(1, Duration.between(LocalDateTime.now(), user.getSuspendedUntil()).toHours());
        String reason = user.getSuspensionReason() == null || user.getSuspensionReason().isBlank()
                ? ""
                : " 사유: " + user.getSuspensionReason();
        return "이용이 제한된 계정입니다. 약 %d시간 뒤에 다시 쓸 수 있습니다.%s".formatted(hoursLeft, reason);
    }
}
