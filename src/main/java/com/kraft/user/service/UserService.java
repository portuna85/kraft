package com.kraft.user.service;

import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevoker sessionRevoker;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboxMailRepository outboxMailRepository;

    @Transactional
    public Long signUp(String name, String email, String rawPassword) {
        if (userRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 사용중인 이름입니다. name=" + name);
        }
        if (userRepository.existsByEmailHash(EmailHasher.sha512Hex(email))) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

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
     * 변경이 <b>커밋된 뒤</b> 이 계정의 모든 세션을 서버에서 폐기한다({@link SessionRevoker}).
     * 브라우저 JS의 후속 로그아웃에 맡기던 예전 방식은 API 직접 호출이나 후속 요청 실패에
     * 무력했고 다른 기기의 세션도 남겼다(개선 보고서 F04). 커밋 이후로 미루는 이유는,
     * 변경이 롤백되면 세션도 그대로 유지되어야 하기 때문이다.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        AfterCommit.run(() -> sessionRevoker.revokeAll(email));
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

        user.changePassword(passwordEncoder.encode(newPassword));
        String email = user.getEmail();
        AfterCommit.run(() -> sessionRevoker.revokeAll(email));
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
    public void withdraw(String email, String currentPassword) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        Long userId = user.getId();
        emailVerificationTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        outboxMailRepository.deleteByUserId(userId);

        user.withdraw(
                "withdrawn-" + userId + "@kraft.invalid",
                "탈퇴한 사용자" + userId,
                // 아무도 맞힐 수 없는 값. 익명 주소를 알아내도 로그인할 수 없다.
                passwordEncoder.encode(UUID.randomUUID().toString()));

        AfterCommit.run(() -> sessionRevoker.revokeAll(email));
    }

    /**
     * 지금 글을 쓸 수 없는 이유. 쓸 수 있으면 빈 값이다.
     * <p>
     * 화면이 "폼을 보여줄지"를 정할 때 쓴다. 서버가 거절할 것을 화면이 미리 같은 규칙으로
     * 판단해야, 다 쓰고 나서야 이유를 알게 되는 흐름이 생기지 않는다. 판정 자체는 작성
     * 경로와 같은 {@link WriteAccessPolicy}가 하므로 두 경로가 갈라지지 않는다.
     */
    public Optional<String> writeBlockReason(String email) {
        return userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .flatMap(WriteAccessPolicy::blockReason);
    }

    @Transactional
    public void promoteToUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));
        user.promoteToUser();
    }
}
