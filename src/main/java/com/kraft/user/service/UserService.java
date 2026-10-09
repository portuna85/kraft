package com.kraft.user.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailPolicy;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordBytePolicy;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.mail.OutboxMailStore;
import com.kraft.user.mail.OutboxMailWorker;
import com.kraft.user.session.SessionRevocationStore;
import com.kraft.user.session.SessionRevocationWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    /**
     * 탈퇴 계정의 대체 이름·이메일 공간. 새 가입이 이 공간을 쓰면 탈퇴가 유일성 제약에 걸리므로 막는다.
     * {@code .invalid}는 예약 도메인(RFC 2606)이라 정상 사용자와 겹치지 않는다.
     */
    static final String WITHDRAWN_NAME_PREFIX = "탈퇴한 사용자";
    static final String WITHDRAWN_EMAIL_DOMAIN = "@kraft.invalid";
    /** 대체 이름·이메일이 이미 쓰이고 있을 때 접미사를 바꿔 다시 시도하는 횟수. */
    private static final int REPLACEMENT_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationStore sessionRevocationStore;
    private final SessionRevocationWorker sessionRevocationWorker;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboxMailRepository outboxMailRepository;
    private final OutboxMailStore outboxMailStore;
    private final OutboxMailWorker outboxMailWorker;
    /** 해시 계산을 끝낸 뒤 짧은 쓰기 트랜잭션만 여는 데 쓴다. */
    private final TransactionTemplate transactionTemplate;

    /**
     * BCrypt(약 100ms)는 트랜잭션 밖에서 계산하고 쓰기만 짧은 트랜잭션으로 묶는다.
     *
     * @return 새 계정을 만들었으면 {@code true}. 호출자는 이 값과 무관하게 같은 응답을 내려야 한다
     * (응답이 갈리면 이메일 가입 여부가 드러난다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean signUp(String name, String email, String rawPassword) {
        // 대소문자·공백만 다른 이메일이 별개 계정이 되지 않게 먼저 정규화한다.
        email = EmailPolicy.normalize(email);
        if (name != null && name.strip().startsWith(WITHDRAWN_NAME_PREFIX)) {
            throw new BusinessValidationException("사용할 수 없는 이름입니다. '" + WITHDRAWN_NAME_PREFIX + "'로 시작하는 이름은 탈퇴한 계정 표시에 쓰입니다.");
        }
        if (email.endsWith(WITHDRAWN_EMAIL_DOMAIN)) {
            throw new BusinessValidationException("사용할 수 없는 이메일 주소입니다.");
        }
        // 닉네임은 공개 정보라 중복을 바로 알려도 된다(열거 방지는 이메일만).
        if (userRepository.existsByName(name)) {
            throw new BusinessValidationException("이미 사용중인 이름입니다. name=" + name);
        }
        PasswordBytePolicy.validate(rawPassword);

        // 분기 전에 해시한다 — 응답 시간으로 가입 여부가 드러나지 않게.
        String encodedPassword = passwordEncoder.encode(rawPassword);

        final String normalizedEmail = email;
        return Boolean.TRUE.equals(
                transactionTemplate.execute(status -> saveNewUser(name, normalizedEmail, encodedPassword)));
    }

    private boolean saveNewUser(String name, String email, String encodedPassword) {
        Optional<User> existing = userRepository.findByEmailHmac(EmailHasher.hmacHex(email));
        if (existing.isPresent()) {
            // 쿨다운·시간당 예산 안에서만 큐에 넣는다(남의 메일함 폭격 방지). 응답은 같다.
            if (outboxMailStore.enqueueNotice(existing.get(), OutboxMailKind.ACCOUNT_EXISTS)) {
                // 다음 예약 주기를 기다리지 않고 커밋 직후 한 번 보낸다.
                AfterCommit.run(outboxMailWorker::drainAsync);
            }
            return false;
        }

        User user = User.builder()
                .name(name)
                .email(email)
                .password(encodedPassword)
                .role(Role.GUEST)
                .build();

        userRepository.save(user);
        return true;
    }

    /**
     * 현재 비밀번호를 확인한 뒤 바꾸고, 커밋 후 이 계정의 모든 세션을 서버에서 폐기한다
     * ({@link #revokeSessionsAfterCommit}). 롤백되면 세션도 유지된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        String verifiedHash = verifiedPasswordHash(userId, currentPassword);
        PasswordBytePolicy.validate(newPassword);
        String newHash = passwordEncoder.encode(newPassword);

        transactionTemplate.executeWithoutResult(status -> {
            User user = reloadIfUnchanged(userId, verifiedHash);
            user.changePassword(newHash);
            revokeSessionsAfterCommit(user);
        });
    }

    /** 트랜잭션 밖에서 현재 비밀번호를 확인하고, 확인에 쓴 저장 해시를 돌려준다. */
    private String verifiedPasswordHash(Long userId, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. userId=" + userId));
        String storedHash = user.getPassword();
        if (!passwordEncoder.matches(currentPassword, storedHash)) {
            throw new BusinessValidationException("현재 비밀번호가 일치하지 않습니다.");
        }
        return storedHash;
    }

    /** 쓰기 트랜잭션에서 다시 읽어, 확인 이후 비밀번호가 바뀌었으면 거절한다(옛 승인 재사용 방지). */
    private User reloadIfUnchanged(Long userId, String verifiedHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. userId=" + userId));
        if (!verifiedHash.equals(user.getPassword())) {
            throw new BusinessValidationException("현재 비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    /**
     * 현재 비밀번호 없이 바꾸고 세션을 모두 폐기한다. 토큰을 검증한 {@link PasswordResetService}만 부른다
     * (다른 호출자가 생기면 토큰 검사를 우회하는 길이 된다).
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        resetPasswordEncoded(userId, encodeNewPassword(newPassword));
    }

    /** 새 비밀번호를 검증·해시한다. BCrypt 동안 커넥션을 쥐지 않게 트랜잭션 없이 돈다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String encodeNewPassword(String newPassword) {
        PasswordBytePolicy.validate(newPassword);
        return passwordEncoder.encode(newPassword);
    }

    /** 미리 해시한 값을 저장하고 세션을 폐기한다. 토큰 소비와 같은 트랜잭션에 참여한다. */
    @Transactional
    public void resetPasswordEncoded(Long userId, String encodedPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. id=" + userId));
        user.changePassword(encodedPassword);
        revokeSessionsAfterCommit(user);
    }

    /**
     * 회원 탈퇴. 글·댓글은 남기고(대화 맥락 보존) 개인 식별 값만 익명으로 바꾼다. 되돌릴 수 없어 비밀번호를
     * 다시 확인한다. 대기 메일과 남은 토큰은 지워 탈퇴 후 옛 링크가 동작하지 않게 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void withdraw(Long userId, String currentPassword) {
        String verifiedHash = verifiedPasswordHash(userId, currentPassword);
        // 아무도 맞힐 수 없는 값. 익명 주소를 알아내도 로그인할 수 없다.
        String unusableHash = passwordEncoder.encode(UUID.randomUUID().toString());

        transactionTemplate.executeWithoutResult(status -> {
            User user = reloadIfUnchanged(userId, verifiedHash);

            emailVerificationTokenRepository.deleteByUserId(userId);
            passwordResetTokenRepository.deleteByUserId(userId);
            outboxMailRepository.deleteByUserId(userId);

            user.withdraw(replacementEmail(userId), replacementName(userId), unusableHash);

            revokeSessionsAfterCommit(user);
        });
    }

    /** "탈퇴한 사용자{id}". 기존 계정이 이미 쓰고 있으면 무작위 접미사를 붙인다. */
    private String replacementName(Long userId) {
        String base = WITHDRAWN_NAME_PREFIX + userId;
        return firstUnused(base, candidate -> userRepository.existsByName(candidate), suffix -> base + "-" + suffix);
    }

    /** 탈퇴 대체 이메일. 이름과 같은 이유로 충돌하면 접미사를 붙인다. */
    private String replacementEmail(Long userId) {
        String local = "withdrawn-" + userId;
        return firstUnused(local + WITHDRAWN_EMAIL_DOMAIN,
                candidate -> userRepository.existsByEmailHmac(EmailHasher.hmacHex(candidate)),
                suffix -> local + "-" + suffix + WITHDRAWN_EMAIL_DOMAIN);
    }

    private static String firstUnused(String base, Predicate<String> taken,
                                      UnaryOperator<String> withSuffix) {
        if (!taken.test(base)) {
            return base;
        }
        for (int i = 0; i < REPLACEMENT_ATTEMPTS; i++) {
            String candidate = withSuffix.apply(UUID.randomUUID().toString().substring(0, 8));
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("탈퇴 대체 식별자를 만들지 못했다. base=" + base);
    }

    /**
     * 지금 글을 쓸 수 없는 이유(쓸 수 있거나 미인증이면 빈 값). 화면이 폼을 보여줄지 정할 때 쓰며,
     * 작성 경로와 같은 {@link WriteAccessPolicy}로 판정한다.
     */
    public Optional<String> writeBlockReason(Authentication authentication) {
        return Optional.ofNullable(CurrentUser.userIdOrNull(authentication, userRepository))
                .flatMap(userRepository::findById)
                .flatMap(WriteAccessPolicy::blockReason);
    }

    @Transactional
    public void promoteToUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. id=" + userId));
        user.promoteToUser();
    }

    /**
     * 세션 폐기를 같은 트랜잭션의 영속 태스크로 남기고 커밋 직후 한 번 시도한다. 실패해도
     * {@link SessionRevocationWorker}가 끝내 처리한다.
     */
    private void revokeSessionsAfterCommit(User user) {
        Long taskId = sessionRevocationStore.enqueue(user);
        AfterCommit.run(() -> sessionRevocationWorker.attemptNow(taskId));
    }
}
