package com.kraft.shared.security;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

/**
 * "글을 쓸 수 있는 사람인가"를 Post/Comment 등 여러 도메인의 작성 경로에서 공유하기 위한 순수
 * 정적 유틸리티. {@link OwnershipPolicy}와 같은 스타일로, 실패 시 {@link AccessDeniedException}을
 * 던져 {@code ApiExceptionHandler}가 403으로 변환하도록 한다.
 * <p>
 * 이메일 인증을 마쳤는가({@link Role#GUEST}가 아닌가)만 본다.
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
        return Optional.empty();
    }
}
