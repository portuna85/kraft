package com.kraft.user.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.user.domain.EmailPolicy;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GUEST → USER 승격을 위한 이메일 인증 토큰 발급·검증. 메일은 대기열({@link OutboxMailStore})에 넣기만 하고
 * 실제 발송은 {@link OutboxMailWorker}가 트랜잭션 밖에서 한다.
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
     * 자기 자신의 프록시. self-invocation은 {@code @Transactional}을 건너뛰어 같은 트랜잭션이 rollback-only가
     * 되고, catch로 잡아도 반환 시 {@code UnexpectedRollbackException}으로 가입 응답이 500이 된다.
     * 프록시를 거치면 독립 트랜잭션이 열려 예외가 밖으로 새지 않는다.
     */
    @Lazy
    @Autowired
    private EmailVerificationService self;

    /**
     * 인증 토큰을 만들고 메일을 대기열에 넣는다. SMTP는 커밋 후 {@code @Async}로 따로 돈다 —
     * {@code afterCommit}에서 보내도 커넥션은 아직 잡혀 있어, 메일 서버가 느리면 풀이 마른다.
     */
    @Transactional
    public void sendVerificationEmail(String rawEmail) {
        String email = EmailPolicy.normalize(rawEmail);
        User user = userRepository.findByEmailHmac(EmailHasher.hmacHex(email))
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        // withdraw()는 role을 바꾸지 않아 탈퇴한 GUEST도 통과하므로 여기서도 거부한다.
        if (user.isWithdrawn()) {
            throw new BusinessValidationException("탈퇴한 회원입니다. email=" + EmailMasker.mask(email));
        }

        issueTokenAndEnqueue(user);
    }

    /** 토큰 저장 + 메일 대기열 적재. 토큰 테이블에는 해시만 남고 평문은 발송될 때까지만 outbox에 산다. */
    private void issueTokenAndEnqueue(User user) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash(EmailHasher.sha512Hex(token))
                .user(user)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .build());

        // 토큰과 대기열 행은 같은 트랜잭션에서 커밋된다.
        outboxMailStore.enqueue(user, token, OutboxMailKind.VERIFY_EMAIL);

        AfterCommit.run(outboxMailWorker::drainAsync);
    }

    /**
     * 가입 직후 쓰는 안전 버전: 토큰·대기열 저장 실패를 흡수해 가입 응답이 실패하지 않게 한다
     * (인증 메일은 {@link #resend}로 다시 받을 수 있다). {@code @Transactional}을 붙이지 않는다 —
     * {@link #self}로 부른 {@link #sendVerificationEmail}이 독립 트랜잭션을 열어야 한다.
     */
    public void sendVerificationEmailSafely(String email) {
        try {
            self.sendVerificationEmail(email);
        } catch (Exception e) {
            log.warn("인증 메일을 대기열에 넣지 못했습니다. email={}", EmailMasker.mask(email), e);
        }
    }

    /**
     * 토큰을 검증하고 회원을 승격한다. 만료 토큰은 {@link ExpiredTokenPurger}가 별도 트랜잭션에서 지운다
     * (여기서 지우면 이어지는 예외가 삭제까지 롤백한다). 소비는 승격 전에 조건부로 해, 같은 토큰이 동시에
     * 들어와도 행 잠금으로 한 요청만 성공한다.
     *
     * @return 승격된 회원의 id(호출자가 같은 세션의 권한을 즉시 갱신하는 데 쓴다)
     */
    @Transactional
    public Long verify(String token) {
        String tokenHash = EmailHasher.sha512Hex(token);
        EmailVerificationToken verificationToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessValidationException("유효하지 않은 인증 링크입니다."));

        if (verificationToken.isExpired()) {
            expiredTokenPurger.purge(verificationToken.getId());
            throw new BusinessValidationException("인증 링크가 만료되었습니다. 다시 요청해 주세요.");
        }

        int consumed = tokenRepository.deleteByIdAndTokenHash(verificationToken.getId(), tokenHash);
        if (consumed == 0) {
            throw new BusinessValidationException("이미 사용되었거나 유효하지 않은 인증 링크입니다.");
        }
        Long userId = verificationToken.getUser().getId();
        userService.promoteToUser(userId);
        return userId;
    }

    /**
     * 인증 메일 재발송(GUEST만). 기존 토큰을 지우므로 먼저 보낸 링크는 무효가 된다 — 연타하면 여러 통 중
     * 마지막만 동작하는 혼란이 생겨 쿨다운으로 막는다.
     */
    @Transactional
    public void resend(Long userId) {
        // 계정 행을 잠가 쿨다운 검사와 재발급을 직렬화한다(동시 요청이 둘 다 통과하는 경쟁 방지).
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. userId=" + userId));

        if (user.getRole() != Role.GUEST) {
            throw new BusinessValidationException("이미 인증된 계정입니다.");
        }
        requireResendAllowed(user.getId());

        tokenRepository.deleteByUserId(user.getId());
        issueTokenAndEnqueue(user);
    }

    private void requireResendAllowed(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliestNext = outboxMailStore.lastQueuedAt(userId, OutboxMailKind.VERIFY_EMAIL)
                .map(last -> last.plus(RESEND_COOLDOWN))
                .orElse(LocalDateTime.MIN);

        if (now.isBefore(earliestNext)) {
            long waitSeconds = Math.max(1, Duration.between(now, earliestNext).toSeconds());
            throw new BusinessValidationException(
                    "인증 메일을 방금 보냈습니다. " + waitSeconds + "초 후에 다시 시도해 주세요.");
        }
    }
}
