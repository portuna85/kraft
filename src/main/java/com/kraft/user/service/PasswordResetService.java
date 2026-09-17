package com.kraft.user.service;

import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.PasswordResetToken;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailStore;
import com.kraft.user.mail.OutboxMailWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 비밀번호 찾기. 로그인해야 비밀번호를 바꿀 수 있는데 잊은 사람은 로그인할 수 없다 — 그 고리를
 * 메일로 끊는다.
 *
 * <h3>요청 단계가 아무것도 알려주지 않는 이유</h3>
 * {@link #request}는 <b>어떤 경우에도 똑같이 조용히 끝난다</b>. 가입하지 않은 주소든, 방금
 * 요청해서 제한에 걸린 주소든 호출한 쪽이 받는 결과는 같다. 응답이 갈리면 그것만으로
 * "이 주소가 가입되어 있다"를 확인하는 도구가 된다(계정 열거). 화면도 늘 같은 안내를 보여준다.
 *
 * <h3>토큰</h3>
 * 30분짜리 1회용이다. 이메일 인증(24시간)보다 훨씬 짧은 것은, 이 링크가 곧 계정 접근 권한이기
 * 때문이다. 재발급하면 옛 링크는 그 자리에서 무효가 되고, 한 번 쓴 토큰은 지운다.
 *
 * <h3>끝나면 세션을 모두 폐기한다</h3>
 * 비밀번호를 잊었다는 것은 이미 남이 쓰고 있을 수 있다는 뜻이다. 재설정은 비밀번호 변경과 같은
 * 규칙으로(UserService.changePassword) 그 계정의 모든 세션을 끊는다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /** 같은 계정으로 메일을 연달아 요청하는 것을 막는다. 인증 메일 재발송과 같은 간격이다. */
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final OutboxMailStore outboxMailStore;
    private final OutboxMailWorker outboxMailWorker;
    private final ExpiredTokenPurger expiredTokenPurger;

    /**
     * 재설정 링크를 보낸다. 가입하지 않은 주소나 제한에 걸린 요청은 <b>아무 일도 하지 않고</b>
     * 조용히 끝난다 — 호출한 쪽에서 구분할 수 없어야 한다.
     */
    @Transactional
    public void request(String email) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email)).orElse(null);
        if (user == null) {
            // 가입되지 않은 주소. 로그에도 존재 여부만 남기고 주소는 가린다.
            log.info("가입되지 않은 주소로 비밀번호 재설정을 요청했습니다. email={}", EmailMasker.mask(email));
            return;
        }
        // 쿨다운 검사와 재발급을 계정 단위로 직렬화한다(B07) — 그래야 두 동시 요청이 같은
        // "마지막 발송 시각"을 동시에 읽고 둘 다 쿨다운을 통과하는 경쟁이 없어진다.
        userRepository.findByIdForUpdate(user.getId());

        if (requestedTooRecently(user.getId())) {
            log.info("비밀번호 재설정 요청이 너무 잦아 보내지 않았습니다. userId={}", user.getId());
            return;
        }

        // 옛 링크는 이 시점에 무효가 된다. 메일함에 여러 개가 살아 있지 않게 한다.
        tokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        tokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .build());

        // 토큰과 대기열 행은 같은 트랜잭션에서 함께 커밋된다. 한쪽만 남으면 안 된다.
        outboxMailStore.enqueue(user, token, OutboxMailKind.PASSWORD_RESET);
        AfterCommit.run(outboxMailWorker::drainAsync);
    }

    /**
     * 링크의 토큰으로 새 비밀번호를 정한다. 여기서는 반대로 <b>실패 이유를 분명히</b> 알려준다 —
     * 이미 링크를 받은 사람에게 "만료됐는지 잘못된 링크인지"를 감추면 다시 시도할 길이 없다.
     * <p>
     * 소비(삭제)를 비밀번호 변경보다 먼저, 그리고 <b>조건부로</b> 한다(B07). 같은 토큰이 동시에
     * 두 번 들어오면 {@code deleteByIdAndToken}의 DB 행 잠금이 정확히 하나만 성공시킨다 —
     * 이긴 쪽만 비밀번호를 바꿔, 두 요청 모두 성공한 것처럼 보이는 경쟁을 막는다.
     */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재설정 링크입니다. 다시 요청해 주세요."));

        if (resetToken.isExpired()) {
            // 이 트랜잭션에서 지우면 바로 아래 예외와 함께 삭제까지 롤백된다(F08과 같은 덫).
            // 자기 트랜잭션에서 먼저 커밋하는 쪽에 맡긴다.
            expiredTokenPurger.purgePasswordResetToken(resetToken.getId());
            throw new IllegalArgumentException("재설정 링크가 만료되었습니다. 다시 요청해 주세요.");
        }

        int consumed = tokenRepository.deleteByIdAndToken(resetToken.getId(), token);
        if (consumed == 0) {
            throw new IllegalArgumentException("이미 사용되었거나 유효하지 않은 재설정 링크입니다. 다시 요청해 주세요.");
        }
        userService.resetPassword(resetToken.getUser().getId(), newPassword);
    }

    private boolean requestedTooRecently(Long userId) {
        return outboxMailStore.lastQueuedAt(userId, OutboxMailKind.PASSWORD_RESET)
                .map(last -> LocalDateTime.now().isBefore(last.plus(REQUEST_COOLDOWN)))
                .orElse(false);
    }
}
