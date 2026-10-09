package com.kraft.config.security;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailPolicy;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 로그인 연속 실패에 대한 계정 단위 누적 방어({@code AuthRateLimitFilter}의 분당 제한은 IP를 바꾸면 우회된다).
 * {@code DaoAuthenticationProvider}의 인증 이벤트를 듣는다. 없는 계정은 잠글 대상이 없어 조용히 지나가고, 이미 잠긴 계정은
 * {@link UserDetailsServiceImpl}이 {@code accountNonLocked=false}로 돌려 비밀번호를 보기 전에 거절되므로 잠긴 동안은 실패가
 * 더 쌓이지 않는다.
 */
@RequiredArgsConstructor
@Service
public class LoginLockoutService {

    /** 이만큼 연속 실패하면 잠그기 시작한다. */
    static final int LOCK_THRESHOLD = 5;

    /** 잠금 시간의 상한(분). 재시도 간격이 끝없이 늘어나지 않게 한다. */
    static final long MAX_LOCK_MINUTES = 60;

    private final UserRepository userRepository;
    private final OutboxMailStore outboxMailStore;

    @EventListener
    @Transactional
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = EmailPolicy.normalize(event.getAuthentication().getName());
        if (email == null) {
            return;
        }
        userRepository.findByEmailHmac(EmailHasher.hmacHex(email))
                .filter(user -> !user.isWithdrawn())
                .ifPresent(this::recordFailure);
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof KraftUserDetails principal) {
            userRepository.resetFailedLogins(principal.getUserId());
        }
    }

    /** 카운터·잠금을 원자적 UPDATE로 갱신한다 — 같은 계정에 동시 실패가 몰려도 {@code @Version} 충돌이나 증가분 유실이 없다. */
    private void recordFailure(User user) {
        Long id = user.getId();
        userRepository.incrementFailedLogins(id);
        int attempts = userRepository.findFailedLoginAttempts(id);
        if (attempts < LOCK_THRESHOLD) {
            return;
        }

        // 5회째부터 5→1분, 6→2분, 7→4분 ... 식으로 두 배씩 늘리다 상한에서 멈춘다.
        long minutes = Math.min(MAX_LOCK_MINUTES, 1L << Math.min(attempts - LOCK_THRESHOLD, 10));
        userRepository.extendLock(id, LocalDateTime.now().plus(Duration.ofMinutes(minutes)));

        // 임계를 처음 넘는 순간에만 알린다(쿨다운·예산은 enqueueNotice가 지킨다).
        if (attempts == LOCK_THRESHOLD) {
            outboxMailStore.enqueueNotice(user, OutboxMailKind.LOGIN_ATTEMPTS_WARNING);
        }
    }
}
