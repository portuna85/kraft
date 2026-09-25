package com.kraft.user.service;

import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.EmailPolicy;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordBytePolicy;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.session.SessionRevocationStore;
import com.kraft.user.session.SessionRevocationWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    /**
     * 탈퇴한 계정의 대체 이름·이메일 공간(평가 보고서 2026-09-25 F10). 새 가입은 이 공간을 쓸 수
     * 없다 — 예전에는 누군가 "탈퇴한 사용자123"이나 "withdrawn-123@kraft.invalid"로 먼저 가입해
     * 두면 123번 회원의 탈퇴가 유일성 제약에 걸려 실패했다. {@code .invalid}는 실제로 존재할 수
     * 없는 예약 최상위 도메인(RFC 2606)이라 정상 사용자가 막히지 않는다.
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

    @Transactional
    public Long signUp(String name, String email, String rawPassword) {
        // 대소문자·앞뒤 공백만 다른 이메일이 별개 계정으로 가입되지 않도록 정규화부터
        // 한다(BE-06) — 이후의 해시·저장이 전부 이 값을 쓴다.
        email = EmailPolicy.normalize(email);
        if (name != null && name.strip().startsWith(WITHDRAWN_NAME_PREFIX)) {
            throw new IllegalArgumentException("사용할 수 없는 이름입니다. '" + WITHDRAWN_NAME_PREFIX + "'로 시작하는 이름은 탈퇴한 계정 표시에 쓰입니다.");
        }
        if (email.endsWith(WITHDRAWN_EMAIL_DOMAIN)) {
            throw new IllegalArgumentException("사용할 수 없는 이메일 주소입니다.");
        }
        if (userRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 사용중인 이름입니다. name=" + name);
        }
        if (userRepository.existsByEmailHash(EmailHasher.sha512Hex(email))) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        PasswordBytePolicy.validate(rawPassword);

        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.GUEST)
                .build();

        return userRepository.save(user).getId();
    }

    /**
     * 회원정보 변경은 비밀번호 변경만 가능하다는 기획 의도(User.java 클래스 주석)에 따라,
     * 반드시 현재 비밀번호를 확인한 뒤에만 새 비밀번호로 바꾼다.
     * <p>
     * 변경이 <b>커밋된 뒤</b> 이 계정의 모든 세션을 서버에서 폐기하도록 영속 태스크를 남긴다
     * ({@link #revokeSessionsAfterCommit}). 브라우저 JS의 후속 로그아웃에 맡기던 예전 방식은
     * API 직접 호출이나 후속 요청 실패에 무력했고 다른 기기의 세션도 남겼다(개선 보고서 F04).
     * 커밋 이후로 미루는 이유는, 변경이 롤백되면 세션도 그대로 유지되어야 하기 때문이다.
     */
    @Transactional
    public void changePassword(String rawEmail, String currentPassword, String newPassword) {
        String email = EmailPolicy.normalize(rawEmail);
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        PasswordBytePolicy.validate(newPassword);

        user.changePassword(passwordEncoder.encode(newPassword));
        revokeSessionsAfterCommit(user, email);
    }

    /**
     * 현재 비밀번호를 묻지 않고 새 비밀번호를 정한다. 메일로 받은 1회용 토큰을 이미 검증한
     * {@link PasswordResetService}만 부른다 — 이 메서드에 인증 경로가 하나 더 생기면 그 토큰
     * 검사를 건너뛰는 길이 생기므로, 호출자는 여기 하나로 유지한다.
     * <p>
     * 세션을 모두 폐기하는 것은 비밀번호 변경과 같다. 비밀번호를 잊었다는 것은 이미 남이
     * 쓰고 있을 수 있다는 뜻이기도 하다.
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. id=" + userId));
        PasswordBytePolicy.validate(newPassword);

        user.changePassword(passwordEncoder.encode(newPassword));
        revokeSessionsAfterCommit(user, user.getEmail());
    }

    /**
     * 회원 탈퇴. 글과 댓글은 남기고 그 사람을 가리키는 값만 지운다 — 남의 댓글이 달린 글이나
     * 대화의 맥락까지 함께 사라지지 않게 하려는 것이다(V9 주석 참고). 화면에는 익명 이름으로
     * 보인다.
     * <p>
     * 비밀번호를 한 번 더 확인하는 이유는 되돌릴 수 없는 작업이기 때문이다. 자리를 비운 사이
     * 남이 눌러 계정을 없애는 일도 이 확인이 막는다.
     * <p>
     * 이메일이 익명 주소로 바뀌므로 원래 주소로 다시 가입할 수 있다. 보낼 예정이던 메일과
     * 남아 있던 링크들은 함께 지운다 — 없는 계정으로 가는 메일이고, 지우지 않으면 탈퇴 후에도
     * 옛 링크로 무언가 할 수 있는 길이 남는다.
     */
    @Transactional
    public void withdraw(String rawEmail, String currentPassword) {
        String email = EmailPolicy.normalize(rawEmail);
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        Long userId = user.getId();
        emailVerificationTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        outboxMailRepository.deleteByUserId(userId);

        user.withdraw(
                replacementEmail(userId),
                replacementName(userId),
                // 아무도 맞힐 수 없는 값. 익명 주소를 알아내도 로그인할 수 없다.
                passwordEncoder.encode(UUID.randomUUID().toString()));

        // 여기서 넘기는 email은 위에서 파라미터로 받은 탈퇴 전 주소다. user.withdraw() 이후
        // user.getEmail()을 쓰면 이미 익명 주소를 태스크에 남기게 된다.
        revokeSessionsAfterCommit(user, email);
    }

    /**
     * 탈퇴 표시 이름. 기본은 "탈퇴한 사용자{id}"다. 예약어 제한이 생기기 전에 가입한 다른 계정이
     * 그 이름을 이미 쓰고 있으면(F10) 짧은 무작위 접미사를 붙인다 — 새 가입만 막고 기존 충돌
     * 데이터를 그대로 두면 그 회원은 영영 탈퇴할 수 없다.
     */
    private String replacementName(Long userId) {
        String base = WITHDRAWN_NAME_PREFIX + userId;
        return firstUnused(base, candidate -> userRepository.existsByName(candidate), suffix -> base + "-" + suffix);
    }

    /** 탈퇴 대체 이메일. 이름과 같은 이유로 충돌하면 접미사를 붙인다(F10). */
    private String replacementEmail(Long userId) {
        String local = "withdrawn-" + userId;
        return firstUnused(local + WITHDRAWN_EMAIL_DOMAIN,
                candidate -> userRepository.existsByEmailHash(EmailHasher.sha512Hex(candidate)),
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
     * 지금 글을 쓸 수 없는 이유. 쓸 수 있으면 빈 값이다.
     * <p>
     * 화면이 "폼을 보여줄지"를 정할 때 쓴다. 서버가 거절할 것을 화면이 미리 같은 규칙으로
     * 판단해야, 다 쓰고 나서야 이유를 알게 되는 흐름이 생기지 않는다. 판정 자체는 작성
     * 경로와 같은 {@link WriteAccessPolicy}가 하므로 두 경로가 갈라지지 않는다.
     * <p>
     * 회원은 principal의 불변 id로 찾는다({@link CurrentUser}) — principal은 이메일을 들고 있지
     * 않다(평가 보고서 2026-09-25 F01). 미인증이거나 계정을 찾지 못하면 빈 값이다.
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
     * 세션 폐기를 영속 태스크로 남기고(같은 트랜잭션에서 커밋), 커밋 직후 곧바로 한 번 처리를
     * 시도한다(B06). 예전에는 {@code AfterCommit}에서 즉시 폐기만 시도하고 실패하면 그냥
     * 로그로만 남겼다 — 세션 저장소 장애나 그 직후 프로세스 종료로 폐기가 유실되면 복구할
     * 방법이 없었다. 태스크가 DB에 남아 있으므로 실패해도 {@link SessionRevocationWorker}의
     * 주기 작업이 최종적으로 완수한다.
     */
    private void revokeSessionsAfterCommit(User user, String email) {
        Long taskId = sessionRevocationStore.enqueue(user, email);
        AfterCommit.run(() -> sessionRevocationWorker.attemptNow(taskId));
    }
}
