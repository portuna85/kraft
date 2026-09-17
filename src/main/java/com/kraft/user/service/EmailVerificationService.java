package com.kraft.user.service;

import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.EmailVerificationToken;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.Role;
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
 * 회원가입 후 "GUEST → USER" 승격을 위한 이메일 인증 토큰 발급·검증을 담당한다.
 * <p>
 * 메일은 여기서 보내지 않는다. 대기열({@link OutboxMailStore})에 넣기만 하고 실제 발송은
 * {@link OutboxMailWorker}가 트랜잭션 밖에서 한다 — 그 이유는 {@link #sendVerificationEmail} 참고.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    /** 재발송 요청 사이에 두는 최소 간격. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final OutboxMailStore outboxMailStore;
    private final OutboxMailWorker outboxMailWorker;
    private final ExpiredTokenPurger expiredTokenPurger;

    /**
     * 인증 토큰을 만들고 메일을 <b>대기열에 넣는다.</b> 실제 발송은 이 트랜잭션이 커밋된 뒤
     * 별도 흐름에서 일어난다.
     * <p>
     * 예전에는 여기서 SMTP를 그대로 호출했다. 연결·읽기·쓰기 타임아웃이 각각 5초라 메일 서버가
     * 굼뜨면 <b>DB 커넥션 하나를 최대 15초 붙잡은 채</b> 기다렸고, 동시에 몇 명만 가입해도
     * 커넥션 풀이 말랐다(개선 보고서 "메일 안정성").
     * <p>
     * 발송을 단순히 {@code afterCommit}으로 미루는 것으로는 부족하다 — Spring은 그 콜백을
     * 커넥션을 반납하는 {@code cleanupAfterCompletion}<b>보다 먼저</b> 실행하므로 커넥션은 여전히
     * 잡혀 있다. 그래서 발송을 {@code @Async}로 다른 스레드에 넘겨 완전히 떼어 놓는다.
     */
    @Transactional
    public void sendVerificationEmail(String email) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .build());

        // 토큰과 대기열 행은 같은 트랜잭션에서 함께 커밋된다. 한쪽만 남는 일이 없어야 한다.
        outboxMailStore.enqueue(user, token, OutboxMailKind.VERIFY_EMAIL);

        // 커밋이 끝난 뒤 다른 스레드에서 보낸다. 사용자가 주기 작업을 기다리지 않아도 된다.
        AfterCommit.run(outboxMailWorker::drainAsync);
    }

    /**
     * 회원가입 직후 호출하는 안전 버전. 예외를 흡수해 회원가입 응답 자체가 실패하지 않게 한다 —
     * 이미 생성된 회원 정보는 그대로 유효하며, 인증 메일이 오지 않았다면 {@link #resend}로 다시
     * 요청할 수 있다.
     * <p>
     * 이 메서드가 막아 주는 것이 이제 SMTP 오류는 아니다. 발송은 대기열에 들어간 뒤 별도 흐름에서
     * 일어나므로 여기까지 올라오지 않는다. 남은 것은 토큰·대기열 저장이 실패하는 경우다.
     * <p>
     * {@code @Transactional}을 명시하는 이유는 그대로다. 이 메서드가 내부에서
     * {@code sendVerificationEmail(email)}을 호출하는 것은 self-invocation이라 Spring 프록시를
     * 거치지 않으므로, 그 메서드의 {@code @Transactional}(쓰기 가능)이 무시되고 클래스 레벨의
     * {@code readOnly = true}가 적용된다. H2는 읽기 전용 트랜잭션에서도 INSERT를 관대하게
     * 허용해 로컬에서는 드러나지 않았지만 MariaDB는 엄격히 거부한다(Docker 검증 중 실제로 재현).
     */
    @Transactional
    public void sendVerificationEmailSafely(String email) {
        try {
            sendVerificationEmail(email);
        } catch (Exception e) {
            log.warn("인증 메일을 대기열에 넣지 못했습니다. email={}", EmailMasker.mask(email), e);
        }
    }

    /**
     * 만료된 토큰은 {@link ExpiredTokenPurger}가 <b>별도 트랜잭션에서</b> 지운다. 여기서 바로
     * {@code tokenRepository.delete()}를 부르면, 이어지는 예외가 이 쓰기 트랜잭션을 롤백시키면서
     * 삭제까지 되돌려 만료 토큰이 그대로 남았다(개선 보고서 F08).
     * <p>
     * 소비(삭제)를 승격보다 먼저, 그리고 <b>조건부로</b> 한다(B07). 같은 토큰이 동시에 두 번
     * 들어오면 {@code deleteByIdAndToken}의 DB 행 잠금이 정확히 하나만 성공시킨다 — 이긴
     * 쪽만 승격을 실행해, 두 요청 모두 성공한 것처럼 보이는 경쟁을 막는다.
     */
    @Transactional
    public void verify(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 링크입니다."));

        if (verificationToken.isExpired()) {
            expiredTokenPurger.purge(verificationToken.getId());
            throw new IllegalArgumentException("인증 링크가 만료되었습니다. 다시 요청해 주세요.");
        }

        int consumed = tokenRepository.deleteByIdAndToken(verificationToken.getId(), token);
        if (consumed == 0) {
            throw new IllegalArgumentException("이미 사용되었거나 유효하지 않은 인증 링크입니다.");
        }
        userService.promoteToUser(verificationToken.getUser().getId());
    }

    /**
     * 사용자가 명시적으로 재발송을 요청했을 때 호출한다. 이미 인증된(Role이 GUEST가 아닌) 계정은
     * 대상이 아니므로 거부한다. 재발송 전 기존 토큰을 지워, 같은 사용자에 대해 유효한 토큰이
     * 여러 개 쌓이지 않게 한다.
     * <p>
     * 짧은 간격의 반복 요청은 막는다. 버튼을 연타하면 메일이 그만큼 쌓이는데, 재발송이 기존 토큰을
     * 지우므로 <b>먼저 도착한 링크들이 전부 무효가 된다</b> — 받는 사람은 여러 통을 받고 그중
     * 마지막 하나만 동작하는 상황이 된다. 발송 비용보다 이 혼란이 더 문제다.
     */
    @Transactional
    public void resend(String email) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        // 쿨다운 검사와 재발급을 계정 단위로 직렬화한다(B07) — 그래야 두 동시 요청이 같은
        // "마지막 발송 시각"을 동시에 읽고 둘 다 쿨다운을 통과하는 경쟁이 없어진다.
        userRepository.findByIdForUpdate(user.getId());

        if (user.getRole() != Role.GUEST) {
            throw new IllegalArgumentException("이미 인증된 계정입니다.");
        }
        requireResendAllowed(user.getId());

        tokenRepository.deleteByUserId(user.getId());
        sendVerificationEmail(email);
    }

    private void requireResendAllowed(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliestNext = outboxMailStore.lastQueuedAt(userId, OutboxMailKind.VERIFY_EMAIL)
                .map(last -> last.plus(RESEND_COOLDOWN))
                .orElse(LocalDateTime.MIN);

        if (now.isBefore(earliestNext)) {
            long waitSeconds = Math.max(1, Duration.between(now, earliestNext).toSeconds());
            throw new IllegalArgumentException(
                    "인증 메일을 방금 보냈습니다. " + waitSeconds + "초 후에 다시 시도해 주세요.");
        }
    }
}
